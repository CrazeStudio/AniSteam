import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../core/api.dart';
import '../ui/motion.dart';
import '../ui/tokens.dart';
import '../ui/widgets.dart';

class WatchScreen extends StatefulWidget {
  const WatchScreen({
    super.key,
    required this.episode,
    this.playlist = const [],
    this.startIndex = 0,
    this.animeTitle = '',
    this.onEpisodeChanged,
  });
  final Anime episode;
  final List<Anime> playlist;
  final int startIndex;
  final String animeTitle;
  final ValueChanged<Anime>? onEpisodeChanged;
  @override
  State<WatchScreen> createState() => _WatchScreenState();
}

class _WatchScreenState extends State<WatchScreen> {
  Anime? currentEpisode;
  int currentIndex = 0;
  List<StreamChoice> streams = [];
  final TextEditingController serverFilterController = TextEditingController();
  String serverFilter = '';
  bool showAllServers = false;
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
  final Set<String> _attemptedServerUrls = <String>{};
  final Set<String> _failedServerUrls = <String>{};
  Duration _lastSavedPosition = Duration.zero;

  // Double-tap seek feedback.
  int seekFlash = 0; // -1 back, 0 none, 1 forward
  double _doubleTapFraction = .5;
  Timer? _seekFlashTimer;

  String _lastServerKey(Anime episode) => 'anisteam_last_server:${episode.url}';
  String _progressKey(Anime episode) => 'anisteam_progress:${episode.url}';

  Future<void> _saveLastServer(Anime episode, StreamChoice choice) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_lastServerKey(episode), choice.url);
    } catch (_) {}
  }

  Future<void> _saveProgress(Anime episode, Duration value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (value <= Duration.zero) {
        await prefs.remove(_progressKey(episode));
      } else {
        await prefs.setInt(_progressKey(episode), value.inMilliseconds);
      }
    } catch (_) {}
  }

  String get _animeTitle =>
      widget.animeTitle.isNotEmpty ? widget.animeTitle : (currentEpisode?.title ?? 'AniSteam');
  bool get _toonStreamOnly =>
      (currentEpisode?.source ?? widget.episode.source).toLowerCase().contains('toonstream');

  bool _isPreferredToonStreamServer(StreamChoice choice) {
    final title = choice.title.toLowerCase();
    final host = Uri.tryParse(choice.url)?.host.toLowerCase() ?? '';
    if (choice.isShortLink ||
        title.contains('short') ||
        title.contains('abyss') ||
        host == 'abyssplayer.com' ||
        host.endsWith('.abyssplayer.com')) {
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

  bool _isTrustedShortNavigation(Uri target, Uri source) {
    final targetHost = target.host.toLowerCase().replaceFirst(RegExp(r'^www\.'), '');
    final sourceHost = source.host.toLowerCase().replaceFirst(RegExp(r'^www\.'), '');
    if (targetHost.isEmpty || sourceHost.isEmpty) return false;

    // Allow the same provider domain and its subdomains.
    if (targetHost == sourceHost ||
        targetHost.endsWith('.$sourceHost') ||
        sourceHost.endsWith('.$targetHost')) {
      return true;
    }

    // Permit common video/CDN hosts needed by embedded players, rather than
    // allowing an arbitrary redirect destination to take over the iframe.
    const trustedVideoHostTerms = <String>{
      'abyssplayer', 'abyss.to', 'ravok', 'jwplayer', 'googlevideo.com',
      'vidcloud', 'megacloud', 'streamtape', 'rabbitstream', 'vidstreaming',
      'gogo', 'mp4upload', 'filemoon', 'streamwish', 'voe', 'dood',
      'vidhide', 'vizcloud', 'mycloud', 'mcloud', 'vidsrc', 'embed',
      'player', 'cdn', 'storage', 'video', 'stream',
    };
    if (trustedVideoHostTerms.any((term) => targetHost.contains(term))) return true;

    final url = target.toString().toLowerCase();
    return RegExp(r'\.(m3u8|mp4|webm|mkv|ts)(?:$|[?#])').hasMatch(url);
  }

  bool get _hasPlaylist => widget.playlist.isNotEmpty;
  bool get _hasPrevious => _hasPlaylist && currentIndex > 0;
  bool get _hasNext => _hasPlaylist && currentIndex < widget.playlist.length - 1;

  @override
  void initState() {
    super.initState();
    currentEpisode = widget.episode;
    currentIndex = widget.startIndex;
    _loadPlaybackSettingsAndPlay();
  }

  Future<void> _loadPlaybackSettingsAndPlay() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      showAllServers = prefs.getBool('show_all_servers') ?? false;
      keepScreenAwake = prefs.getBool('keep_screen_awake') ?? true;
    } catch (_) {}
    if (!mounted) return;
    if (keepScreenAwake) {
      try {
        await WakelockPlus.enable();
      } catch (_) {}
    }
    await _loadEpisode(widget.episode, index: widget.startIndex);
  }

  @override
  void dispose() {
    hideTimer?.cancel();
    _seekFlashTimer?.cancel();
    serverFilterController.dispose();
    try {
      unawaited(WakelockPlus.disable());
    } catch (_) {}
    final old = controller;
    final episode = currentEpisode;
    if (old != null &&
        episode != null &&
        old.value.isInitialized &&
        old.value.position > Duration.zero &&
        old.value.position < old.value.duration * .92) {
      unawaited(_saveProgress(episode, old.value.position));
    }
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
    _attemptedServerUrls.clear();
    _failedServerUrls.clear();
    _lastSavedPosition = Duration.zero;
    widget.onEpisodeChanged?.call(Anime(
      title: episode.title,
      url: episode.url,
      image: episode.image,
      description: episode.description,
      source: episode.source,
      seriesTitle: widget.animeTitle.isNotEmpty ? widget.animeTitle : episode.seriesTitle,
    ));
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
      StreamChoice? remembered;
      try {
        final prefs = await SharedPreferences.getInstance();
        final lastUrl = prefs.getString(_lastServerKey(episode));
        for (final option in pool) {
          if (option.url == lastUrl) {
            remembered = option;
            break;
          }
        }
      } catch (_) {}
      final preferred = remembered ?? pool.firstWhere((item) => item.isDirect, orElse: () => pool.first);
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

  Future<void> _selectStream(StreamChoice choice, {bool userInitiated = false}) async {
    if (userInitiated) {
      _attemptedServerUrls.remove(choice.url);
      _failedServerUrls.remove(choice.url);
    }
    if (!_attemptedServerUrls.add(choice.url) && !userInitiated) return;
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
      final episode = currentEpisode ?? widget.episode;
      try {
        final prefs = await SharedPreferences.getInstance();
        final savedMs = prefs.getInt(_progressKey(episode)) ?? 0;
        final savedPosition = Duration(milliseconds: savedMs);
        if (savedMs > 0 && savedPosition < next.value.duration * .92) {
          await next.seekTo(savedPosition);
          _lastSavedPosition = savedPosition;
        } else if (savedMs > 0) {
          await prefs.remove(_progressKey(episode));
        }
      } catch (_) {}
      next.addListener(_onVideoTick);
      await next.setPlaybackSpeed(speed);
      await next.play();
      await _saveLastServer(episode, choice);
      // Fix: the screen can be closed while the awaits above are in flight.
      if (!mounted || controller != next) return;
      setState(() => loadingPlayer = false);
      _scheduleHide();
    } catch (_) {
      _failedServerUrls.add(choice.url);
      final failedController = controller;
      controller = null;
      if (failedController != null) {
        try {
          failedController.removeListener(_onVideoTick);
          await failedController.dispose();
        } catch (_) {}
      }
      if (mounted) {
        setState(() {
          loadingPlayer = false;
          errorMessage = 'This server failed. Looking for another available server…';
        });
        await _tryNextServer();
      }
    }
  }

  Future<void> _tryNextServer() async {
    if (!mounted) return;
    List<StreamChoice> pool = streams;
    if (_toonStreamOnly && !showAllServers) {
      final preferred = streams.where(_isPreferredToonStreamServer).toList();
      if (preferred.isNotEmpty) pool = preferred;
    }
    StreamChoice? next;
    for (final choice in pool) {
      if (!_attemptedServerUrls.contains(choice.url) && !_failedServerUrls.contains(choice.url)) {
        next = choice;
        break;
      }
    }
    final nextChoice = next;
    if (nextChoice == null) {
      setState(() {
        loadingPlayer = false;
        webLoading = false;
        errorMessage = 'Every available server failed. Refresh the server list or try again later.';
      });
      return;
    }
    setState(() {
      active = nextChoice;
      errorMessage = 'Trying another server: ${nextChoice.title}';
      loadingPlayer = nextChoice.isDirect;
      webLoading = !nextChoice.isDirect;
    });
    await _selectStream(nextChoice);
  }

  Future<void> _handleCurrentServerFailure() async {
    final failed = active;
    if (failed == null || !_failedServerUrls.add(failed.url)) return;
    final old = controller;
    controller = null;
    if (old != null) {
      try {
        old.removeListener(_onVideoTick);
        await old.dispose();
      } catch (_) {}
    }
    await _tryNextServer();
  }

  void _onVideoTick() {
    final c = controller;
    if (!mounted || c == null || !c.value.isInitialized) return;
    final value = c.value;
    if (value.hasError) {
      unawaited(_handleCurrentServerFailure());
      return;
    }
    final ended = value.position >= value.duration && value.duration > Duration.zero;
    setState(() {
      position = value.position;
      duration = value.duration;
    });
    final episode = currentEpisode ?? widget.episode;
    if (!ended && value.position - _lastSavedPosition >= const Duration(seconds: 15)) {
      _lastSavedPosition = value.position;
      unawaited(_saveProgress(episode, value.position));
    }
    if (ended && !_episodeEndedHandled) {
      _episodeEndedHandled = true;
      unawaited(_saveProgress(episode, Duration.zero));
      if (_hasNext) _playEpisode(currentIndex + 1);
    }
  }

  Future<void> _playEpisode(int index) async {
    if (!_hasPlaylist || index < 0 || index >= widget.playlist.length) return;
    await _loadEpisode(widget.playlist[index], index: index);
  }

  Future<void> _reloadPlayback() async {
    final choice = active;
    if (choice == null) {
      await _refreshServers();
      return;
    }
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
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not open the external player.')));
      }
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
    setState(() {
      fullscreen = true;
      showControls = true;
    });
    await SystemChrome.setPreferredOrientations(const [
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _scheduleHide();
  }

  Future<void> _leaveFullscreen() async {
    if (!mounted || !fullscreen) return;
    setState(() {
      fullscreen = false;
      showControls = true;
    });
    await SystemChrome.setPreferredOrientations([]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    _scheduleHide();
  }

  /// Back press while an embedded page is fullscreen: exit that first.
  Future<void> _exitWebFullscreen() async {
    try {
      await webController?.evaluateJavascript(
        source: 'if(document.exitFullscreen){document.exitFullscreen();}'
            'else if(document.webkitExitFullscreen){document.webkitExitFullscreen();}',
      );
    } catch (_) {}
    if (!mounted) return;
    setState(() => webFullscreen = false);
    await SystemChrome.setPreferredOrientations([]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
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
      if (mounted) setState(() => showControls = true);
    } else {
      await c.play();
      if (mounted) setState(() => showControls = true);
      _scheduleHide();
    }
  }

  void _flashSeek(int direction) {
    _seekFlashTimer?.cancel();
    setState(() => seekFlash = direction);
    _seekFlashTimer = Timer(const Duration(milliseconds: 550), () {
      if (mounted) setState(() => seekFlash = 0);
    });
  }

  void _onDoubleTapSeek() {
    final c = controller;
    if (locked || c == null || !c.value.isInitialized) return;
    final forward = _doubleTapFraction >= .5;
    final target = c.value.position + Duration(seconds: forward ? 10 : -10);
    final end = c.value.duration;
    final clamped = target < Duration.zero ? Duration.zero : (target > end ? end : target);
    unawaited(c.seekTo(clamped));
    tapHaptic();
    _flashSeek(forward ? 1 : -1);
  }

  Future<void> _showSpeedPicker() async {
    final selected = await showModalBottomSheet<double>(
      context: context,
      backgroundColor: surface,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Playback speed', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
                  .map((value) => ChoiceChip(
                        label: Text(value == 1.0 ? 'Normal' : '$value×'),
                        selected: speed == value,
                        onSelected: (_) => Navigator.pop(context, value),
                      ))
                  .toList(),
            ),
          ]),
        ),
      ),
    );
    if (selected == null || !mounted) return;
    setState(() => speed = selected);
    try {
      await controller?.setPlaybackSpeed(selected);
    } catch (_) {}
  }

  Future<void> _showServerPicker() async {
    final visibleServers = _filteredStreams;
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
            const Text('Direct streams use the native video player. Short links and embed pages open inside AniSteam’s WebView.',
                style: TextStyle(color: muted)),
            const SizedBox(height: 12),
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: visibleServers.length,
                separatorBuilder: (_, __) => const Divider(height: 1, color: Color(0xFF292D38)),
                itemBuilder: (context, index) {
                  final item = visibleServers[index];
                  return FadeSlideIn(
                    delay: Motion.stagger(index, step: 30),
                    duration: const Duration(milliseconds: 380),
                    offset: const Offset(0, 12),
                    child: ListTile(
                      leading: Icon(item.isDirect ? Icons.play_circle_fill_rounded : Icons.language_rounded,
                          color: item.url == active?.url ? lime : muted, size: 29),
                      title: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text('${item.playbackKind} · ${item.language}'),
                      trailing: item.url == active?.url
                          ? const Icon(Icons.check_circle_rounded, color: lime)
                          : const Icon(Icons.chevron_right_rounded),
                      onTap: () => Navigator.pop(context, item),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => Navigator.pop(context, const StreamChoice(title: '__refresh__', url: 'https://invalid.local')),
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Refresh servers'),
              ),
            ),
          ]),
        ),
      ),
    );
    if (selected == null || !mounted) return;
    if (selected.title == '__refresh__') {
      await _refreshServers();
    } else {
      await _selectStream(selected, userInitiated: true);
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
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: widget.playlist.length,
                separatorBuilder: (_, __) => const Divider(height: 1, color: Color(0xFF292D38)),
                itemBuilder: (context, index) {
                  final episode = widget.playlist[index];
                  return ListTile(
                    leading: CircleAvatar(
                      backgroundColor: index == currentIndex ? lime.withValues(alpha: .15) : const Color(0xFF252936),
                      child: Text((index + 1).toString(),
                          style: TextStyle(color: index == currentIndex ? lime : Colors.white)),
                    ),
                    title: Text(episode.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                    subtitle: Text(episode.source),
                    trailing: index == currentIndex ? const Icon(Icons.equalizer_rounded, color: lime) : null,
                    onTap: () => Navigator.pop(context, index),
                  );
                },
              ),
            ),
          ]),
        ),
      ),
    );
    if (selected != null && mounted && selected != currentIndex) await _playEpisode(selected);
  }

  Widget _playerSurface() {
    final choice = active;
    if (choice == null) {
      return const ColoredBox(color: Colors.black, child: Center(child: LogoLoader(size: 44)));
    }
    if (choice.isDirect) {
      final c = controller;
      if (c == null || !c.value.isInitialized) {
        return const ColoredBox(color: Colors.black);
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

    final host = Uri.tryParse(choice.url)?.host.toLowerCase() ?? '';
    final useShortIframe = _toonStreamOnly &&
        (choice.title.trim().toLowerCase() == 'short' ||
            choice.isShortLink ||
            host == 'abyssplayer.com' ||
            host.endsWith('.abyssplayer.com'));
    final escapedSrc = choice.url
        .replaceAll('&', '&amp;')
        .replaceAll('"', '&quot;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;');
    final shortIframeHtml = '<!doctype html><html><head>'
        '<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">'
        '<meta charset="utf-8">'
        '<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}'
        'iframe{border:0;width:100%;height:100%;display:block;background:#000}</style>'
        '</head><body><iframe src="$escapedSrc" title="Short video player" '
        'allow="autoplay; fullscreen; picture-in-picture; encrypted-media" '
        'allowfullscreen frameborder="0" scrolling="no"></iframe></body></html>';

    return InAppWebView(
      key: _webViewKey,
      // Only the ToonStream short provider uses the supplied iframe HTML.
      // Other providers continue loading their own URLs directly.
      initialData: useShortIframe
          ? InAppWebViewInitialData(
              data: shortIframeHtml,
              baseUrl: WebUri(Uri.tryParse(choice.url)?.origin ?? 'https://abyssplayer.com'),
            )
          : null,
      initialUrlRequest: useShortIframe ? null : URLRequest(url: WebUri(choice.url)),
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
        userAgent:
            'Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
      ),
      onWebViewCreated: (web) => webController = web,
      onLoadStart: (web, url) {
        if (mounted) setState(() => webLoading = true);
      },
      onLoadStop: (web, url) {
        if (mounted) setState(() => webLoading = false);
        if (!useShortIframe && active?.url == choice.url) {
          unawaited(_saveLastServer(currentEpisode ?? widget.episode, choice));
        }
      },
      shouldOverrideUrlLoading: (web, action) async {
        final uri = action.request.url;
        if (uri == null) return NavigationActionPolicy.CANCEL;
        final scheme = uri.scheme.toLowerCase();

        // Never allow a Short/Abyss click to launch another Android app,
        // browser intent, market page, or custom URL scheme.
        if (scheme != 'https' && scheme != 'http') {
          return NavigationActionPolicy.CANCEL;
        }

        if (useShortIframe) {
          // Never let the top-level WebView leave AniSteam's local wrapper page.
          if (action.isForMainFrame == true) {
            return NavigationActionPolicy.CANCEL;
          }

          // Keep the provider's iframe on its own domain or known video/CDN
          // destinations. No separate ad-blocker toggle is required.
          final sourceUri = Uri.tryParse(choice.url);
          if (sourceUri == null || !_isTrustedShortNavigation(uri, sourceUri)) {
            return NavigationActionPolicy.CANCEL;
          }
        }
        return NavigationActionPolicy.ALLOW;
      },
      onCreateWindow: (web, action) async {
        // Pop-up windows are a common source of redirect loops on short-link
        // pages. Do not create a second WebView/window; keep navigation in
        // the existing player only.
        return false;
      },
      onReceivedError: (web, request, error) {
        if (request.isForMainFrame == true && mounted) {
          setState(() {
            webLoading = false;
            errorMessage = 'This embedded server failed. Trying another available server…';
          });
          unawaited(_handleCurrentServerFailure());
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

  Widget _seekFlashOverlay() => Positioned.fill(
        child: IgnorePointer(
          child: Row(children: [
            Expanded(child: _SeekBadge(visible: seekFlash == -1, icon: Icons.fast_rewind_rounded)),
            Expanded(child: _SeekBadge(visible: seekFlash == 1, icon: Icons.fast_forward_rounded)),
          ]),
        ),
      );

  Widget _directControls({required bool isFullscreen}) {
    final c = controller;
    final value = c?.value;
    final playing = value?.isPlaying == true;
    final maxMs = duration.inMilliseconds > 0 ? duration.inMilliseconds.toDouble() : 1.0;
    final currentMs = position.inMilliseconds.clamp(0, maxMs.toInt()).toDouble();
    return Positioned.fill(
      child: IgnorePointer(
        ignoring: !showControls || locked,
        child: AnimatedOpacity(
          opacity: showControls && !locked ? 1 : 0,
          duration: Motion.fast,
          child: DecoratedBox(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Color(0xB8000000), Colors.transparent, Colors.transparent, Color(0xDD000000)],
                stops: [0, .25, .62, 1],
              ),
            ),
            child: Stack(children: [
              Positioned(
                left: 6,
                right: 6,
                top: 0,
                child: AnimatedSlide(
                  offset: showControls ? Offset.zero : const Offset(0, -.5),
                  duration: Motion.base,
                  curve: Motion.emphasized,
                  child: Row(children: [
                    IconButton(
                      onPressed: () {
                        if (isFullscreen) {
                          _leaveFullscreen();
                        } else {
                          Navigator.maybePop(context);
                        }
                      },
                      icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
                    ),
                    Expanded(
                      child: Text('$_animeTitle · ${currentEpisode?.title ?? ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
                    ),
                    IconButton(onPressed: _showServerPicker, icon: const Icon(Icons.dns_rounded, color: Colors.white)),
                    IconButton(onPressed: _openExternal, icon: const Icon(Icons.open_in_new_rounded, color: Colors.white)),
                  ]),
                ),
              ),
              Center(
                child: AnimatedScale(
                  scale: showControls ? 1 : .85,
                  duration: Motion.base,
                  curve: Curves.easeOutBack,
                  child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    IconButton(
                      onPressed: _hasPrevious ? () => _playEpisode(currentIndex - 1) : null,
                      iconSize: 36,
                      icon: const Icon(Icons.skip_previous_rounded, color: Colors.white),
                    ),
                    const SizedBox(width: 18),
                    IconButton.filled(
                      onPressed: _togglePlayPause,
                      iconSize: 36,
                      style: IconButton.styleFrom(backgroundColor: Colors.white.withValues(alpha: .18)),
                      icon: AnimatedSwitcher(
                        duration: Motion.fast,
                        transitionBuilder: (child, a) =>
                            ScaleTransition(scale: a, child: FadeTransition(opacity: a, child: child)),
                        child: Icon(
                          playing ? Icons.pause_rounded : Icons.play_arrow_rounded,
                          key: ValueKey(playing),
                          color: Colors.white,
                        ),
                      ),
                    ),
                    const SizedBox(width: 18),
                    IconButton(
                      onPressed: _hasNext ? () => _playEpisode(currentIndex + 1) : null,
                      iconSize: 36,
                      icon: const Icon(Icons.skip_next_rounded, color: Colors.white),
                    ),
                  ]),
                ),
              ),
              Positioned(
                left: 14,
                right: 14,
                bottom: 8,
                child: AnimatedSlide(
                  offset: showControls ? Offset.zero : const Offset(0, .5),
                  duration: Motion.base,
                  curve: Motion.emphasized,
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    if (duration > Duration.zero)
                      Slider(
                        value: currentMs,
                        min: 0,
                        max: maxMs,
                        onChanged: (ms) => setState(() => position = Duration(milliseconds: ms.round())),
                        onChangeEnd: (ms) => controller?.seekTo(Duration(milliseconds: ms.round())),
                        activeColor: lime,
                        inactiveColor: Colors.white24,
                      ),
                    Row(children: [
                      Text('${_durationText(position)} / ${_durationText(duration)}',
                          style: const TextStyle(color: Colors.white, fontSize: 11)),
                      const Spacer(),
                      Text('$speed×', style: const TextStyle(color: Colors.white, fontSize: 12)),
                      IconButton(onPressed: _showSpeedPicker, icon: const Icon(Icons.speed_rounded, color: Colors.white)),
                      IconButton(                        onPressed: () => setState(() {
                          locked = true;
                          showControls = false;
                        }),
                        icon: const Icon(Icons.lock_outline_rounded, color: Colors.white),
                      ),
                      IconButton(
                        onPressed: _toggleFullscreen,
                        icon: Icon(isFullscreen ? Icons.fullscreen_exit_rounded : Icons.fullscreen_rounded, color: Colors.white),
                      ),
                    ]),
                  ]),
                ),
              ),
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
    return hours > 0 ? '$hours:$mm:$ss' : '$mm:$ss';
  }

  Widget _playerArea({required bool isFullscreen}) {
    final player = _playerSurface();
    final direct = active?.isDirect == true;
    final stack = Stack(
      fit: StackFit.expand,
      children: [
        player,
        if (direct) _seekFlashOverlay(),
        Positioned.fill(
          child: IgnorePointer(
            child: AnimatedSwitcher(
              duration: Motion.base,
              child: (loadingPlayer || webLoading)
                  ? const ColoredBox(
                      key: ValueKey('player-loading'),
                      color: Color(0x66000000),
                      child: Center(child: LogoLoader(size: 44)),
                    )
                  : const SizedBox.shrink(key: ValueKey('player-ready')),
            ),
          ),
        ),
        if (direct) _directControls(isFullscreen: isFullscreen),
        if (!direct && isFullscreen && showControls && !locked)
          Positioned(
            left: 6,
            right: 6,
            top: 0,
            child: DecoratedBox(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xCC000000), Colors.transparent],
                ),
              ),
              child: Row(children: [
                IconButton(onPressed: _leaveFullscreen, icon: const Icon(Icons.arrow_back_rounded, color: Colors.white)),
                Expanded(
                  child: Text(currentEpisode?.title ?? 'AniSteam Player',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
                ),
                IconButton(onPressed: _showServerPicker, icon: const Icon(Icons.dns_rounded, color: Colors.white)),
                IconButton(onPressed: _openExternal, icon: const Icon(Icons.open_in_new_rounded, color: Colors.white)),
                IconButton(onPressed: _toggleFullscreen, icon: const Icon(Icons.fullscreen_exit_rounded, color: Colors.white)),
              ]),
            ),
          ),
        if (locked)
          Positioned(
            right: 14,
            bottom: 14,
            child: FilledButton.tonalIcon(
              onPressed: () => setState(() {
                locked = false;
                showControls = true;
                _scheduleHide();
              }),
              icon: const Icon(Icons.lock_open_rounded),
              label: const Text('Unlock controls'),
            ),
          ),
      ],
    );
    if (!direct) return stack;
    // One gesture layer for tap-to-toggle and double-tap seek. Translucent so
    // the control buttons on top keep their own taps (also fixes: tapping the
    // video while controls were visible used to do nothing).
    return LayoutBuilder(
      builder: (context, box) => GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: _toggleControls,
        onDoubleTapDown: (d) =>
            _doubleTapFraction = box.maxWidth <= 0 ? .5 : d.localPosition.dx / box.maxWidth,
        onDoubleTap: _onDoubleTapSeek,
        child: stack,
      ),
    );
  }

  Widget _fullscreenScaffold() => Scaffold(
        backgroundColor: Colors.black,
        body: SizedBox.expand(child: _playerArea(isFullscreen: true)),
      );

  Widget _normalScaffold() => Scaffold(
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
            ? const Center(child: LogoLoader(size: 58))
            : errorMessage != null && active == null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: FadeSlideIn(
                        child: Column(mainAxisSize: MainAxisSize.min, children: [
                          const Floating(child: Icon(Icons.cloud_off_rounded, color: warm, size: 44)),
                          const SizedBox(height: 12),
                          const Text('Playback unavailable', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
                          const SizedBox(height: 8),
                          Text(errorMessage!, textAlign: TextAlign.center, style: const TextStyle(color: muted)),
                          const SizedBox(height: 16),
                          FilledButton.icon(
                            onPressed: _refreshServers,
                            icon: const Icon(Icons.refresh_rounded),
                            label: const Text('Retry servers'),
                          ),
                        ]),
                      ),
                    ),
                  )
                : ListView(
                    physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
                    children: [
                      AspectRatio(aspectRatio: 16 / 9, child: _playerArea(isFullscreen: false)),
                      AnimatedSize(
                        duration: Motion.base,
                        curve: Motion.emphasized,
                        alignment: Alignment.topCenter,
                        child: errorMessage != null
                            ? Padding(
                                padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                                child: Text(errorMessage!, style: const TextStyle(color: warm)),
                              )
                            : const SizedBox(width: double.infinity),
                      ),
                      FadeSlideIn(
                        delay: const Duration(milliseconds: 120),
                        child: _infoSection(),
                      ),
                    ],
                  ),
      );

  Widget _infoSection() {
    final subtitle = '${currentEpisode?.title ?? 'Playing episode'} · ${active?.title ?? 'Finding server'}';
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(_animeTitle, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
        const SizedBox(height: 5),
        AnimatedSwitcher(
          duration: Motion.base,
          child: Text(subtitle, key: ValueKey(subtitle), style: const TextStyle(color: muted)),
        ),
        const SizedBox(height: 14),
        Row(children: [
          const Expanded(
            child: Text('PLAYBACK SERVERS',
                style: TextStyle(color: muted, fontWeight: FontWeight.w800, letterSpacing: 1.2)),
          ),
          TextButton.icon(
            onPressed: _refreshServers,
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const Text('Refresh'),
          ),
        ]),
        if (streams.isEmpty)
          const Text('No servers returned for this episode.', style: TextStyle(color: muted))
        else ...[
          TextField(
            key: const ValueKey('server-filter'),
            controller: serverFilterController,
            onChanged: (value) => setState(() => serverFilter = value),
            decoration: InputDecoration(
              hintText: 'Search servers by name',
              prefixIcon: const Icon(Icons.search_rounded),
              suffixIcon: serverFilter.isEmpty
                  ? null
                  : IconButton(
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
                Expanded(
                  child: Text('Auto filter · Moly, Short and Play', style: TextStyle(color: muted, fontSize: 12)),
                ),
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
                  style: const TextStyle(color: muted, height: 1.4),
                ),
              );
            }
            return Wrap(
              spacing: 8,
              runSpacing: 8,
              children: visibleServers
                  .map((choice) => ChoiceChip(
                        selected: choice.url == active?.url,
                        label: Text(choice.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                        avatar: Icon(choice.isDirect ? Icons.play_arrow_rounded : Icons.language_rounded, size: 17),
                        onSelected: (_) => _selectStream(choice),
                      ))
                  .toList(),
            );
          }),
        ],
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(currentEpisode?.title ?? 'Episode', style: const TextStyle(fontWeight: FontWeight.w900)),
              const SizedBox(height: 5),
              Text(active?.playbackKind ?? 'Resolving playback server', style: const TextStyle(color: muted)),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _hasPrevious ? () => _playEpisode(currentIndex - 1) : null,
                    icon: const Icon(Icons.skip_previous_rounded),
                    style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                    label: const Text('Prev', maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _hasPlaylist ? _showEpisodes : null,
                    icon: const Icon(Icons.playlist_play_rounded),
                    style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                    label: const Text('List', maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _hasNext ? () => _playEpisode(currentIndex + 1) : null,
                    icon: const Icon(Icons.skip_next_rounded),
                    style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12)),
                    label: const Text('Next', maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ),
              ]),
            ]),
          ),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 12),
          child: Text(
            'Short links and embedded player pages stay inside AniSteam. Direct MP4/HLS streams use the in-app video player (double-tap the left or right side to jump 10 seconds); use Open external player to hand a link to another Android app.',
            style: TextStyle(color: muted, height: 1.45),
          ),
        ),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    // PopScope replaces the deprecated WillPopScope (and also handles
    // Android predictive back + embedded-page fullscreen).
    return PopScope(
      canPop: !fullscreen && !webFullscreen,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        if (fullscreen) {
          unawaited(_leaveFullscreen());
        } else if (webFullscreen) {
          unawaited(_exitWebFullscreen());
        }
      },
      child: fullscreen ? _fullscreenScaffold() : _normalScaffold(),
    );
  }
}

class _SeekBadge extends StatelessWidget {
  const _SeekBadge({required this.visible, required this.icon});
  final bool visible;
  final IconData icon;

  @override
  Widget build(BuildContext context) => AnimatedOpacity(
        opacity: visible ? 1 : 0,
        duration: Motion.fast,
        child: AnimatedScale(
          scale: visible ? 1 : .7,
          duration: Motion.base,
          curve: Curves.easeOutBack,
          child: Center(
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: const BoxDecoration(color: Color(0x66000000), shape: BoxShape.circle),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Icon(icon, color: Colors.white, size: 30),
                const Text('10s', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w800)),
              ]),
            ),
          ),
        ),
      );
}