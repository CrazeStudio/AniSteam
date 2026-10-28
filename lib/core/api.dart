import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const defaultApiBase = 'https://anisteam-api-sooty.vercel.app';

class Anime {
  const Anime({
    required this.title,
    required this.url,
    this.image = '',
    this.description = '',
    this.source = 'ToonStream',
    this.seriesTitle = '',
  });
  final String title, url, image, description, source, seriesTitle;

  factory Anime.fromMap(Map<String, dynamic> m, {String fallbackSource = 'ToonStream'}) {
    String read(List<String> keys) {
      for (final k in keys) {
        final v = m[k];
        if (v is String && v.trim().isNotEmpty) return v.trim();
        if (v is num) return v.toString();
      }
      return '';
    }

    final url = read(['url', 'link', 'href', 'animeUrl', 'episodeUrl', 'watchUrl', 'targetUrl', 'slug']);
    final inferred = url.toLowerCase().contains('hianime') ? 'HiAnime' : fallbackSource;
    final title = read(['title', 'name', 'animeTitle', 'englishTitle', 'episodeTitle']);
    return Anime(
      title: title.isEmpty ? 'Untitled' : title,
      url: url,
      image: read(['image', 'poster', 'posterUrl', 'thumbnail', 'cover', 'img', 'imageUrl', 'poster_url']),
      description: read(['description', 'synopsis', 'overview', 'summary']),
      source: inferred,
      seriesTitle: read(['seriesTitle', 'series_title']),
    );
  }

  Map<String, dynamic> toJson() => {
        'title': title,
        'url': url,
        'image': image,
        'description': description,
        'source': source,
        'seriesTitle': seriesTitle,
      };

  factory Anime.fromJson(Map<String, dynamic> m) => Anime(
        title: (m['title'] ?? 'Untitled').toString(),
        url: (m['url'] ?? '').toString(),
        image: (m['image'] ?? '').toString(),
        description: (m['description'] ?? '').toString(),
        source: (m['source'] ?? 'ToonStream').toString(),
        seriesTitle: (m['seriesTitle'] ?? m['series_title'] ?? '').toString(),
      );
}

class AniApi {
  // Always use the configured AniSteam-2 service. Ignore stale values left by older builds.
  // Keep the service address private and out of Settings.
  static Future<String> baseUrl() async => defaultApiBase;

  static Future<dynamic> get(String path, [Map<String, String>? query]) async {
    final base = await baseUrl();
    final parsedBase = Uri.tryParse(base);
    if (parsedBase == null || parsedBase.host.isEmpty || !['https', 'http'].contains(parsedBase.scheme)) {
      throw Exception('AniSteam service configuration is invalid.');
    }
    final uri = Uri.parse('$base$path').replace(queryParameters: query);
    final response = await http
        .get(uri, headers: const {'Accept': 'application/json'})
        .timeout(const Duration(seconds: 25));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('HTTP ${response.statusCode} from AniSteam API');
    }
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } on FormatException {
      throw Exception('API returned invalid JSON');
    }
  }

  static List<Map<String, dynamic>> maps(dynamic value, [int depth = 0]) {
    if (depth > 6) return [];
    if (value is List) {
      return value.whereType<Map>().map((x) => Map<String, dynamic>.from(x)).toList();
    }
    if (value is Map) {
      final m = Map<String, dynamic>.from(value);
      for (final key in ['results', 'data', 'items', 'anime', 'catalogue', 'catalog', 'episodes', 'streams', 'direct_streams', 'player_streams']) {
        if (m.containsKey(key)) {
          final found = maps(m[key], depth + 1);
          if (found.isNotEmpty) return found;
        }
      }
      for (final v in m.values) {
        final found = maps(v, depth + 1);
        if (found.isNotEmpty) return found;
      }
    }
    return [];
  }

  static List<Anime> parse(dynamic data, {String source = 'ToonStream'}) {
    final seen = <String>{};
    return maps(data)
        .map((m) => Anime.fromMap(m, fallbackSource: source))
        .where((a) => a.url.startsWith('http') && seen.add(a.url))
        .toList();
  }

  static const String _catalogueCacheKey = 'anisteam_catalogue_cache_v1';

  /// Reads the last saved catalogue instantly so Home can paint before the network answers.
  static Future<List<Anime>> cachedCatalogue() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_catalogueCacheKey);
      if (raw == null) return [];
      return (jsonDecode(raw) as List)
          .whereType<Map>()
          .map((item) => Anime.fromJson(Map<String, dynamic>.from(item)))
          .where((item) => item.url.startsWith('http'))
          .toList();
    } catch (_) {
      return [];
    }
  }

  static Future<List<Anime>> catalogue() async {
    final cached = await cachedCatalogue();
    try {
      final fresh = parse(await get('/catalogue', {'page': '1'}));
      if (fresh.isNotEmpty) {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(
          _catalogueCacheKey,
          jsonEncode(fresh.take(100).map((anime) => anime.toJson()).toList()),
        );
      }
      return fresh.isNotEmpty ? fresh : cached;
    } catch (_) {
      if (cached.isNotEmpty) return cached;
      rethrow;
    }
  }

  static Future<List<Anime>> search(String query, String mode) async {
    final collected = <Anime>[];
    Future<void> fetch(String source, String path, Map<String, String> params) async {
      try {
        collected.addAll(parse(await get(path, params), source: source));
      } catch (_) {}
    }

    if (mode == 'COMBINED' || mode == 'TOONSTREAM') {
      await fetch('ToonStream', '/toonstream/search', {'q': query});
      if (!collected.any((a) => a.source == 'ToonStream')) {
        await fetch('ToonStream', '/search', {'q': query});
      }
    }
    if (mode == 'COMBINED' || mode == 'HIANIME') {
      await fetch('HiAnime', '/hianime/search', {'q': query});
    }
    final unique = <String, Anime>{};
    for (final a in collected) {
      final key = a.title.toLowerCase().trim();
      if (key.isEmpty) continue;
      final old = unique[key];
      if (old == null) {
        unique[key] = a;
      } else {
        unique[key] = Anime(
          title: old.title,
          url: old.url,
          image: old.image.isEmpty ? a.image : old.image,
          description: old.description.isEmpty ? a.description : old.description,
          source: old.source,
          seriesTitle: old.seriesTitle,
        );
      }
    }
    if (unique.isEmpty) {
      throw Exception('No results found for "$query" from the selected sources.');
    }
    return unique.values.toList();
  }

  static Future<dynamic> details(Anime anime) => get('/details', {'url': anime.url});

  static Future<List<Anime>> episodes(Anime anime) async {
    final source = anime.source.toLowerCase().contains('hianime') ? 'hianime' : 'toonstream';
    final parsed = parse(await get('/$source/episodes', {'url': anime.url}), source: anime.source);
    if (parsed.isNotEmpty) {
      return parsed
          .asMap()
          .entries
          .map((e) => Anime(
                title: e.value.title == 'Untitled' ? 'Episode ${e.key + 1}' : e.value.title,
                url: e.value.url,
                image: e.value.image,
                source: anime.source,
              ))
          .toList();
    }

    // Movies have no episode list. If the source identifies this page as a
    // movie, expose the movie page itself as the single playable item.
    final uri = Uri.tryParse(anime.url);
    final pathLooksLikeMovie = uri != null &&
        (RegExp(r'/(movie|movies)/', caseSensitive: false).hasMatch(uri.path) ||
            uri.path.toLowerCase().contains('type-movies'));
    try {
      final detail = await details(anime);
      final kind = (detail is Map ? (detail['kind'] ?? detail['type'] ?? '') : '').toString().toLowerCase();
      final isMovie = kind == 'movie' || kind == 'film' || pathLooksLikeMovie;
      if (isMovie) {
        return [Anime(title: 'Movie · ${anime.title}', url: anime.url, image: anime.image, source: anime.source)];
      }
    } catch (_) {
      if (pathLooksLikeMovie) {
        return [Anime(title: 'Movie · ${anime.title}', url: anime.url, image: anime.image, source: anime.source)];
      }
    }
    return [];
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
        title: title.trim().isEmpty
            ? (direct ? 'Direct stream' : 'Web player ${out.length + 1}')
            : title.trim(),
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
              label: label == 'Server' ? 'Server ${i + 1}' : label,
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
          if (isWebUrl(candidate)) {
            url = candidate;
            break;
          }
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
      if (node is String &&
          isWebUrl(node.trim()) &&
          (contextKey.contains('stream') ||
              contextKey.contains('player') ||
              contextKey.contains('embed') ||
              contextKey.contains('server') ||
              contextKey.contains('direct') ||
              contextKey == 'results')) {
        add(node.trim(), label, typeHint, '');
      }
    }

    dynamic payload = raw;
    if (payload is Map) {
      payload = payload['data'] ?? payload['result'] ?? payload;
    }
    walk(payload);
    // Keep the API-provided server named exactly `short`; do not add a duplicate fallback.
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

  String get playbackKind =>
      isDirect ? 'Direct video' : (isShortLink ? 'Short-link web player' : 'Embedded web player');
}