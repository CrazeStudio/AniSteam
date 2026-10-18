import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

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
  // Keep the service endpoint aligned with AniSteam-2. Ignore any legacy
  // api_base_url value saved by older builds; it may point to a stale deployment.
  // The endpoint is intentionally not editable or displayed in Settings.
  static Future<String> baseUrl() async => defaultApiBase.replaceFirst(RegExp(r'/+
  static Future<dynamic> get(String path, [Map<String,String>? query]) async {
    final base = await baseUrl();
    final parsedBase = Uri.tryParse(base);
    if (parsedBase == null || parsedBase.host.isEmpty || !['https','http'].contains(parsedBase.scheme)) {
      throw Exception('Invalid API URL. Open Settings and enter the API base URL.');
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
  static Future<List<Anime>> streams(Anime episode) async {
    final data=await get('/watch',{'url':episode.url});final out=<Anime>[];
    void walk(dynamic v,[String label='Stream']){
      if(v is String && (v.startsWith('https://')||v.startsWith('http://'))){out.add(Anime(title:label,url:v,source:episode.source));}
      else if(v is Map){
        final m=Map<String,dynamic>.from(v);final u=(m['url']??m['src']??m['file']??m['link']??'').toString();
        if(u.startsWith('https://')||u.startsWith('http://'))out.add(Anime(title:(m['name']??m['label']??m['quality']??label).toString(),url:u,source:episode.source));
        else{for(final e in m.entries){walk(e.value,e.key);}}
      }else if(v is List){for(var i=0;i<v.length;i++){walk(v[i],label=='Stream'?'Stream ${i+1}':label);}}
    }
    walk(data);final seen=<String>{};return out.where((a)=>seen.add(a.url)).toList();
  }
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
 FutureBuilder<List<Anime>>(future:episodes,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(25),child:Center(child:CircularProgressIndicator()));if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>episodes=AniApi.episodes(widget.anime)));final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No episodes found',message:'The selected source returned no episode links.');return Column(children:list.asMap().entries.map((e)=>Card(child:ListTile(leading:CircleAvatar(backgroundColor:lime.withValues(alpha:.13),child:Text('${e.key+1}',style:const TextStyle(color:lime,fontWeight:FontWeight.w900))),title:Text(e.value.title,maxLines:2,overflow:TextOverflow.ellipsis),trailing:const Icon(Icons.play_arrow_rounded,color:lime),onTap:(){widget.onWatch(e.value);Navigator.push(context,MaterialPageRoute(builder:(_)=>WatchScreen(episode:e.value)));}))).toList());})
 ]));}
class WatchScreen extends StatefulWidget {const WatchScreen({super.key,required this.episode});final Anime episode;@override State<WatchScreen> createState()=>_WatchScreenState();}
class _WatchScreenState extends State<WatchScreen>{
 late Future<List<Anime>> future=AniApi.streams(widget.episode);
 bool direct(String u){final p=u.toLowerCase().split('?').first;return p.endsWith('.mp4')||p.endsWith('.m3u8')||p.endsWith('.webm')||p.endsWith('.mkv');}
 Future<void> open(Anime a) async {if(direct(a.url)){if(mounted)Navigator.push(context,MaterialPageRoute(builder:(_)=>MediaScreen(stream:a)));return;}final uri=Uri.tryParse(a.url);if(uri==null||!['http','https'].contains(uri.scheme))return;final ok=await launchUrl(uri,mode:LaunchMode.externalApplication);if(!ok&&mounted)ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Could not open player link.')));}
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Choose a stream')),body:FutureBuilder<List<Anime>>(future:future,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Center(child:CircularProgressIndicator());if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>future=AniApi.streams(widget.episode)));final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No playable links',message:'The API did not return a stream for this episode.');return ListView(padding:const EdgeInsets.all(16),children:[Text(widget.episode.title,style:const TextStyle(fontSize:22,fontWeight:FontWeight.w900)),const SizedBox(height:8),const Text('External player pages open outside AniSteam.',style:TextStyle(color:Color(0xFF9CA2B0))),const SizedBox(height:16),...list.asMap().entries.map((e)=>Card(child:ListTile(leading:const Icon(Icons.play_circle_fill_rounded,color:lime,size:30),title:Text(e.value.title.isEmpty?'Stream ${e.key+1}':e.value.title),subtitle:Text(direct(e.value.url)?'Direct media':'External player'),trailing:const Icon(Icons.open_in_new_rounded),onTap:()=>open(e.value))))]);}));}
class MediaScreen extends StatefulWidget {const MediaScreen({super.key,required this.stream});final Anime stream;@override State<MediaScreen> createState()=>_MediaScreenState();}
class _MediaScreenState extends State<MediaScreen>{VideoPlayerController? controller;Object? error;@override void initState(){super.initState();init();}Future<void> init()async{try{final c=VideoPlayerController.networkUrl(Uri.parse(widget.stream.url));controller=c;await c.initialize();if(mounted)setState((){});}catch(e){if(mounted)setState(()=>error=e);}}@override void dispose(){controller?.dispose();super.dispose();}@override Widget build(BuildContext context){final c=controller;return Scaffold(appBar:AppBar(title:Text(widget.stream.title,maxLines:1,overflow:TextOverflow.ellipsis)),body:Center(child:error!=null?Padding(padding:const EdgeInsets.all(24),child:Text('Playback unavailable: $error')):c==null||!c.value.isInitialized?const CircularProgressIndicator():Column(mainAxisAlignment:MainAxisAlignment.center,children:[AspectRatio(aspectRatio:c.value.aspectRatio,child:VideoPlayer(c)),const SizedBox(height:18),IconButton.filledTonal(iconSize:34,onPressed:()=>setState(()=>c.value.isPlaying?c.pause():c.play()),icon:Icon(c.value.isPlaying?Icons.pause_rounded:Icons.play_arrow_rounded)),VideoProgressIndicator(c,allowScrubbing:true)])));}}
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

  Future<void> checkConnection() async {
    if (checking) return;
    setState(() { checking = true; connectionStatus = 'Checking connection…'; });
    try {
      await AniApi.get('/catalogue', {'page':'1'});
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
      const Card(child:ListTile(leading:Icon(Icons.play_circle_fill_rounded,color:lime,size:32),title:Text('AniSteam',style:TextStyle(fontWeight:FontWeight.w900)),subtitle:Text('Anime discovery · episode lists · stream selection · local watch history'))),
      const Padding(padding:EdgeInsets.all(8),child:Text('AniSteam connects to its configured service. Playback availability depends on the provider and returned stream. It does not bypass DRM, login, paywalls, CAPTCHA, or anti-bot restrictions.',style:TextStyle(color:Color(0xFF9CA2B0),height:1.45))),
    ]),
  );
}
class Brand extends StatelessWidget {const Brand({super.key});@override Widget build(BuildContext context)=>Row(children:[Image.asset('assets/anisteam_logo.webp',width:30,height:30,fit:BoxFit.contain,errorBuilder:(_,__,___)=>const Icon(Icons.play_circle_fill_rounded,color:lime,size:29)),const SizedBox(width:9),const Text('AniSteam',style:TextStyle(fontWeight:FontWeight.w900))]);}
class AnimeCard extends StatelessWidget {const AnimeCard({super.key,required this.anime,required this.onTap});final Anime anime;final VoidCallback onTap;@override Widget build(BuildContext context)=>InkWell(onTap:onTap,borderRadius:BorderRadius.circular(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Expanded(child:ClipRRect(borderRadius:BorderRadius.circular(17),child:Stack(fit:StackFit.expand,children:[if(anime.image.isNotEmpty)Image.network(anime.image,fit:BoxFit.cover,errorBuilder:(_,__,___)=>const PosterPlaceholder())else const PosterPlaceholder(),const Positioned(right:10,top:10,child:Icon(Icons.play_circle_fill_rounded,color:lime,size:27))]))),const SizedBox(height:9),Text(anime.title,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800,fontSize:13)),const SizedBox(height:4),Text(anime.source.toUpperCase(),style:const TextStyle(color:Color(0xFF9CA2B0),fontSize:9,letterSpacing:1.1))]));}
class PosterPlaceholder extends StatelessWidget {const PosterPlaceholder({super.key});@override Widget build(BuildContext context)=>Container(color:const Color(0xFF1B202B),child:const Center(child:Icon(Icons.movie_outlined,color:Color(0xFF6F7787),size:45)));}
class ErrorView extends StatelessWidget {const ErrorView({super.key,required this.message,required this.retry});final String message;final VoidCallback retry;@override Widget build(BuildContext context){final networkFailure=message.contains('Failed host lookup')||message.contains('SocketException')||message.contains('No address associated with hostname');final shown=networkFailure?'AniSteam could not reach its service. Check your internet connection and try again in a moment.':message;return Center(child:Padding(padding:const EdgeInsets.all(22),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.cloud_off_rounded,size:40,color:Color(0xFFFFB86B)),const SizedBox(height:12),const Text('Could not load data',style:TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(shown,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0))),const SizedBox(height:14),OutlinedButton.icon(onPressed:retry,icon:const Icon(Icons.refresh_rounded),label:const Text('Try again'))])));}}
class EmptyView extends StatelessWidget {const EmptyView({super.key,required this.title,required this.message});final String title,message;@override Widget build(BuildContext context)=>Center(child:Padding(padding:const EdgeInsets.all(24),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.video_library_outlined,size:43,color:Color(0xFF858B99)),const SizedBox(height:12),Text(title,textAlign:TextAlign.center,style:const TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(message,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0),height:1.4))])));}), '');
  static Future<dynamic> get(String path, [Map<String,String>? query]) async {
    final base = await baseUrl();
    final parsedBase = Uri.tryParse(base);
    if (parsedBase == null || parsedBase.host.isEmpty || !['https','http'].contains(parsedBase.scheme)) {
      throw Exception('Invalid API URL. Open Settings and enter the API base URL.');
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
  static Future<List<Anime>> streams(Anime episode) async {
    final data=await get('/watch',{'url':episode.url});final out=<Anime>[];
    void walk(dynamic v,[String label='Stream']){
      if(v is String && (v.startsWith('https://')||v.startsWith('http://'))){out.add(Anime(title:label,url:v,source:episode.source));}
      else if(v is Map){
        final m=Map<String,dynamic>.from(v);final u=(m['url']??m['src']??m['file']??m['link']??'').toString();
        if(u.startsWith('https://')||u.startsWith('http://'))out.add(Anime(title:(m['name']??m['label']??m['quality']??label).toString(),url:u,source:episode.source));
        else{for(final e in m.entries){walk(e.value,e.key);}}
      }else if(v is List){for(var i=0;i<v.length;i++){walk(v[i],label=='Stream'?'Stream ${i+1}':label);}}
    }
    walk(data);final seen=<String>{};return out.where((a)=>seen.add(a.url)).toList();
  }
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
 FutureBuilder<List<Anime>>(future:episodes,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(25),child:Center(child:CircularProgressIndicator()));if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>episodes=AniApi.episodes(widget.anime)));final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No episodes found',message:'The selected source returned no episode links.');return Column(children:list.asMap().entries.map((e)=>Card(child:ListTile(leading:CircleAvatar(backgroundColor:lime.withValues(alpha:.13),child:Text('${e.key+1}',style:const TextStyle(color:lime,fontWeight:FontWeight.w900))),title:Text(e.value.title,maxLines:2,overflow:TextOverflow.ellipsis),trailing:const Icon(Icons.play_arrow_rounded,color:lime),onTap:(){widget.onWatch(e.value);Navigator.push(context,MaterialPageRoute(builder:(_)=>WatchScreen(episode:e.value)));}))).toList());})
 ]));}
class WatchScreen extends StatefulWidget {const WatchScreen({super.key,required this.episode});final Anime episode;@override State<WatchScreen> createState()=>_WatchScreenState();}
class _WatchScreenState extends State<WatchScreen>{
 late Future<List<Anime>> future=AniApi.streams(widget.episode);
 bool direct(String u){final p=u.toLowerCase().split('?').first;return p.endsWith('.mp4')||p.endsWith('.m3u8')||p.endsWith('.webm')||p.endsWith('.mkv');}
 Future<void> open(Anime a) async {if(direct(a.url)){if(mounted)Navigator.push(context,MaterialPageRoute(builder:(_)=>MediaScreen(stream:a)));return;}final uri=Uri.tryParse(a.url);if(uri==null||!['http','https'].contains(uri.scheme))return;final ok=await launchUrl(uri,mode:LaunchMode.externalApplication);if(!ok&&mounted)ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Could not open player link.')));}
 @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Choose a stream')),body:FutureBuilder<List<Anime>>(future:future,builder:(context,snap){if(snap.connectionState==ConnectionState.waiting)return const Center(child:CircularProgressIndicator());if(snap.hasError)return ErrorView(message:snap.error.toString(),retry:()=>setState(()=>future=AniApi.streams(widget.episode)));final list=snap.data??[];if(list.isEmpty)return const EmptyView(title:'No playable links',message:'The API did not return a stream for this episode.');return ListView(padding:const EdgeInsets.all(16),children:[Text(widget.episode.title,style:const TextStyle(fontSize:22,fontWeight:FontWeight.w900)),const SizedBox(height:8),const Text('External player pages open outside AniSteam.',style:TextStyle(color:Color(0xFF9CA2B0))),const SizedBox(height:16),...list.asMap().entries.map((e)=>Card(child:ListTile(leading:const Icon(Icons.play_circle_fill_rounded,color:lime,size:30),title:Text(e.value.title.isEmpty?'Stream ${e.key+1}':e.value.title),subtitle:Text(direct(e.value.url)?'Direct media':'External player'),trailing:const Icon(Icons.open_in_new_rounded),onTap:()=>open(e.value))))]);}));}
class MediaScreen extends StatefulWidget {const MediaScreen({super.key,required this.stream});final Anime stream;@override State<MediaScreen> createState()=>_MediaScreenState();}
class _MediaScreenState extends State<MediaScreen>{VideoPlayerController? controller;Object? error;@override void initState(){super.initState();init();}Future<void> init()async{try{final c=VideoPlayerController.networkUrl(Uri.parse(widget.stream.url));controller=c;await c.initialize();if(mounted)setState((){});}catch(e){if(mounted)setState(()=>error=e);}}@override void dispose(){controller?.dispose();super.dispose();}@override Widget build(BuildContext context){final c=controller;return Scaffold(appBar:AppBar(title:Text(widget.stream.title,maxLines:1,overflow:TextOverflow.ellipsis)),body:Center(child:error!=null?Padding(padding:const EdgeInsets.all(24),child:Text('Playback unavailable: $error')):c==null||!c.value.isInitialized?const CircularProgressIndicator():Column(mainAxisAlignment:MainAxisAlignment.center,children:[AspectRatio(aspectRatio:c.value.aspectRatio,child:VideoPlayer(c)),const SizedBox(height:18),IconButton.filledTonal(iconSize:34,onPressed:()=>setState(()=>c.value.isPlaying?c.pause():c.play()),icon:Icon(c.value.isPlaying?Icons.pause_rounded:Icons.play_arrow_rounded)),VideoProgressIndicator(c,allowScrubbing:true)])));}}
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

  Future<void> checkConnection() async {
    if (checking) return;
    setState(() { checking = true; connectionStatus = 'Checking connection…'; });
    try {
      await AniApi.get('/catalogue', {'page':'1'});
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
      const Card(child:ListTile(leading:Icon(Icons.play_circle_fill_rounded,color:lime,size:32),title:Text('AniSteam',style:TextStyle(fontWeight:FontWeight.w900)),subtitle:Text('Anime discovery · episode lists · stream selection · local watch history'))),
      const Padding(padding:EdgeInsets.all(8),child:Text('AniSteam connects to its configured service. Playback availability depends on the provider and returned stream. It does not bypass DRM, login, paywalls, CAPTCHA, or anti-bot restrictions.',style:TextStyle(color:Color(0xFF9CA2B0),height:1.45))),
    ]),
  );
}
class Brand extends StatelessWidget {const Brand({super.key});@override Widget build(BuildContext context)=>Row(children:[Image.asset('assets/anisteam_logo.webp',width:30,height:30,fit:BoxFit.contain,errorBuilder:(_,__,___)=>const Icon(Icons.play_circle_fill_rounded,color:lime,size:29)),const SizedBox(width:9),const Text('AniSteam',style:TextStyle(fontWeight:FontWeight.w900))]);}
class AnimeCard extends StatelessWidget {const AnimeCard({super.key,required this.anime,required this.onTap});final Anime anime;final VoidCallback onTap;@override Widget build(BuildContext context)=>InkWell(onTap:onTap,borderRadius:BorderRadius.circular(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Expanded(child:ClipRRect(borderRadius:BorderRadius.circular(17),child:Stack(fit:StackFit.expand,children:[if(anime.image.isNotEmpty)Image.network(anime.image,fit:BoxFit.cover,errorBuilder:(_,__,___)=>const PosterPlaceholder())else const PosterPlaceholder(),const Positioned(right:10,top:10,child:Icon(Icons.play_circle_fill_rounded,color:lime,size:27))]))),const SizedBox(height:9),Text(anime.title,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800,fontSize:13)),const SizedBox(height:4),Text(anime.source.toUpperCase(),style:const TextStyle(color:Color(0xFF9CA2B0),fontSize:9,letterSpacing:1.1))]));}
class PosterPlaceholder extends StatelessWidget {const PosterPlaceholder({super.key});@override Widget build(BuildContext context)=>Container(color:const Color(0xFF1B202B),child:const Center(child:Icon(Icons.movie_outlined,color:Color(0xFF6F7787),size:45)));}
class ErrorView extends StatelessWidget {const ErrorView({super.key,required this.message,required this.retry});final String message;final VoidCallback retry;@override Widget build(BuildContext context){final networkFailure=message.contains('Failed host lookup')||message.contains('SocketException')||message.contains('No address associated with hostname');final shown=networkFailure?'AniSteam could not reach its service. Check your internet connection and try again in a moment.':message;return Center(child:Padding(padding:const EdgeInsets.all(22),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.cloud_off_rounded,size:40,color:Color(0xFFFFB86B)),const SizedBox(height:12),const Text('Could not load data',style:TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(shown,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0))),const SizedBox(height:14),OutlinedButton.icon(onPressed:retry,icon:const Icon(Icons.refresh_rounded),label:const Text('Try again'))])));}}
class EmptyView extends StatelessWidget {const EmptyView({super.key,required this.title,required this.message});final String title,message;@override Widget build(BuildContext context)=>Center(child:Padding(padding:const EdgeInsets.all(24),child:Column(mainAxisSize:MainAxisSize.min,children:[const Icon(Icons.video_library_outlined,size:43,color:Color(0xFF858B99)),const SizedBox(height:12),Text(title,textAlign:TextAlign.center,style:const TextStyle(fontSize:18,fontWeight:FontWeight.w900)),const SizedBox(height:7),Text(message,textAlign:TextAlign.center,style:const TextStyle(color:Color(0xFF9CA2B0),height:1.4))])));}