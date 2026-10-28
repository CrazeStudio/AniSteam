import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api.dart';
import '../ui/motion.dart';
import '../ui/tokens.dart';
import '../ui/widgets.dart';

typedef OpenAnime = void Function(Anime anime, String heroTag);

// ───────────────────────── Home ─────────────────────────

class HomeTab extends StatefulWidget {
  const HomeTab({
    super.key,
    required this.sourceMode,
    required this.history,
    required this.onOpen,
    required this.onContinue,
  });
  final String sourceMode;
  final List<Anime> history;
  final OpenAnime onOpen;
  final ValueChanged<Anime> onContinue;

  @override
  State<HomeTab> createState() => _HomeTabState();
}

class _HomeTabState extends State<HomeTab> {
  List<Anime> items = const [];
  Object? error;
  bool loading = true;
  bool refreshing = false;
  double refreshTurns = 0;
  int _token = 0;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  /// Paint the saved catalogue immediately, then refresh in the background.
  Future<void> _bootstrap() async {
    final cached = await AniApi.cachedCatalogue();
    if (!mounted) return;
    if (cached.isNotEmpty) {
      setState(() {
        items = cached;
        loading = false;
      });
    }
    await _load();
  }

  Future<void> _load() async {
    final token = ++_token;
    setState(() {
      refreshing = true;
      error = null;
      if (items.isEmpty) loading = true;
    });
    try {
      final fresh = await AniApi.catalogue();
      if (!mounted || token != _token) return;
      setState(() {
        items = fresh;
        loading = false;
        refreshing = false;
      });
    } catch (e) {
      if (!mounted || token != _token) return;
      setState(() {
        loading = false;
        refreshing = false;
        if (items.isEmpty) error = e;
      });
    }
  }

  void _manualRefresh() {
    setState(() => refreshTurns += 1);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    Widget body;
    if (loading && items.isEmpty) {
      body = const HomeSkeleton(key: ValueKey('home-loading'));
    } else if (error != null && items.isEmpty) {
      body = ErrorView(key: const ValueKey('home-error'), message: error.toString(), retry: _load);
    } else if (items.isEmpty) {
      body = const EmptyView(
        key: ValueKey('home-empty'),
        title: 'No catalogue yet',
        message: 'The API returned no catalogue items.',
      );
    } else {
      body = _content();
    }
    return Scaffold(
      appBar: AppBar(
        title: const Brand(),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _manualRefresh,
            icon: AnimatedRotation(
              turns: refreshTurns,
              duration: const Duration(milliseconds: 700),
              curve: Curves.easeOutCubic,
              child: const Icon(Icons.refresh_rounded),
            ),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: SizedBox(
            height: 2,
            child: refreshing && items.isNotEmpty
                ? const LinearProgressIndicator(minHeight: 2, color: lime, backgroundColor: Colors.transparent)
                : null,
          ),
        ),
      ),
      body: AnimatedSwitcher(duration: const Duration(milliseconds: 350), child: body),
    );
  }

  Widget _content() {
    return RefreshIndicator(
      key: const ValueKey('home-data'),
      color: lime,
      backgroundColor: surface,
      onRefresh: _load,
      child: CustomScrollView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 22),
              child: _HomeHero(sourceMode: widget.sourceMode),
            ),
          ),
          if (widget.history.isNotEmpty)
            SliverToBoxAdapter(child: _ContinueRow(history: widget.history, onContinue: widget.onContinue)),
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 14),
              child: SectionTitle('Explore catalogue', icon: Icons.auto_awesome_rounded),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            sliver: SliverGrid(
              delegate: SliverChildBuilderDelegate(
                (context, i) {
                  final a = items[i];
                  final tag = 'home:${a.url}';
                  return FadeSlideIn(
                    key: ValueKey(tag),
                    delay: Motion.stagger(i < 8 ? i : i % 2, step: 55),
                    duration: const Duration(milliseconds: 520),
                    child: AnimeCard(anime: a, heroTag: tag, onTap: () => widget.onOpen(a, tag)),
                  );
                },
                childCount: items.length,
              ),
              gridDelegate: kPosterGrid,
            ),
          ),
        ],
      ),
    );
  }
}

class _ContinueRow extends StatelessWidget {
  const _ContinueRow({required this.history, required this.onContinue});
  final List<Anime> history;
  final ValueChanged<Anime> onContinue;

  @override
  Widget build(BuildContext context) {
    final count = math.min(12, history.length);
    return FadeSlideIn(
      child: Padding(
        padding: const EdgeInsets.only(bottom: 22),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: SectionTitle('Continue watching', icon: Icons.play_circle_rounded),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 196,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              physics: const BouncingScrollPhysics(),
              scrollDirection: Axis.horizontal,
              itemCount: count,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (context, i) => FadeSlideIn(
                delay: Motion.stagger(i, step: 60),
                offset: const Offset(26, 0),
                child: _ContinueWatchingCard(item: history[i], onTap: () => onContinue(history[i])),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}

class _ContinueWatchingCard extends StatelessWidget {
  const _ContinueWatchingCard({required this.item, required this.onTap});
  final Anime item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final title = item.seriesTitle.isNotEmpty ? item.seriesTitle : item.title;
    final imageWidth = (142 * MediaQuery.devicePixelRatioOf(context)).round().clamp(180, 420).toInt();
    return SizedBox(
      width: 142,
      child: Pressable(
        onTap: onTap,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(15),
              child: Stack(fit: StackFit.expand, children: [
                PosterImage(url: item.image, cacheWidth: imageWidth),
                Positioned(
                  right: 8,
                  bottom: 8,
                  child: Container(
                    padding: const EdgeInsets.all(7),
                    decoration: BoxDecoration(color: lime, borderRadius: BorderRadius.circular(20)),
                    child: const Icon(Icons.play_arrow_rounded, color: Color(0xFF11140D), size: 20),
                  ),
                ),
              ]),
            ),
          ),
          const SizedBox(height: 8),
          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800)),
          Text(item.title == title ? item.source : item.title,
              maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10, color: muted)),
        ]),
      ),
    );
  }
}

class _HomeHero extends StatelessWidget {
  const _HomeHero({required this.sourceMode});
  final String sourceMode;

  @override
  Widget build(BuildContext context) {
    final sourceLabel = sourceMode == 'COMBINED' ? 'TOONSTREAM  +  HIANIME' : sourceMode.replaceAll('_', ' ');
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF252B3B), Color(0xFF151923), Color(0xFF10131A)],
        ),
        border: Border.all(color: lime.withValues(alpha: .28)),
        boxShadow: [BoxShadow(color: lime.withValues(alpha: .06), blurRadius: 24, spreadRadius: 1)],
      ),
      child: Aurora(
        borderRadius: 24,
        child: Padding(
          padding: const EdgeInsets.all(19),
          child: Stack(children: [
            Positioned(
              right: -12,
              bottom: -24,
              child: Floating(
                amplitude: 8,
                child: Icon(Icons.play_circle_outline_rounded, size: 132, color: lime.withValues(alpha: .09)),
              ),
            ),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              FadeSlideIn(
                offset: const Offset(0, 14),
                child: Row(children: [
                  const AniSteamLogo(size: 38),
                  const SizedBox(width: 10),
                  const Text('AniSteam', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
                  const Spacer(),
                  const Icon(Icons.auto_awesome_rounded, color: Color(0xFFFF9A43), size: 21),
                ]),
              ),
              const SizedBox(height: 22),
              const FadeSlideIn(
                delay: Duration(milliseconds: 90),
                child: Text('Your next obsession',
                    style: TextStyle(fontSize: 26, height: 1.08, fontWeight: FontWeight.w900, letterSpacing: -.4)),
              ),
              const FadeSlideIn(
                delay: Duration(milliseconds: 170),
                child: Text('starts here.',
                    style: TextStyle(fontSize: 26, height: 1.08, fontWeight: FontWeight.w900, letterSpacing: -.4, color: lime)),
              ),
              const SizedBox(height: 9),
              const FadeSlideIn(
                delay: Duration(milliseconds: 260),
                child: Text('Find a series. Pick an episode. Get watching.',
                    style: TextStyle(fontSize: 13, color: Color(0xFFB6BDCB), height: 1.4)),
              ),
              const SizedBox(height: 16),
              FadeSlideIn(
                delay: const Duration(milliseconds: 340),
                beginScale: .92,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
                  decoration: BoxDecoration(
                    color: lime.withValues(alpha: .10),
                    borderRadius: BorderRadius.circular(30),
                    border: Border.all(color: lime.withValues(alpha: .20)),
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    PulseGlow(
                      radius: 4,
                      child: Container(width: 8, height: 8, decoration: const BoxDecoration(color: lime, shape: BoxShape.circle)),
                    ),
                    const SizedBox(width: 8),
                    Text(sourceLabel,
                        style: const TextStyle(fontSize: 10, letterSpacing: 1.0, fontWeight: FontWeight.w800, color: lime)),
                  ]),
                ),
              ),
            ]),
          ]),
        ),
      ),
    );
  }
}

// ───────────────────────── Search ─────────────────────────

class SearchTab extends StatefulWidget {
  const SearchTab({super.key, required this.sourceMode, required this.onOpen});
  final String sourceMode;
  final OpenAnime onOpen;

  @override
  State<SearchTab> createState() => _SearchTabState();
}

class _SearchTabState extends State<SearchTab> {
  static const suggestions = ['Naruto', 'One Piece', 'Jujutsu Kaisen', 'Demon Slayer', 'Attack on Titan', 'Solo Leveling'];

  final controller = TextEditingController();
  Timer? _debounce;
  int _token = 0;
  List<Anime>? results;
  String? error;
  bool busy = false;
  String lastQuery = '';

  @override
  void dispose() {
    _debounce?.cancel();
    controller.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    _debounce?.cancel();
    final q = value.trim();
    if (q.isEmpty) {
      _token++;
      setState(() {
        results = null;
        error = null;
        busy = false;
      });
      return;
    }
    setState(() {});
    // Live search once the user pauses typing.
    if (q.length >= 3) _debounce = Timer(const Duration(milliseconds: 600), () => submit());
  }

  Future<void> submit({bool dismissKeyboard = false}) async {
    _debounce?.cancel();
    final q = controller.text.trim();
    if (q.isEmpty) return;
    if (dismissKeyboard) FocusScope.of(context).unfocus();
    final token = ++_token;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final r = await AniApi.search(q, widget.sourceMode);
      if (!mounted || token != _token) return; // a newer search replaced this one
      setState(() {
        results = r;
        lastQuery = q;
        busy = false;
      });
    } catch (e) {
      if (!mounted || token != _token) return;
      setState(() {
        results = null;
        error = e.toString().replaceFirst('Exception: ', '');
        lastQuery = q;
        busy = false;
      });
    }
  }

  void _useSuggestion(String s) {
    controller.text = s;
    controller.selection = TextSelection.collapsed(offset: s.length);
    submit(dismissKeyboard: true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Search anime', style: TextStyle(fontWeight: FontWeight.w900))),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            Expanded(
              child: TextField(
                controller: controller,
                textInputAction: TextInputAction.search,
                onChanged: _onChanged,
                onSubmitted: (_) => submit(dismissKeyboard: true),
                decoration: InputDecoration(
                  hintText: 'Search titles...',
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: controller.text.isEmpty
                      ? null
                      : IconButton(
                          tooltip: 'Clear',
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () {
                            controller.clear();
                            _onChanged('');
                          },
                        ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(16),
                    borderSide: BorderSide(color: lime.withValues(alpha: .7), width: 1.4),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filled(
              onPressed: busy ? null : () => submit(dismissKeyboard: true),
              icon: AnimatedSwitcher(
                duration: Motion.fast,
                child: busy
                    ? const SizedBox(key: ValueKey('busy'), width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.arrow_forward_rounded, key: ValueKey('go')),
              ),
            ),
          ]),
        ),
        Expanded(child: AnimatedSwitcher(duration: const Duration(milliseconds: 300), child: _body())),
      ]),
    );
  }

  Widget _body() {
    if (busy) {
      return const Shimmer(
        key: ValueKey('search-busy'),
        child: SkeletonGrid(padding: EdgeInsets.fromLTRB(16, 4, 16, 16)),
      );
    }
    if (error != null) {
      if (error!.startsWith('No results')) {
        return EmptyView(
          key: ValueKey('search-none-$lastQuery'),
          icon: Icons.search_off_rounded,
          title: 'No results',
          message: 'Nothing matched “$lastQuery”. Try a different title or another source in Settings.',
        );
      }
      return ErrorView(key: const ValueKey('search-error'), message: error!, retry: submit);
    }
    final list = results;
    if (list == null) return _idle();
    return Column(key: ValueKey('search-results-$lastQuery'), children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(18, 0, 18, 8),
        child: Align(
          alignment: Alignment.centerLeft,
          child: Text('${list.length} ${list.length == 1 ? 'result' : 'results'} for “$lastQuery”',
              style: const TextStyle(color: muted, fontSize: 12, fontWeight: FontWeight.w700)),
        ),
      ),
      Expanded(
        child: GridView.builder(
          physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
          itemCount: list.length,
          gridDelegate: kPosterGrid,
          itemBuilder: (context, i) {
            final a = list[i];
            final tag = 'search:${a.url}';
            return FadeSlideIn(
              key: ValueKey(tag),
              delay: Motion.stagger(i < 8 ? i : i % 2, step: 55),
              duration: const Duration(milliseconds: 520),
              child: AnimeCard(anime: a, heroTag: tag, onTap: () => widget.onOpen(a, tag)),
            );
          },
        ),
      ),
    ]);
  }

  Widget _idle() => SingleChildScrollView(
        key: const ValueKey('search-idle'),
        padding: const EdgeInsets.fromLTRB(24, 36, 24, 24),
        child: Column(children: [
          Floating(child: Icon(Icons.travel_explore_rounded, size: 64, color: lime.withValues(alpha: .85))),
          const SizedBox(height: 16),
          const Text('Find your next favourite', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          const Text('Search across the providers enabled in Settings.', textAlign: TextAlign.center, style: TextStyle(color: muted)),
          const SizedBox(height: 24),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 10,
            runSpacing: 10,
            children: [
              for (var i = 0; i < suggestions.length; i++)
                FadeSlideIn(
                  delay: Motion.stagger(i, step: 60),
                  beginScale: .9,
                  child: Pressable(
                    onTap: () => _useSuggestion(suggestions[i]),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                      decoration: BoxDecoration(
                        color: surface,
                        borderRadius: BorderRadius.circular(30),
                        border: Border.all(color: hairline),
                      ),
                      child: Row(mainAxisSize: MainAxisSize.min, children: [
                        const Icon(Icons.trending_up_rounded, size: 15, color: lime),
                        const SizedBox(width: 6),
                        Text(suggestions[i], style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                      ]),
                    ),
                  ),
                ),
            ],
          ),
        ]),
      );
}

// ───────────────────────── History ─────────────────────────

class HistoryTab extends StatelessWidget {
  const HistoryTab({
    super.key,
    required this.items,
    required this.onClear,
    required this.onOpen,
    required this.onRemove,
    required this.onRestore,
  });
  final List<Anime> items;
  final VoidCallback onClear;
  final ValueChanged<Anime> onOpen;
  final ValueChanged<Anime> onRemove;
  final void Function(Anime item, int index) onRestore;

  Future<void> _confirmClear(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear watch history?'),
        content: Text('This removes all ${items.length} saved ${items.length == 1 ? 'episode' : 'episodes'} from this device.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Clear')),
        ],
      ),
    );
    if (ok == true) onClear();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Watch history', style: TextStyle(fontWeight: FontWeight.w900)),
        actions: [
          IconButton(
            tooltip: 'Clear history',
            onPressed: items.isEmpty ? null : () => _confirmClear(context),
            icon: const Icon(Icons.delete_sweep_rounded),
          ),
        ],
      ),
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 300),
        child: items.isEmpty
            ? const EmptyView(
                key: ValueKey('history-empty'),
                title: 'Nothing watched yet',
                message: 'Episodes you open will be saved here on this device.',
                icon: Icons.history_rounded,
              )
            : ListView.builder(
                key: const ValueKey('history-list'),
                physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                itemCount: items.length,
                itemBuilder: (context, i) {
                  final item = items[i];
                  final seriesTitle = item.seriesTitle.isNotEmpty ? item.seriesTitle : item.title;
                  return FadeSlideIn(
                    key: ValueKey('hist-fade-${item.url}'),
                    delay: Motion.stagger(i, step: 40),
                    duration: const Duration(milliseconds: 420),
                    offset: const Offset(0, 14),
                    child: Dismissible(
                      key: ValueKey('hist-${item.url}'),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        margin: const EdgeInsets.symmetric(vertical: 5),
                        padding: const EdgeInsets.only(right: 22),
                        alignment: Alignment.centerRight,
                        decoration: BoxDecoration(
                          color: const Color(0xFFFF6B6B).withValues(alpha: .16),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: const Icon(Icons.delete_outline_rounded, color: Color(0xFFFF6B6B)),
                      ),
                      onDismissed: (_) {
                        final index = items.indexOf(item);
                        onRemove(item);
                        ScaffoldMessenger.of(context)
                          ..hideCurrentSnackBar()
                          ..showSnackBar(SnackBar(
                            content: const Text('Removed from history'),
                            action: SnackBarAction(label: 'Undo', onPressed: () => onRestore(item, index < 0 ? 0 : index)),
                          ));
                      },
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 5),
                        child: Pressable(
                          onTap: () => onOpen(item),
                          scale: .98,
                          child: Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: surface,
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(color: hairline),
                            ),
                            child: Row(children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: SizedBox(width: 48, height: 68, child: PosterImage(url: item.image, cacheWidth: 160)),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  Text(seriesTitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w800)),
                                  const SizedBox(height: 3),
                                  Text(item.seriesTitle.isNotEmpty ? '${item.title} · ${item.source}' : item.source,
                                      maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: muted, fontSize: 12)),
                                ]),
                              ),
                              const Icon(Icons.play_circle_fill_rounded, color: lime, size: 30),
                            ]),
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}

// ───────────────────────── Settings ─────────────────────────

class SettingsTab extends StatefulWidget {
  const SettingsTab({super.key, required this.sourceMode, required this.onMode});
  final String sourceMode;
  final ValueChanged<String> onMode;

  @override
  State<SettingsTab> createState() => _SettingsTabState();
}

class _SettingsTabState extends State<SettingsTab> {
  bool checking = false;
  String? connectionStatus;
  bool showAllServers = false;
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
    setState(() {
      checking = true;
      connectionStatus = 'Checking connection…';
    });
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

  Widget _label(String text) =>
      Text(text, style: const TextStyle(color: muted, fontWeight: FontWeight.w800, letterSpacing: 1.4));

  Widget _status() {
    final s = connectionStatus;
    if (s == null) return const SizedBox(key: ValueKey('status-none'), width: double.infinity);
    final ok = s.startsWith('Connected');
    final color = ok ? lime : (checking ? muted : warm);
    return Container(
      key: ValueKey(s),
      width: double.infinity,
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withValues(alpha: .25)),
      ),
      child: Row(children: [
        checking
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: 1),
                duration: const Duration(milliseconds: 600),
                curve: Curves.elasticOut,
                builder: (context, v, child) => Transform.scale(scale: v, child: child),
                child: Icon(ok ? Icons.check_circle_rounded : Icons.error_outline_rounded, color: color, size: 20),
              ),
        const SizedBox(width: 10),
        Expanded(child: Text(s, style: TextStyle(color: color, height: 1.4))),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    var n = 0;
    Widget step(Widget child) => FadeSlideIn(
          delay: Motion.stagger(n++, step: 55),
          duration: const Duration(milliseconds: 480),
          offset: const Offset(0, 16),
          child: child,
        );
    return Scaffold(
      appBar: AppBar(title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w900))),
      body: ListView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        padding: const EdgeInsets.all(16),
        children: [
          step(_label('CONTENT SOURCES')),
          const SizedBox(height: 10),
          for (final option in const [
            ('COMBINED', 'Combined', 'Search ToonStream and HiAnime', Icons.layers_rounded),
            ('TOONSTREAM', 'ToonStream', 'Use ToonStream endpoints', Icons.tv_rounded),
            ('HIANIME', 'HiAnime', 'Use HiAnime endpoints', Icons.movie_filter_rounded),
          ])
            step(_SourceTile(
              icon: option.$4,
              title: option.$2,
              subtitle: option.$3,
              selected: widget.sourceMode == option.$1,
              onTap: () => widget.onMode(option.$1),
            )),
          const SizedBox(height: 22),
          step(_label('SERVICE STATUS')),
          const SizedBox(height: 8),
          step(const Text(
            'The service connection is managed privately by AniSteam. Its server address is not shown or editable here.',
            style: TextStyle(color: muted, height: 1.45),
          )),
          const SizedBox(height: 12),
          step(SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: checking ? null : checkConnection,
              icon: checking
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.wifi_tethering_rounded),
              label: Text(checking ? 'Checking…' : 'Check connection'),
            ),
          )),
          AnimatedSize(
            duration: Motion.base,
            curve: Motion.emphasized,
            alignment: Alignment.topCenter,
            child: AnimatedSwitcher(duration: Motion.base, child: _status()),
          ),
          const SizedBox(height: 22),
          step(_label('PLAYER PREFERENCES')),
          const SizedBox(height: 10),
          step(Card(
            child: SwitchListTile(
              value: showAllServers,
              onChanged: (value) => _savePlayerSetting('show_all_servers', value, () => showAllServers = value),
              secondary: const Icon(Icons.dns_rounded),
              title: const Text('Show all servers', style: TextStyle(fontWeight: FontWeight.w800)),
              subtitle: const Text('Off: automatically show Moly, Short and Play for ToonStream. On: list all returned servers.'),
              activeColor: lime,
            ),
          )),
          step(Card(
            child: SwitchListTile(
              value: keepScreenAwake,
              onChanged: (value) => _savePlayerSetting('keep_screen_awake', value, () => keepScreenAwake = value),
              secondary: const Icon(Icons.screen_lock_portrait_outlined),
              title: const Text('Keep screen awake', style: TextStyle(fontWeight: FontWeight.w800)),
              subtitle: const Text('Prevent the display from sleeping while the player is open.'),
              activeColor: lime,
            ),
          )),
          const SizedBox(height: 22),
          step(_label('ABOUT')),
          const SizedBox(height: 10),
          step(const Card(
            child: ListTile(
              leading: AniSteamLogo(size: 38),
              title: Text('AniSteam', style: TextStyle(fontWeight: FontWeight.w900)),
              subtitle: Text('v2.1.0 · Anime discovery · episode lists · stream selection · local watch history'),
            ),
          )),
          step(const Padding(
            padding: EdgeInsets.all(8),
            child: Text(
              'AniSteam connects to its configured service. Playback availability depends on the provider and returned stream. It does not bypass DRM, login, paywalls, CAPTCHA, or anti-bot restrictions.',
              style: TextStyle(color: muted, height: 1.45),
            ),
          )),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}

class _SourceTile extends StatelessWidget {
  const _SourceTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Pressable(
        onTap: onTap,
        scale: .985,
        child: AnimatedContainer(
          duration: Motion.base,
          curve: Motion.emphasized,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: selected ? lime.withValues(alpha: .08) : surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: selected ? lime.withValues(alpha: .7) : hairline, width: selected ? 1.4 : 1),
          ),
          child: Row(children: [
            AnimatedContainer(
              duration: Motion.base,
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: selected ? lime.withValues(alpha: .18) : surfaceHigh,
                borderRadius: BorderRadius.circular(13),
              ),
              child: Icon(icon, color: selected ? lime : muted, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
                const SizedBox(height: 2),
                Text(subtitle, style: const TextStyle(color: muted, fontSize: 12.5)),
              ]),
            ),
            AnimatedScale(
              scale: selected ? 1 : 0,
              duration: const Duration(milliseconds: 380),
              curve: Curves.easeOutBack,
              child: const Icon(Icons.check_circle_rounded, color: lime),
            ),
          ]),
        ),
      ),
    );
  }
}