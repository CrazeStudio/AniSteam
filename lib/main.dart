import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

const defaultApiBase = 'https://anisteam-api-sooty.vercel.app';
const lime = Color(0xFFB8F35A);
const bg = Color(0xFF0B0D12);

void main() => runApp(const AniSteamApp());

class AniSteamApp extends StatelessWidget {
  const AniSteamApp({super.key});
  @override Widget build(BuildContext context) => MaterialApp(
    title: 'AniSteam', debugShowCheckedModeBanner: false,
    theme: ThemeData(useMaterial3: true, brightness: Brightness.dark,
      scaffoldBackgroundColor: bg,
      colorScheme: ColorScheme.fromSeed(seedColor: lime, brightness: Brightness.dark),
      appBarTheme: const AppBarTheme(backgroundColor: bg, surfaceTintColor: Colors.transparent),
      cardTheme: CardThemeData(color: const Color(0xFF171A23), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
      inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: const Color(0xFF171A23),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none)),
    ), home: const AppShell(),
  );
}

class Anime {
  const Anime({required this.title, required this.url, this.image = '', this.description = '', this.source = 'ToonStream'});
  final String title, url, image, description, source;
  factory Anime.fromMap(Map<String, dynamic> m, {String fallbackSource = 'ToonStream'}) {
    String read(List<String> keys) {
      for (final k in keys) {
        final v = m[k];
        if (v is String && v.trim().isNotEmpty) return v.trim();
        if (v is num) return v.toString();
      }
      return '';
    }
    final url = read(['url','link','href','animeUrl','episodeUrl','watchUrl','targetUrl','slug']);
    final inferred = url.toLowerCase().contains('hianime') ? 'HiAnime' : fallbackSource;
    return Anime(title: read(['title','name','animeTitle','englishTitle','episodeTitle']).isEmpty ? 'Untitled' : read(['title','name','animeTitle','englishTitle','episodeTitle']),
      url: url, image: read(['image','poster','posterUrl','thumbnail','cover','img','imageUrl','poster_url']),
      description: read(['description','synopsis','overview','summary']), source: inferred);
  }
  Map<String, dynamic> toJson() => {'title':title,'url':url,'image':image,'description':description,'source':source};
  factory Anime.fromJson(Map<String,dynamic> m) => Anime(title:(m['title']??'Untitled').toString(),url:(m['url']??'').toString(),image:(m['image']??'').toString(),description:(m['description']??'').toString(),source:(m['source']??'ToonStream').toString());
}

class AniApi {
  // Always use the configured AniSteam-2 service. Ignore stale values left by older builds.
  // Keep the service address private and out of Settings.
  static Future<String> baseUrl() async => defaultApiBase;
  static Future<dynamic> get(String path, [Map<String,String>? query]) async {
    final base = await baseUrl();
    final parsedBase = Uri.tryParse(base);
    if (parsedBase == null || parsedBase.host.isEmpty || !['https','http'].contains(parsedBase.scheme)) {
      throw Exception('AniSteam service configuration is invalid.');
    }
    final uri = Uri.parse('$base$path').replace(queryParameters: query);
    final response = await http.get(uri, headers: const {'Accept':'application/json'}).timeout(const Duration(seconds:25));
    if (response.statusCode < 200 || response.statusCode >= 300) throw Exception('HTTP ${response.statusCode} from AniSteam API');
    try { return jsonDecode(utf8.decode(response.bodyBytes)); } on FormatException { throw Exception('API returned invalid JSON'); }
  }
  static List<Map<String,dynamic>> maps(dynamic value, [int depth=0]) {
    if (depth > 6) return [];
    if (value is List) return value.whereType<Map>().map((x)=>Map<String,dynamic>.from(x)).toList();
    if (value is Map) {
      final m=Map<String,dynamic>.from(value);
      for(final key in ['results','data','items','anime','catalogue','catalog','episodes','streams','direct_streams','player_streams']) {
        if(m.containsKey(key)){final found=maps(m[key],depth+1);if(found.isNotEmpty)return found;}
      }
      for(final v in m.values){final found=maps(v,depth+1);if(found.isNotEmpty)return found;}
    }
    return [];
  }
  static List<Anime> parse(dynamic data, {String source='ToonStream'}) {
    final seen=<String>{};
    return maps(data).map((m)=>Anime.fromMap(m,fallbackSource:source))
      .where((a)=>a.url.startsWith('http')&&seen.add(a.url)).toList();
  }
  static Future<List<Anime>> catalogue() async => parse(await get('/catalogue', {'page':'1'}));
  static Future<List<Anime>> search(String query, String mode) async {
    final collected=<Anime>[];
    Future<void> fetch(String source,String path,Map<String,String> params) async {
      try { collected.addAll(parse(await get(path,params),source:source)); } catch (_) {}
    }
    if(mode=='COMBINED'||mode=='TOONSTREAM'){
      await fetch('ToonStream','/toonstream/search',{'q':query});
      if(!collected.any((a)=>a.source=='ToonStream')) await fetch('ToonStream','/search',{'q':query});
    }
    if(mode=='COMBINED'||mode=='HIANIME') await fetch('HiAnime','/hianime/search',{'q':query});
    final unique=<String,Anime>{};
    for(final a in collected){final key=a.title.toLowerCase().trim();if(key.isNotEmpty){if(unique.containsKey(key)){final old=unique[key]!;unique[key]=Anime(title:old.title,url:old.url,image:old.image.isEmpty?a.image:old.image,description:old.description.isEmpty?a.description:old.description,source:old.source);}else{unique[key]=a;}}}
    if(unique.isEmpty) throw Exception('No results found for "$query" from the selected sources.');
    return unique.values.toList();
  }
  static Future<dynamic> details(Anime anime) => get('/details', {'url':anime.url});
  static Future<List<Anime>> episodes(Anime anime) async {
    final source=anime.source.toLowerCase().contains('hianime')?'hianime':'toonstream';
    return parse(await get('/$source/episodes',{'url':anime.url}),source:anime.source)
      .asMap().entries.map((e)=>Anime(title:e.value.title=='Untitled'?'Episode ${e.key+1}':e.value.title,url:e.value.url,image:e.value.image,source:anime.source)).toList();
  }
  static bool isDirectUrl(String url) {
    final lower = url.toLowerCase();
    final clean = lower.split('?').first.split('#').first;
    return clean.endsWith('.mp4') ||
        clean.endsWith('.m3u8') ||
        clean.endsWith('.webm') ||
        clean.endsWith('.mkv') ||
        lower.contains('.m3u8?') ||
        lower.contains('.mp4?') ||
        lower.contains('/hls/');
  }

  static Future<List<StreamChoice>> streams(Anime episode) async {
    final source = episode.source.toLowerCase();
    final endpoints = <(String, Map<String, String>)>[];
    final query = <String, String>{'url': episode.url};
    if (source.contains('hianime')) {
      endpoints.add(('/hianime/streams', {...query, 'resolve': 'true'}));
      endpoints.add(('/api/stream', query));
      endpoints.add(('/watch', query));
    } else if (source.contains('toonstream')) {
      endpoints.add(('/toonstream/streams', {...query, 'resolve': 'true'}));
      endpoints.add(('/watch', query));
    } else {
      endpoints.add(('/watch', query));
      endpoints.add(('/animesalt/streams', {...query, 'resolve': 'true'}));
    }

    for (final endpoint in endpoints) {
      try {
        final raw = await get(endpoint.$1, endpoint.$2);
        final choices = _parseStreams(raw, episode.source);
        if (choices.isNotEmpty) return choices;
      } catch (_) {
        // Use the next compatible extractor. The page still reports a single
        // useful error if every supported extractor fails.
      }
    }
    throw Exception('No playable servers were returned for this episode.');
  }

  static List<StreamChoice> _parseStreams(dynamic raw, String fallbackSource) {
    final out = <StreamChoice>[];
    final seen = <String>{};
    const abyssIframeUrl = 'https://player.abyssplayer.com/dqvi26UZ6';
    final excluded = {'poster', 'poster_image', 'image', 'thumbnail', 'thumbnail_image', 'cover', 'description'};
    const urlKeys = ['url', 'link', 'src', 'file', 'embed_url', 'embed', 'stream_url', 'streamUrl'];
    String textValue(dynamic value) {
      if (value == null) return '';
      if (value is String || value is num) return value.toString().trim();
      return '';
    }
    bool isWebUrl(String value) => value.startsWith('https://') || value.startsWith('http://');

    void add(String url, String title, String type, String language) {
      if (!isWebUrl(url) || !seen.add(url)) return;
      final direct = isDirectUrl(url) || type.toLowerCase() == 'direct';
      out.add(StreamChoice(
        title: title.trim().isEmpty ? (direct ? 'Direct stream' : 'Web player ' + (out.length + 1).toString()) : title.trim(),
        url: url,
        source: fallbackSource,
        type: direct ? 'direct' : 'embed',
        language: language.trim().isEmpty ? 'Default' : language.trim(),
      ));
    }

    void walk(dynamic node, {String label = 'Server', String typeHint = 'embed', String contextKey = '', int depth = 0}) {
      if (depth > 10 || node == null) return;
      if (node is List) {
        for (var i = 0; i < node.length; i++) {
          walk(node[i],
              label: label == 'Server' ? 'Server ' + (i + 1).toString() : label,
              typeHint: typeHint,
              contextKey: contextKey,
              depth: depth + 1);
        }
        return;
      }
      if (node is Map) {
        final map = Map<String, dynamic>.from(node);
        String url = '';
        for (final key in urlKeys) {
          final candidate = textValue(map[key]);
          if (isWebUrl(candidate)) { url = candidate; break; }
        }
        if (url.isNotEmpty) {
          final title = [
            textValue(map['server']),
            textValue(map['name']),
            textValue(map['label']),
            textValue(map['title']),
            textValue(map['quality']),
          ].firstWhere((x) => x.isNotEmpty, orElse: () => label);
          final type = textValue(map['type']).isNotEmpty
              ? textValue(map['type'])
              : (contextKey.toLowerCase().contains('direct') ? 'direct' : typeHint);
          final language = textValue(map['language']).isNotEmpty
              ? textValue(map['language'])
              : textValue(map['lang']);
          add(url, title, type, language);
          return;
        }
        for (final entry in map.entries) {
          final key = entry.key.toString();
          final lowerKey = key.toLowerCase();
          if (excluded.contains(lowerKey)) continue;
          final looksLikeStreamContainer = lowerKey.contains('stream') ||
              lowerKey.contains('player') ||
              lowerKey.contains('embed') ||
              lowerKey.contains('server') ||
              lowerKey == 'results' ||
              lowerKey == 'players' ||
              lowerKey == 'data' ||
              lowerKey == 'result';
          final nextType = lowerKey.contains('direct') ? 'direct' : typeHint;
          final child = entry.value;
          if (child is String && isWebUrl(child.trim())) {
            if (looksLikeStreamContainer) add(child.trim(), key, nextType, '');
          } else if (child is List || child is Map) {
            walk(child,
                label: key.replaceAll('_', ' '),
                typeHint: nextType,
                contextKey: lowerKey,
                depth: depth + 1);
          }
        }
        return;
      }
      if (node is String && isWebUrl(node.trim()) &&
          (contextKey.contains('stream') || contextKey.contains('player') ||
           contextKey.contains('embed') || contextKey.contains('server') ||
           contextKey.contains('direct') || contextKey == 'results')) {
        add(node.trim(), label, typeHint, '');
      }
    }

    dynamic payload = raw;
    if (payload is Map) {
      payload = payload['data'] ?? payload['result'] ?? payload;
    }
    walk(payload);
    // Some API responses expose only a short-server redirect. Add the supported
    // Abyss iframe player as a WebView fallback rather than trying to decode it as video.
    final shortServerFound = out.any((choice) =>
        choice.isShortLink ||
        choice.title.toLowerCase().contains('short server') ||
        choice.title.toLowerCase().contains('abyss'));
    if (shortServerFound && !seen.contains(abyssIframeUrl)) {
      out.add(StreamChoice(
        title: 'Abyss Short Server',
        url: abyssIframeUrl,
        source: fallbackSource,
        type: 'embed',
        language: 'Default',
      ));
    }
    out.sort((a, b) => (b.isDirect ? 1 : 0).compareTo(a.isDirect ? 1 : 0));
    return out;
  }
}

class StreamChoice {
  const StreamChoice({
    required this.title,
    required this.url,
    this.source = 'AniSteam',
    this.type = 'embed',
    this.language = 'Default',
  });
  final String title;
  final String url;
  final String source;
  final String type;
  final String language;
  bool get isDirect => type == 'direct' || AniApi.isDirectUrl(url);

  bool get isShortLink {
    final host = Uri.tryParse(url)?.host.toLowerCase() ?? '';
    return const {
      'bit.ly', 'tinyurl.com', 'tiny.one', 'cutt.ly', 't.co',
      'is.gd', 'rb.gy', 'shorturl.at', 'ouo.io', 'ouo.press',
    }.contains(host);
  }

  String get playbackKind => isDirect ? 'Direct video' : (isShortLink ? 'Short-link web player' : 'Embedded web player');
}



class AppShell extends StatefulWidget { const AppShell({super.key}); @override State<AppShell> createState()=>_AppShellState(); }
class _AppShellState extends State<AppShell> {
  int tab=0; String sourceMode='COMBINED';
  final history=<Anime>[]; final historyKey='anisteam_watch_history';
  @override void initState(){super.initState();_load();}
  Future<void> _load() async {
    final p=await SharedPreferences.getInstance();
    final raw=p.getString('anisteam_watch_history');
    if(raw!=null){try{history.addAll((jsonDecode(raw) as List).whereType<Map>().map((m)=>Anime.fromJson(Map<String,dynamic>.from(m))));}catch(_){}}
    if(mounted)setState(()=>sourceMode=p.getString('source_mode')??'COMBINED');
  }
  Future<void> setMode(String mode) async {final p=await SharedPreferences.getInstance();await p.setString('source_mode',mode);if(mounted)setState(()=>sourceMode=mode);}
  Future<void> addHistory(Anime anime) async {
    history.removeWhere((a)=>a.url==anime.url);history.insert(0,anime);if(history.length>100)history.removeRange(100,history.length);
    final p=await SharedPreferences.getInstance();await p.setString(historyKey,jsonEncode(history.map((a)=>a.toJson()).toList()));
    if(mounted)setState((){});
  }
  Future<void> clearHistory() async {history.clear();final p=await SharedPreferences.getInstance();await p.remove(historyKey);if(mounted)setState((){});}
  @override Widget build(BuildContext context){
    final pages=[
      HomeTab(sourceMode:sourceMode,onOpen:(a)=>openDetails(a)),
      SearchTab(sourceMode:sourceMode,onOpen:(a)=>openDetails(a)),
      HistoryTab(items:history,onClear:clearHistory,onOpen:(a)=>openDetails(a)),
      SettingsTab(sourceMode:sourceMode,onMode:setMode),
    ];
    return Scaffold(body:IndexedStack(index:tab,children:pages),
      bottomNavigationBar:NavigationBar(selectedIndex:tab,onDestinationSelected:(v)=>setState(()=>tab=v),backgroundColor:const Color(0xFF10131A),indicatorColor:lime.withValues(alpha:.15),
        destinations:const [NavigationDestination(icon:Icon(Icons.home_outlined),selectedIcon:Icon(Icons.home_rounded),label:'Home'),NavigationDestination(icon:Icon(Icons.search_rounded),label:'Search'),NavigationDestination(icon:Icon(Icons.history_rounded),label:'History'),NavigationDestination(icon:Icon(Icons.tune_rounded),label:'Settings')]),
    );
  }
  Future<void> openDetails(Anime anime) async {await Navigator.push(context,MaterialPageRoute(builder:(_)=>DetailsScreen(anime:anime,onWatch:addHistory)));if(mounted)setState((){});}
}

class HomeTab extends StatefulWidget {const HomeTab({super.key,required this.sourceMode,required this.onOpen});final String sourceMode;final ValueChanged<Anime> onOpen;@override State<HomeTab> createState()=>_HomeTabState();}
class _HomeTabState extends State<HomeTab>{
 late Future<List<Anime>> future=AniApi.catalogue();
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Brand(),actions:[IconButton(onPressed:()=>setState(()=>future=AniApi.catalogue()),icon:const Icon(Icons.refresh_rounded))]),
 body:FutureBuilder<List<Anime>>(future:future,builder:(context,snap){
  if(snap.connectionState==ConnectionState.waiting)return const Center(child:CircularProgressIndicator());
  if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>future=AniApi.catalogue()));
  final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No catalogue yet',message:'The API returned no catalogue items.');
  return RefreshIndicator(onRefresh:()async{setState(()=>future=AniApi.catalogue());try{await future;}catch(_){}},child:CustomScrollView(slivers:[
   SliverToBoxAdapter(child:Padding(padding:const EdgeInsets.fromLTRB(18,12,18,20),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    const Text('Your next obsession starts here.',style:TextStyle(fontSize:27,fontWeight:FontWeight.w900,height:1.1)),
    const SizedBox(height:8),Text('Discover anime · ${widget.sourceMode=='COMBINED'?'ToonStream + HiAnime':widget.sourceMode}',style:const TextStyle(color:Color(0xFF9CA2B0))),
    const SizedBox(height:18),const Row(children:[Icon(Icons.auto_awesome_rounded,color:lime),SizedBox(width:8),Text('Explore catalogue',style:TextStyle(fontSize:17,fontWeight:FontWeight.w800))])
   ]))),
   SliverPadding(padding:const EdgeInsets.fromLTRB(16,0,16,24),sliver:SliverGrid(delegate:SliverChildBuilderDelegate((context,i)=>AnimeCard(anime:list[i],onTap:()=>widget.onOpen(list[i])),childCount:list.length),gridDelegate:const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount:2,mainAxisSpacing:15,crossAxisSpacing:13,childAspectRatio:.59)))
  ]));
  }));}
class SearchTab extends StatefulWidget {const SearchTab({super.key,required this.sourceMode,required this.onOpen});final String sourceMode;final ValueChanged<Anime> onOpen;@override State<SearchTab> createState()=>_SearchTabState();}
class _SearchTabState extends State<SearchTab>{
 final controller=TextEditingController();Future<List<Anime>>? future;bool busy=false;
 @override void dispose(){controller.dispose();super.dispose();}
 Future<void> submit() async {final q=controller.text.trim();if(q.isEmpty)return;setState(()=>busy=true);try{final result=await AniApi.search(q,widget.sourceMode);if(mounted)setState(()=>future=Future.value(result));}catch(e){if(mounted)setState(()=>future=Future.error(e));}finally{if(mounted)setState(()=>busy=false);}}
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Search anime',style:TextStyle(fontWeight:FontWeight.w900))),
 body:Column(children:[Padding(padding:const EdgeInsets.all(16),child:Row(children:[Expanded(child:TextField(controller:controller,textInputAction:TextInputAction.search,onSubmitted:(_)=>submit(),decoration:const InputDecoration(hintText:'Search titles...',prefixIcon:Icon(Icons.search_rounded)))),const SizedBox(width:8),IconButton.filled(onPressed:busy?null:submit,icon:busy?const SizedBox(width:18,height:18,child:CircularProgressIndicator(strokeWidth:2)):const Icon(Icons.arrow_forward_rounded))])),
 Expanded(child:future==null?const EmptyView(title:'Find your next favourite',message:'Search across the providers enabled in Settings.'):FutureBuilder<List<Anime>>(future:future,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Center(child:CircularProgressIndicator());if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:submit);final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No results',message:'Try a different title.');return GridView.builder(padding:const EdgeInsets.all(16),itemCount:list.length,gridDelegate:const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount:2,mainAxisSpacing:15,crossAxisSpacing:13,childAspectRatio:.59),itemBuilder:(context,i)=>AnimeCard(anime:list[i],onTap:()=>widget.onOpen(list[i])));})),
 ]));}
class DetailsScreen extends StatefulWidget {const DetailsScreen({super.key,required this.anime,required this.onWatch});final Anime anime;final ValueChanged<Anime> onWatch;@override State<DetailsScreen> createState()=>_DetailsScreenState();}
class _DetailsScreenState extends State<DetailsScreen>{
 late Future<List<Anime>> episodes=AniApi.episodes(widget.anime);late Future<dynamic> details=AniApi.details(widget.anime);
 String findDescription(dynamic v){if(v is Map){for(final k in ['description','synopsis','overview','summary']){final x=v[k];if(x is String&&x.trim().isNotEmpty)return x.trim();}for(final x in v.values){final found=findDescription(x);if(found.isNotEmpty)return found;}}if(v is List){for(final x in v){final found=findDescription(x);if(found.isNotEmpty)return found;}}return '';}
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Anime details')),body:ListView(padding:const EdgeInsets.all(18),children:[
 ClipRRect(borderRadius:BorderRadius.circular(22),child:AspectRatio(aspectRatio:16/9,child:widget.anime.image.isEmpty?const PosterPlaceholder():Image.network(widget.anime.image,fit:BoxFit.cover,errorBuilder:(_,__,___)=>const PosterPlaceholder()))),
 const SizedBox(height:18),Text(widget.anime.title,style:const TextStyle(fontSize:26,fontWeight:FontWeight.w900)),const SizedBox(height:8),Text(widget.anime.source,style:const TextStyle(color:lime,fontWeight:FontWeight.w800,letterSpacing:1)),
 const SizedBox(height:12),FutureBuilder<dynamic>(future:details,builder:(context,snap){final d=widget.anime.description.isNotEmpty?widget.anime.description:findDescription(snap.data);return Text(d.isEmpty?'Choose an episode below to continue.':d,style:const TextStyle(color:Color(0xFFB0B5C1),height:1.5));}),
 const SizedBox(height:24),const Text('EPISODES',style:TextStyle(fontWeight:FontWeight.w900,letterSpacing:1.5)),const SizedBox(height:10),
 FutureBuilder<List<Anime>>(future:episodes,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(25),child:Center(child:CircularProgressIndicator()));if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>episodes=AniApi.episodes(widget.anime)));final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No episodes found',message:'The selected source returned no episode links.');return Column(children:list.asMap().entries.map((e)=>Card(child:ListTile(leading:CircleAvatar(backgroundColor:lime.withValues(alpha:.13),child:Text('${e.key+1}',style:const TextStyle(color:lime,fontWeight:FontWeight.w900))),title:Text(e.value.title,maxLines:2,overflow:TextOverflow.ellipsis),trailing:const Icon(Icons.play_arrow_rounded,color:lime),onTap:(){widget.onWatch(e.value);Navigator.push(context,MaterialPageRoute(builder:(_)=>WatchScreen(episode:e.value,playlist:list,startIndex:e.key,animeTitle:widget.anime.title)));}))).toList());})
 ]));}
class WatchScreen extends StatefulWidget {
  const WatchScreen({
    super.key,
    required this.episode,
    this.playlist = const [],
    this.startIndex = 0,
    this.animeTitle = '',
  });
  final Anime episode;
  final List<Anime> playlist;
  final int startIndex;
  final String animeTitle;
  @override State<WatchScreen> createState() => _WatchScreenState();
}

class _WatchScreenState extends State<WatchScreen> {
  Anime? currentEpisode;
  int currentIndex = 0;
  List<StreamChoice> streams = [];
  final TextEditingController serverFilterController = TextEditingController();
  String serverFilter = '';
  bool showAllServers = false;
  bool redirectBlockerEnabled = true;
  bool keepScreenAwake = true;
  StreamChoice? active;
  VideoPlayerController? controller;
  InAppWebViewController? webController;
  bool loadingStreams = true;
  bool loadingPlayer = false;
  bool webLoading = false;
  bool fullscreen = false;
  bool webFullscreen = false;
  GlobalKey _webViewKey = GlobalKey();
  bool locked = false;
  bool showControls = true;
  bool externalBusy = false;
  double speed = 1.0;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  String? errorMessage;
  Timer? hideTimer;
  bool _episodeEndedHandled = false;

  String get _animeTitle => widget.animeTitle.isNotEmpty ? widget.animeTitle : (currentEpisode?.title ?? 'AniSteam');
  bool get _toonStreamOnly => (currentEpisode?.source ?? widget.episode.source).toLowerCase().contains('toonstream');
  bool _isPreferredToonStreamServer(StreamChoice choice) {
    final title = choice.title.toLowerCase();
    final host = Uri.tryParse(choice.url)?.host.toLowerCase() ?? '';
    if (choice.isShortLink || title.contains('short') || title.contains('abyss') ||
        host == 'abyssplayer.com' || host.endsWith('.abyssplayer.com')) {
      return true;
    }
    if (RegExp(r'(^|[^a-z])moly([^a-z]|$)').hasMatch(title) || host.contains('moly')) {
      return true;
    }
    if (RegExp(r'(^|[^a-z])play([^a-z]|$)').hasMatch(title) || host.startsWith('play.')) {
      return true;
    }
    return false;
  }

  List<StreamChoice> get _filteredStreams {
    var visible = streams;
    if (_toonStreamOnly && !showAllServers) {
      visible = visible.where(_isPreferredToonStreamServer).toList();
    }
    final query = serverFilter.trim().toLowerCase();
    if (query.isEmpty) return visible;
    return visible.where((choice) {
      final host = Uri.tryParse(choice.url)?.host.toLowerCase() ?? '';
      return choice.title.toLowerCase().contains(query) ||
          choice.source.toLowerCase().contains(query) ||
          host.contains(query) ||
          (query == 'play' && choice.playbackKind.toLowerCase().contains('player')) ||
          (query == 'short' && (choice.isShortLink || choice.title.toLowerCase().contains('abyss')));
    }).toList();
  }

  bool _isBlockedRedirectHost(String host) {
    final normalized = host.toLowerCase().replaceFirst(RegExp(r'^www\.'), '');
    const blockedDomains = <String>{
      'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
      'adservice.google.com', 'popads.net', 'popcash.net', 'onclickads.net',
      'onclickmega.com', 'exoclick.com', 'trafficjunky.net', 'juicyads.com',
      'adnxs.com', 'adform.net', 'taboola.com', 'outbrain.com',
    };
    return blockedDomains.any((domain) =>
        normalized == domain || normalized.endsWith('.' + domain));
  }
  bool get _hasPlaylist => widget.playlist.isNotEmpty;
  bool get _hasPrevious => _hasPlaylist && currentIndex > 0;
  bool get _hasNext => _hasPlaylist && currentIndex < widget.playlist.length - 1;

  @override void initState() {
    super.initState();
    currentEpisode = widget.episode;
    currentIndex = widget.startIndex;
    _loadPlaybackSettingsAndPlay();
  }

  Future<void> _loadPlaybackSettingsAndPlay() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      showAllServers = prefs.getBool('show_all_servers') ?? false;
      redirectBlockerEnabled = prefs.getBool('redirect_blocker_enabled') ?? true;
      keepScreenAwake = prefs.getBool('keep_screen_awake') ?? true;
    } catch (_) {}
    if (!mounted) return;
    if (keepScreenAwake) {
      try { await WakelockPlus.enable(); } catch (_) {}
    }
    await _loadEpisode(widget.episode, index: widget.startIndex);
  }

  @override void dispose() {
    hideTimer?.cancel();
    serverFilterController.dispose();
    try { unawaited(WakelockPlus.disable()); } catch (_) {}
    final old = controller;
    if (old != null) {
      old.removeListener(_onVideoTick);
      old.dispose();
    }
    SystemChrome.setPreferredOrientations([]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  Future<void> _loadEpisode(Anime episode, {int? index}) async {
    hideTimer?.cancel();
    final old = controller;
    controller = null;
    if (old != null) {
      old.removeListener(_onVideoTick);
      await old.dispose();
    }
    if (!mounted) return;
    serverFilterController.clear();
    setState(() {
      currentEpisode = episode;
      if (index != null) currentIndex = index;
      serverFilter = '';
      streams = [];
      active = null;
      webController = null;
      loadingStreams = true;
      loadingPlayer = false;
      webLoading = false;
      errorMessage = null;
      position = Duration.zero;
      duration = Duration.zero;
      _episodeEndedHandled = false;
      showControls = true;
    });
    try {
      final result = await AniApi.streams(episode);
      if (!mounted) return;
      setState(() {
        streams = result;
        loadingStreams = false;
      });
      if (result.isEmpty) {
        setState(() => errorMessage = 'No playable servers were found for this episode.');
        return;
      }
      final preferredPool = _toonStreamOnly && !showAllServers
          ? result.where(_isPreferredToonStreamServer).toList()
          : result;
      if (_toonStreamOnly && !showAllServers && preferredPool.isEmpty) {
        setState(() => errorMessage =
            'Auto-filter could not find Moly, Short or Play. Enable “Show all servers” in Settings to inspect the complete list.');
        return;
      }
      final pool = preferredPool.isEmpty ? result : preferredPool;
      final preferred = pool.firstWhere((item) => item.isDirect, orElse: () => pool.first);
      await _selectStream(preferred);
    } catch (e) {
      if (mounted) {
        setState(() {
          loadingStreams = false;
          loadingPlayer = false;
          errorMessage = 'AniSteam could not find a playable server for this episode. Try refreshing the server list.';
        });
      }
    }
  }

  Future<void> _selectStream(StreamChoice choice) async {
    final old = controller;
    controller = null;
    if (old != null) {
      old.removeListener(_onVideoTick);
      await old.dispose();
    }
    webController = null;
    if (active?.url != choice.url) _webViewKey = GlobalKey();
    if (!mounted) return;
    setState(() {
      active = choice;
      loadingPlayer = choice.isDirect;
      webLoading = !choice.isDirect;
      errorMessage = null;
      position = Duration.zero;
      duration = Duration.zero;
      showControls = true;
      _episodeEndedHandled = false;
    });

    if (!choice.isDirect) {
      _scheduleHide();
      return;
    }

    try {
      final uri = Uri.tryParse(choice.url);
      if (uri == null || !uri.hasScheme || (uri.scheme != 'https' && uri.scheme != 'http')) {
        throw Exception('Invalid media URL');
      }
      final next = VideoPlayerController.networkUrl(
        uri,
        videoPlayerOptions: VideoPlayerOptions(mixWithOthers: false),
      );
      controller = next;
      await next.initialize();
      if (!mounted || controller != next) {
        await next.dispose();
        return;
      }
      next.addListener(_onVideoTick);
      await next.setPlaybackSpeed(speed);
      await next.play();
      setState(() => loadingPlayer = false);
      _scheduleHide();
    } catch (_) {
      if (mounted) {
        setState(() {
          loadingPlayer = false;
          errorMessage = 'This direct stream could not be played in AniSteam. Try another server or open it in an external player.';
        });
      }
    }
  }

  void _onVideoTick() {
    final c = controller;
    if (!mounted || c == null || !c.value.isInitialized) return;
    final value = c.value;
    if (value.hasError) {
      if (errorMessage == null) {
        setState(() => errorMessage = 'Playback failed for this server. Choose another server or open it externally.');
      }
      return;
    }
    final ended = value.position >= value.duration && value.duration > Duration.zero;
    setState(() {
      position = value.position;
      duration = value.duration;
    });
    if (ended && !_episodeEndedHandled) {
      _episodeEndedHandled = true;
      if (_hasNext) _playEpisode(currentIndex + 1);
    }
  }

  Future<void> _playEpisode(int index) async {
    if (!_hasPlaylist || index < 0 || index >= widget.playlist.length) return;
    await _loadEpisode(widget.playlist[index], index: index);
  }

  Future<void> _reloadPlayback() async {
    final choice = active;
    if (choice == null) { await _refreshServers(); return; }
    if (!choice.isDirect && webController != null) {
      await webController!.reload();
    } else {
      await _selectStream(choice);
    }
  }

  Future<void> _refreshServers() async {
    final episode = currentEpisode;
    if (episode == null) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Refreshing available servers…')));
    await _loadEpisode(episode, index: currentIndex);
  }

  Future<void> _openExternal() async {
    final choice = active;
    if (choice == null || externalBusy) return;
    final shouldOpen = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Open external player?'),
        content: const Text('AniSteam will hand this server link to Android so a compatible browser or video player can open it.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Open')),
        ],
      ),
    );
    if (shouldOpen != true || !mounted) return;
    setState(() => externalBusy = true);
    try {
      final uri = Uri.parse(choice.url);
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!opened && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No compatible external app could open this link.')));
      }
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not open the external player.')));
    } finally {
      if (mounted) setState(() => externalBusy = false);
    }
  }

  Future<void> _toggleFullscreen() async {
    if (fullscreen) {
      await _leaveFullscreen();
    } else {
      await _enterFullscreen();
    }
  }

  Future<void> _enterFullscreen() async {
    if (!mounted || fullscreen) return;
    setState(() { fullscreen = true; showControls = true; });
    await SystemChrome.setPreferredOrientations(const [
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _scheduleHide();
  }

  Future<void> _leaveFullscreen() async {
    if (!mounted || !fullscreen) return;
    setState(() { fullscreen = false; showControls = true; });
    await SystemChrome.setPreferredOrientations([]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    _scheduleHide();
  }

  void _scheduleHide() {
    hideTimer?.cancel();
    if (locked || !showControls) return;
    hideTimer = Timer(const Duration(seconds: 5), () {
      if (mounted && !locked && controller?.value.isPlaying == true) {
        setState(() => showControls = false);
      }
    });
  }

  void _toggleControls() {
    if (locked) return;
    setState(() => showControls = !showControls);
    _scheduleHide();
  }

  Future<void> _togglePlayPause() async {
    final c = controller;
    if (c == null || !c.value.isInitialized) return;
    if (c.value.isPlaying) {
      await c.pause();
      setState(() => showControls = true);
    } else {
      await c.play();
      setState(() => showControls = true);
      _scheduleHide();
    }
  }

  Future<void> _showSpeedPicker() async {
    final selected = await showModalBottomSheet<double>(
      context: context,
      backgroundColor: const Color(0xFF171A23),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Playback speed', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 12),
            Wrap(spacing: 8, runSpacing: 8, children: [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0].map((value) =>
              ChoiceChip(
                label: Text(value == 1.0 ? 'Normal' : value.toString() + '×'),
                selected: speed == value,
                onSelected: (_) => Navigator.pop(context, value),
              )).toList()),
          ]),
        ),
      ),
    );
    if (selected == null || !mounted) return;
    setState(() => speed = selected);
    try { await controller?.setPlaybackSpeed(selected); } catch (_) {}
  }

  Future<void> _showServerPicker() async {
    final selected = await showModalBottomSheet<StreamChoice>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF10131A),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 18),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Row(children: [
              const Expanded(child: Text('Playback servers', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900))),
              IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close_rounded)),
            ]),
            const Text('Direct streams use the native video player. Short links and embed pages open inside AniSteam’s WebView.', style: TextStyle(color: Color(0xFF9CA2B0))),
            const SizedBox(height: 12),
            Flexible(child: ListView.separated(
              shrinkWrap: true,
              itemCount: streams.length,
              separatorBuilder: (_, __) => const Divider(height: 1, color: Color(0xFF292D38)),
              itemBuilder: (context, index) {
                final item = streams[index];
                return ListTile(
                  leading: Icon(item.isDirect ? Icons.play_circle_fill_rounded : Icons.language_rounded, color: item.url == active?.url ? lime : const Color(0xFF9CA2B0), size: 29),
                  title: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                  subtitle: Text(item.playbackKind + ' · ' + item.language),
                  trailing: item.url == active?.url ? const Icon(Icons.check_circle_rounded, color: lime) : const Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.pop(context, item),
                );
              },
            )),
            const SizedBox(height: 12),
            SizedBox(width: double.infinity, child: OutlinedButton.icon(
              onPressed: () => Navigator.pop(context, const StreamChoice(title: '__refresh__', url: 'https://invalid.local')),
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Refresh servers'),
            )),
          ]),
        ),
      ),
    );
    if (selected == null || !mounted) return;
    if (selected.title == '__refresh__') {
      await _refreshServers();
    } else {
      await _selectStream(selected);
    }
  }

  Future<void> _showEpisodes() async {
    if (!_hasPlaylist) return;
    final selected = await showModalBottomSheet<int>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF10131A),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 18),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Row(children: [
              const Expanded(child: Text('Episodes', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900))),
              IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close_rounded)),
            ]),
            Flexible(child: ListView.separated(
              shrinkWrap: true,
              itemCount: widget.playlist.length,
              separatorBuilder: (_, __) => const Divider(height: 1, color: Color(0xFF292D38)),
              itemBuilder: (context, index) {
                final episode = widget.playlist[index];
                return ListTile(
                  leading: CircleAvatar(backgroundColor: index == currentIndex ? lime.withValues(alpha: .15) : const Color(0xFF252936), child: Text((index + 1).toString(), style: TextStyle(color: index == currentIndex ? lime : Colors.white))),
                  title: Text(episode.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                  subtitle: Text(episode.source),
                  trailing: index == currentIndex ? const Icon(Icons.equalizer_rounded, color: lime) : null,
                  onTap: () => Navigator.pop(context, index),
                );
              },
            )),
          ]),
        ),
      ),
    );
    if (selected != null && mounted && selected != currentIndex) await _playEpisode(selected);
  }

  Widget _playerSurface() {
    final choice = active;
    if (choice == null) {
      return const ColoredBox(color: Colors.black, child: Center(child: CircularProgressIndicator()));
    }
    if (choice.isDirect) {
      final c = controller;
      if (c == null || !c.value.isInitialized) {
        return const ColoredBox(color: Colors.black, child: Center(child: CircularProgressIndicator()));
      }
      return ColoredBox(
        color: Colors.black,
        child: Center(
          child: FittedBox(
            fit: BoxFit.contain,
            child: SizedBox(
              width: c.value.size.width,
              height: c.value.size.height,
              child: VideoPlayer(c),
            ),
          ),
        ),
      );
    }

    final useAbyssIframe = _toonStreamOnly && (choice.isShortLink || choice.title.toLowerCase().contains('short') || choice.title.toLowerCase().contains('abyss'));
    final iframeSrc = choice.url.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    final shortServerHtml = '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"></head><body style="margin:0;background:#000;overflow:hidden"><iframe width="640" height="360" src="$iframeSrc" frameborder="0" scrolling="0" allowfullscreen style="border:0;width:100vw;height:100vh;display:block"></iframe></body></html>';
    return InAppWebView(
      key: _webViewKey,
      initialData: useAbyssIframe ? InAppWebViewInitialData(data: shortServerHtml, baseUrl: WebUri(Uri.parse(choice.url).origin)) : null,
      initialUrlRequest: useAbyssIframe ? null : URLRequest(url: WebUri(choice.url)),
      initialSettings: InAppWebViewSettings(
        javaScriptEnabled: true,
        domStorageEnabled: true,
        databaseEnabled: true,
        mediaPlaybackRequiresUserGesture: false,
        iframeAllowFullscreen: true,
        isElementFullscreenEnabled: true,
        allowsInlineMediaPlayback: true,
        mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
        useWideViewPort: true,
        loadWithOverviewMode: true,
        allowFileAccess: false,
        allowContentAccess: true,
        supportMultipleWindows: false,
        javaScriptCanOpenWindowsAutomatically: false,
        userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
      ),
      onWebViewCreated: (web) => webController = web,
      onLoadStart: (web, url) {
        if (mounted) setState(() => webLoading = true);
      },
      onLoadStop: (web, url) {
        if (mounted) setState(() => webLoading = false);
      },
      shouldOverrideUrlLoading: (web, action) async {
        final uri = action.request.url;
        if (uri == null) return NavigationActionPolicy.CANCEL;
        final scheme = uri.scheme.toLowerCase();
        if (scheme != 'https' && scheme != 'http') return NavigationActionPolicy.CANCEL;
        if (redirectBlockerEnabled && _isBlockedRedirectHost(uri.host)) {
          return NavigationActionPolicy.CANCEL;
        }
        return NavigationActionPolicy.ALLOW;
      },
      onReceivedError: (web, request, error) {
        if (request.isForMainFrame == true && mounted) {
          setState(() {
            webLoading = false;
            errorMessage = 'This embedded server could not load. Try another server or open it externally.';
          });
        }
      },
      onEnterFullscreen: (web) async {
        if (!mounted || webFullscreen) return;
        setState(() => webFullscreen = true);
        await SystemChrome.setPreferredOrientations(const [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight]);
        await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
      },
      onExitFullscreen: (web) async {
        if (!mounted || !webFullscreen) return;
        setState(() => webFullscreen = false);
        await SystemChrome.setPreferredOrientations([]);
        await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      },
    );
  }

  Widget _directControls({required bool isFullscreen}) {
    final c = controller;
    final value = c?.value;
    final maxMs = duration.inMilliseconds > 0 ? duration.inMilliseconds.toDouble() : 1.0;
    final currentMs = position.inMilliseconds.clamp(0, maxMs.toInt()).toDouble();
    return Positioned.fill(
      child: IgnorePointer(
        ignoring: !showControls || locked,
        child: AnimatedOpacity(
          opacity: showControls && !locked ? 1 : 0,
          duration: const Duration(milliseconds: 180),
          child: DecoratedBox(
            decoration: const BoxDecoration(gradient: LinearGradient(
              begin: Alignment.topCenter, end: Alignment.bottomCenter,
              colors: [Color(0xB8000000), Colors.transparent, Colors.transparent, Color(0xDD000000)],
              stops: [0, .25, .62, 1],
            )),
            child: Stack(children: [
              Positioned(left: 6, right: 6, top: 0, child: Row(children: [
                IconButton(onPressed: () { if (isFullscreen) _leaveFullscreen(); else Navigator.maybePop(context); }, icon: const Icon(Icons.arrow_back_rounded, color: Colors.white)),
                Expanded(child: Text(_animeTitle + ' · ' + (currentEpisode?.title ?? ''), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800))),
                IconButton(onPressed: _showServerPicker, icon: const Icon(Icons.dns_rounded, color: Colors.white)),
                IconButton(onPressed: _openExternal, icon: const Icon(Icons.open_in_new_rounded, color: Colors.white)),
              ])),
              Center(child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                IconButton(onPressed: _hasPrevious ? () => _playEpisode(currentIndex - 1) : null, iconSize: 36, icon: const Icon(Icons.skip_previous_rounded, color: Colors.white)),
                const SizedBox(width: 18),
                IconButton.filled(onPressed: _togglePlayPause, iconSize: 36, style: IconButton.styleFrom(backgroundColor: Colors.white.withValues(alpha: .18)), icon: Icon(value?.isPlaying == true ? Icons.pause_rounded : Icons.play_arrow_rounded, color: Colors.white)),
                const SizedBox(width: 18),
                IconButton(onPressed: _hasNext ? () => _playEpisode(currentIndex + 1) : null, iconSize: 36, icon: const Icon(Icons.skip_next_rounded, color: Colors.white)),
              ])),
              Positioned(left: 14, right: 14, bottom: 8, child: Column(mainAxisSize: MainAxisSize.min, children: [
                if (duration > Duration.zero) Slider(
                  value: currentMs, min: 0, max: maxMs,
                  onChanged: (ms) => setState(() => position = Duration(milliseconds: ms.round())),
                  onChangeEnd: (ms) => controller?.seekTo(Duration(milliseconds: ms.round())),
                  activeColor: lime, inactiveColor: Colors.white24,
                ),
                Row(children: [
                  Text(_durationText(position) + ' / ' + _durationText(duration), style: const TextStyle(color: Colors.white, fontSize: 11)),
                  const Spacer(),
                  Text(speed.toString() + '×', style: const TextStyle(color: Colors.white, fontSize: 12)),
                  IconButton(onPressed: _showSpeedPicker, icon: const Icon(Icons.speed_rounded, color: Colors.white)),
                  IconButton(onPressed: () => setState(() { locked = true; showControls = false; }), icon: const Icon(Icons.lock_outline_rounded, color: Colors.white)),
                  IconButton(onPressed: _toggleFullscreen, icon: Icon(isFullscreen ? Icons.fullscreen_exit_rounded : Icons.fullscreen_rounded, color: Colors.white)),
                ]),
              ])),
            ]),
          ),
        ),
      ),
    );
  }

  String _durationText(Duration value) {
    final seconds = value.inSeconds;
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    final remainder = seconds % 60;
    final mm = minutes.toString().padLeft(2, '0');
    final ss = remainder.toString().padLeft(2, '0');
    return hours > 0 ? hours.toString() + ':' + mm + ':' + ss : mm + ':' + ss;
  }

  Widget _playerArea({required bool isFullscreen}) {
    final surface = _playerSurface();
    return Stack(
      fit: StackFit.expand,
      children: [
        if (active?.isDirect == true)
          GestureDetector(behavior: HitTestBehavior.opaque, onTap: _toggleControls, child: surface)
        else
          surface,
        if (loadingPlayer || webLoading)
          const ColoredBox(color: Color(0x55000000), child: Center(child: CircularProgressIndicator(color: lime))),
        if (active?.isDirect == true) _directControls(isFullscreen: isFullscreen),
        if (active?.isDirect != true && isFullscreen && showControls && !locked)
          Positioned(left: 6, right: 6, top: 0, child: DecoratedBox(
            decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xCC000000), Colors.transparent])),
            child: Row(children: [
              IconButton(onPressed: _leaveFullscreen, icon: const Icon(Icons.arrow_back_rounded, color: Colors.white)),
              Expanded(child: Text(currentEpisode?.title ?? 'AniSteam Player', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800))),
              IconButton(onPressed: _showServerPicker, icon: const Icon(Icons.dns_rounded, color: Colors.white)),
              IconButton(onPressed: _openExternal, icon: const Icon(Icons.open_in_new_rounded, color: Colors.white)),
              IconButton(onPressed: _toggleFullscreen, icon: const Icon(Icons.fullscreen_exit_rounded, color: Colors.white)),
            ]),
          )),
        if (locked)
          Positioned(right: 14, bottom: 14, child: FilledButton.tonalIcon(
            onPressed: () => setState(() { locked = false; showControls = true; _scheduleHide(); }),
            icon: const Icon(Icons.lock_open_rounded),
            label: const Text('Unlock controls'),
          )),
      ],
    );
  }

  @override Widget build(BuildContext context) {
    if (fullscreen) {
      return WillPopScope(
        onWillPop: () async { await _leaveFullscreen(); return false; },
        child: Scaffold(
          backgroundColor: Colors.black,
          body: SizedBox.expand(child: _playerArea(isFullscreen: true)),
        ),
      );
    }

    return WillPopScope(
      onWillPop: () async {
        if (fullscreen) { await _leaveFullscreen(); return false; }
        return true;
      },
      child: Scaffold(
        appBar: AppBar(
          titleSpacing: 0,
          title: const Row(children: [
            AniSteamLogo(size: 30),
            SizedBox(width: 8),
            Text('AniSteam Player', style: TextStyle(fontWeight: FontWeight.w900, fontSize: 17)),
          ]),
          actions: [
            IconButton(tooltip: 'Reload player', onPressed: _reloadPlayback, icon: const Icon(Icons.refresh_rounded)),
            IconButton(tooltip: 'Choose server', onPressed: streams.isEmpty ? null : _showServerPicker, icon: const Icon(Icons.dns_rounded)),
            IconButton(tooltip: 'Fullscreen', onPressed: active == null ? null : _toggleFullscreen, icon: const Icon(Icons.fullscreen_rounded)),
          ],
        ),
        body: loadingStreams
          ? const Center(child: CircularProgressIndicator())
          : errorMessage != null && active == null
            ? Center(child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.cloud_off_rounded, color: Color(0xFFFFB86B), size: 42),
                  const SizedBox(height: 12),
                  const Text('Playback unavailable', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
                  const SizedBox(height: 8),
                  Text(errorMessage!, textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF9CA2B0))),
                  const SizedBox(height: 16),
                  FilledButton.icon(onPressed: _refreshServers, icon: const Icon(Icons.refresh_rounded), label: const Text('Retry servers')),
                ]),
              ))
            : ListView(
                children: [
                  AspectRatio(aspectRatio: 16 / 9, child: _playerArea(isFullscreen: false)),
                  if (errorMessage != null)
                    Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 0), child: Text(errorMessage!, style: const TextStyle(color: Color(0xFFFFB86B)))),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(_animeTitle, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
                      const SizedBox(height: 5),
                      Text((currentEpisode?.title ?? 'Playing episode') + ' · ' + (active?.title ?? 'Finding server'), style: const TextStyle(color: Color(0xFF9CA2B0))),
                      const SizedBox(height: 14),
                      Row(children: [
                        Expanded(child: Text('PLAYBACK SERVERS', style: const TextStyle(color: Color(0xFF9CA2B0), fontWeight: FontWeight.w800, letterSpacing: 1.2))),
                        TextButton.icon(onPressed: _refreshServers, icon: const Icon(Icons.refresh_rounded, size: 18), label: const Text('Refresh')),
                      ]),
                      if (streams.isEmpty)
                        const Text('No servers returned for this episode.', style: TextStyle(color: Color(0xFF9CA2B0)))
                      else ...[
                        TextField(
                          key: const ValueKey('server-filter'),
                          controller: serverFilterController,
                          onChanged: (value) => setState(() => serverFilter = value),
                          decoration: InputDecoration(
                            hintText: 'Search servers by name',
                            prefixIcon: const Icon(Icons.search_rounded),
                            suffixIcon: serverFilter.isEmpty ? null : IconButton(
                              tooltip: 'Clear server filter',
                              onPressed: () {
                                serverFilterController.clear();
                                setState(() => serverFilter = '');
                              },
                              icon: const Icon(Icons.close_rounded),
                            ),
                            isDense: true,
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
                          ),
                        ),
                        const SizedBox(height: 8),
                        if (_toonStreamOnly && !showAllServers)
                          const Padding(
                            padding: EdgeInsets.only(bottom: 10),
                            child: Row(children: [
                              Icon(Icons.auto_awesome_rounded, size: 15, color: lime),
                              SizedBox(width: 6),
                              Expanded(child: Text('Auto filter · Moly, Short and Play',
                                style: TextStyle(color: Color(0xFF9CA2B0), fontSize: 12))),
                            ]),
                          ),
                        Builder(builder: (context) {
                          final visibleServers = _filteredStreams;
                          if (visibleServers.isEmpty) {
                            return Padding(
                              padding: const EdgeInsets.symmetric(vertical: 10),
                              child: Text(
                                serverFilter.isNotEmpty
                                  ? 'No matching servers. Clear the search or try another name.'
                                  : 'Auto-filter hid unrecognized servers. Enable “Show all servers” in Settings to view them.',
                                style: const TextStyle(color: Color(0xFF9CA2B0), height: 1.4),
                              ),
                            );
                          }
                          return Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: visibleServers.map((choice) =>
                              ChoiceChip(
                                selected: choice.url == active?.url,
                                label: Text(choice.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                                avatar: Icon(choice.isDirect ? Icons.play_arrow_rounded : Icons.language_rounded, size: 17),
                                onSelected: (_) => _selectStream(choice),
                              )).toList(),
                          );
                        }),
                      ],
                      const SizedBox(height: 16),
                      Card(child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(currentEpisode?.title ?? 'Episode', style: const TextStyle(fontWeight: FontWeight.w900)),
                          const SizedBox(height: 5),
                          Text(active?.playbackKind ?? 'Resolving playback server', style: const TextStyle(color: Color(0xFF9CA2B0))),
                          const SizedBox(height: 12),
                          Row(children: [
                            Expanded(child: OutlinedButton.icon(
                              onPressed: _hasPrevious ? () => _playEpisode(currentIndex - 1) : null,
                              icon: const Icon(Icons.skip_previous_rounded),
                              style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                              label: const Text('Prev', maxLines: 1, overflow: TextOverflow.ellipsis),
                            )),
                            const SizedBox(width: 8),
                            Expanded(child: OutlinedButton.icon(
                              onPressed: _hasPlaylist ? _showEpisodes : null,
                              icon: const Icon(Icons.playlist_play_rounded),
                              style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                              label: const Text('List', maxLines: 1, overflow: TextOverflow.ellipsis),
                            )),
                            const SizedBox(width: 8),
                            Expanded(child: FilledButton.icon(
                              onPressed: _hasNext ? () => _playEpisode(currentIndex + 1) : null,
                              icon: const Icon(Icons.skip_next_rounded),
                              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                              label: const Text('Next', maxLines: 1, overflow: TextOverflow.ellipsis),
                            )),
                          ]),
                        ]),
                      )),
                      const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Text('Short links and embedded player pages stay inside AniSteam. Direct MP4/HLS streams use the in-app video player; use Open external player to hand a link to another Android app.', style: TextStyle(color: Color(0xFF9CA2B0), height: 1.45))),
                    ]),
                  ),
                ],
              ),
      ),
    );
  }
}

class HistoryTab extends StatelessWidget {const HistoryTab({super.key,required this.items,required this.onClear,required this.onOpen});final List<Anime> items;final VoidCallback onClear;final ValueChanged<Anime> onOpen;@override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Watch history',style:TextStyle(fontWeight:FontWeight.w900)),actions:[IconButton(onPressed:items.isEmpty?null:onClear,icon:const Icon(Icons.delete_sweep_rounded))]),body:items.isEmpty?const EmptyView(title:'Nothing watched yet',message:'Episodes you open will be saved here on this device.'):ListView.builder(itemCount:items.length,itemBuilder:(context,i){final a=items[i];return ListTile(leading:const Icon(Icons.history_rounded,color:lime),title:Text(a.title,maxLines:2,overflow:TextOverflow.ellipsis),subtitle:Text(a.source),trailing:const Icon(Icons.chevron_right_rounded),onTap:()=>onOpen(a));}));}
class SettingsTab extends StatefulWidget {
  const SettingsTab({super.key, required this.sourceMode, required this.onMode});
  final String sourceMode;
  final ValueChanged<String> onMode;
  @override State<SettingsTab> createState() => _SettingsTabState();
}
class _SettingsTabState extends State<SettingsTab> {
  bool checking = false;
  String? connectionStatus;
  bool showAllServers = false;
  bool redirectBlockerEnabled = true;
  bool keepScreenAwake = true;

  @override
  void initState() {
    super.initState();
    _loadPlayerSettings();
  }

  Future<void> _loadPlayerSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!mounted) return;
      setState(() {
        showAllServers = prefs.getBool('show_all_servers') ?? false;
        redirectBlockerEnabled = prefs.getBool('redirect_blocker_enabled') ?? true;
        keepScreenAwake = prefs.getBool('keep_screen_awake') ?? true;
      });
    } catch (_) {}
  }

  Future<void> _savePlayerSetting(String key, bool value, VoidCallback applyValue) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, value);
    if (mounted) setState(applyValue);
  }

  Future<void> checkConnection() async {
    if (checking) return;
    setState(() { checking = true; connectionStatus = 'Checking connection…'; });
    try {
      await AniApi.get('/health');
      if (mounted) setState(() => connectionStatus = 'Connected. AniSteam received a response.');
    } on Exception catch (e) {
      final message = e.toString();
      final dnsFailure = message.contains('Failed host lookup') ||
          message.contains('SocketException') ||
          message.contains('No address associated with hostname');
      if (mounted) {
        setState(() => connectionStatus = dnsFailure
          ? 'The AniSteam service could not be reached. Check your internet connection and try again later.'
          : 'Connection failed. Please try again later.');
      }
    } catch (_) {
      if (mounted) setState(() => connectionStatus = 'Connection failed. Please try again later.');
    } finally {
      if (mounted) setState(() => checking = false);
    }
  }

  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w900))),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('CONTENT SOURCES', style: TextStyle(color: Color(0xFF9CA2B0), fontWeight: FontWeight.w800, letterSpacing: 1.4)),
      const SizedBox(height:10),
      for (final option in const [('COMBINED','Combined','Search ToonStream and HiAnime'),('TOONSTREAM','ToonStream','Use ToonStream endpoints'),('HIANIME','HiAnime','Use HiAnime endpoints')])
        Card(child: RadioListTile<String>(
          value: option.$1,
          groupValue: widget.sourceMode,
          title: Text(option.$2, style: const TextStyle(fontWeight: FontWeight.w800)),
          subtitle: Text(option.$3),
          activeColor: lime,
          onChanged: (value) { if (value != null) widget.onMode(value); },
        )),
      const SizedBox(height:22),
      const Text('PLAYER PREFERENCES', style: TextStyle(color: Color(0xFF9CA2B0), fontWeight: FontWeight.w800, letterSpacing: 1.4)),
      const SizedBox(height:10),
      Card(
        child: SwitchListTile(
          value: showAllServers,
          onChanged: (value) => _savePlayerSetting('show_all_servers', value, () => showAllServers = value),
          secondary: const Icon(Icons.dns_rounded),
          title: const Text('Show all servers', style: TextStyle(fontWeight: FontWeight.w800)),
          subtitle: const Text('Off: automatically show Moly, Short and Play for ToonStream. On: list all returned servers.'),
          activeColor: lime,
        ),
      ),
      Card(
        child: SwitchListTile(
          value: redirectBlockerEnabled,
          onChanged: (value) => _savePlayerSetting('redirect_blocker_enabled', value, () => redirectBlockerEnabled = value),
          secondary: const Icon(Icons.shield_outlined),
          title: const Text('Redirect blocker', style: TextStyle(fontWeight: FontWeight.w800)),
          subtitle: const Text('Block known ad domains and stop links that try to open other Android apps.'),
          activeColor: lime,
        ),
      ),
      Card(
        child: SwitchListTile(
          value: keepScreenAwake,
          onChanged: (value) => _savePlayerSetting('keep_screen_awake', value, () => keepScreenAwake = value),
          secondary: const Icon(Icons.screen_lock_portrait_outlined),
          title: const Text('Keep screen awake', style: TextStyle(fontWeight: FontWeight.w800)),
          subtitle: const Text('Prevent the display from sleeping while the player is open.'),
          activeColor: lime,
        ),
      ),
      const SizedBox(height:22),
      const Text('SERVICE STATUS', style: TextStyle(color: Color(0xFF9CA2B0), fontWeight: FontWeight.w800, letterSpacing: 1.4)),
      const SizedBox(height:8),
      const Text('The service connection is managed privately by AniSteam. Its server address is not shown or editable here.', style: TextStyle(color: Color(0xFF9CA2B0), height: 1.45)),
      const SizedBox(height:12),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: checking ? null : checkConnection,
          icon: checking
            ? const SizedBox(width:18,height:18,child:CircularProgressIndicator(strokeWidth:2))
            : const Icon(Icons.wifi_tethering_rounded),
          label: Text(checking ? 'Checking…' : 'Check connection'),
        ),
      ),
      if (connectionStatus != null)
        Padding(
          padding: const EdgeInsets.only(top:12),
          child: Text(connectionStatus!, style: TextStyle(
            color: connectionStatus!.startsWith('Connected') ? lime : const Color(0xFFFFB86B),
            height:1.4,
          )),
        ),
      const SizedBox(height:22),
      const Text('ABOUT', style: TextStyle(color: Color(0xFF9CA2B0), fontWeight: FontWeight.w800, letterSpacing: 1.4)),
      const SizedBox(height:10),
      const Card(child:ListTile(leading:AniSteamLogo(size:38),title:Text('AniSteam',style:TextStyle(fontWeight:FontWeight.w900)),subtitle:Text('Anime discovery · episode lists · stream selection · local watch history'))),
      const Padding(padding:EdgeInsets.all(8),child:Text('AniSteam connects to its configured service. Playback availability depends on the provider and returned stream. It does not bypass DRM, login, paywalls, CAPTCHA, or anti-bot restrictions.',style:TextStyle(color:Color(0xFF9CA2B0),height:1.45))),
    ]),
  );
}
class AniSteamLogo extends StatelessWidget {
  const AniSteamLogo({super.key, this.size = 34});
  final double size;
  @override
  Widget build(BuildContext context) => Container(
    width: size, height: size,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(size * 0.27),
      gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xFF242732), Color(0xFF101218)]),
      border: Border.all(color: lime.withValues(alpha: 0.65), width: 1),
    ),
    child: Stack(alignment: Alignment.center, children: [
      Icon(Icons.play_arrow_rounded, color: const Color(0xFFFF7A18), size: size * 0.88),
      Positioned(top: size * 0.17, right: size * 0.20, child: Container(width: size * 0.20, height: size * 0.055, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(size)))),
      Positioned(bottom: size * 0.22, right: size * 0.18, child: Container(width: size * 0.15, height: size * 0.05, decoration: BoxDecoration(color: const Color(0xFFFFB15A), borderRadius: BorderRadius.circular(size)))),
    ]),
  );
}

class Brand extends StatelessWidget {
  const Brand({super.key});
  @override
  Widget build(BuildContext context) => Row(children: [
    const AniSteamLogo(size: 34),
    const SizedBox(width: 9),
    const Text('AniSteam', style: TextStyle(fontWeight: FontWeight.w900)),
  ]);
}
class AnimeCard extends StatelessWidget {const AnimeCard({super.key,required this.anime,required this.onTap});final Anime anime;final VoidCallback onTap;@override Widget build(BuildContext context)=>InkWell(onTap:onTap,borderRadius:BorderRadius.circular(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Expanded(child:ClipRRect(borderRadius:BorderRadius.circular(17),child:Stack(fit:StackFit.expand,children:[if(anime.image.isNotEmpty)Image.network(anime.image,fit:BoxFit.cover,errorBuilder:(_,__,___)=>const PosterPlaceholder())else const PosterPlaceholder(),const Positioned(right:10,top:10,child:Icon(Icons.play_circle_fill_rounded,color:lime,size:27))]))),const SizedBox(height:9),Text(anime.title,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800,fontSize:13)),const SizedBox(height:4),Text(anime.source.toUpperCase(),style:const TextStyle(color:Color(0xFF9CA2B0),fontSize:9,letterSpacing:1.1))]));}
class PosterPlaceholder extends StatelessWidget {const PosterPlaceholder({super.key});@override Widget build(BuildContext context)=>Container(color:const Color(0xFF1B202B),child:const Center(child:Icon(Icons.movie_outlined,color:Color(0xFF6F7787),size:45)));}
class ErrorView extends StatelessWidget {const ErrorView({super.key,required this.message,required this.retry});final String message;final VoidCallback retry;@override Widget build(BuildContext context){final networkFailure=message.contains('Failed host lookup')||message.contains('SocketException')||message.contains('No address associated with hostname');final shown=networkFailure?'AniSteam could not reach its service. Check your internet connection and try again in a moment.':message;return Center(child:Padding(padding:const EdgeInsets.all(22),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.cloud_off_rounded,size:40,color:Color(0xFFFFB86B)),const SizedBox(height:12),const Text('Could not load data',style:TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(shown,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0))),const SizedBox(height:14),OutlinedButton.icon(onPressed:retry,icon:const Icon(Icons.refresh_rounded),label:const Text('Try again'))])));}}
class EmptyView extends StatelessWidget {const EmptyView({super.key,required this.title,required this.message});final String title,message;@override Widget build(BuildContext context)=>Center(child:Padding(padding:const EdgeInsets.all(24),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.video_library_outlined,size:43,color:Color(0xFF858B99)),const SizedBox(height:12),Text(title,textAlign:TextAlign.center,style:const TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(message,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0),height:1.4))])));}