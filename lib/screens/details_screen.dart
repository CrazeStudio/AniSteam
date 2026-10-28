import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../ui/motion.dart';
import '../ui/tokens.dart';
import '../ui/widgets.dart';
import 'watch_screen.dart';

class DetailsScreen extends StatefulWidget {
  const DetailsScreen({
    super.key,
    required this.anime,
    required this.onWatch,
    this.heroTag,
  });
  final Anime anime;
  final ValueChanged<Anime> onWatch;
  final String? heroTag;

  @override
  State<DetailsScreen> createState() => _DetailsScreenState();
}

class _DetailsScreenState extends State<DetailsScreen> {
  static const double _expandedHeight = 360;

  late Future<List<Anime>> episodes = AniApi.episodes(widget.anime);
  late Future<dynamic> details = AniApi.details(widget.anime);

  String findDescription(dynamic v) {
    if (v is Map) {
      for (final k in ['description', 'synopsis', 'overview', 'summary']) {
        final x = v[k];
        if (x is String && x.trim().isNotEmpty) return x.trim();
      }
      for (final x in v.values) {
        final found = findDescription(x);
        if (found.isNotEmpty) return found;
      }
    }
    if (v is List) {
      for (final x in v) {
        final found = findDescription(x);
        if (found.isNotEmpty) return found;
      }
    }
    return '';
  }

  void _recordEpisode(Anime item) => widget.onWatch(Anime(
        title: item.title,
        url: item.url,
        image: widget.anime.image,
        description: widget.anime.description,
        source: item.source,
        seriesTitle: widget.anime.title,
      ));

  void _play(List<Anime> list, int index) {
    _recordEpisode(list[index]);
    Navigator.push(
      context,
      smoothRoute(WatchScreen(
        episode: list[index],
        playlist: list,
        startIndex: index,
        animeTitle: widget.anime.title,
        onEpisodeChanged: _recordEpisode,
      )),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        slivers: [
          _header(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 0),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                FutureBuilder<dynamic>(
                  future: details,
                  builder: (context, snap) {
                    final d = widget.anime.description.isNotEmpty
                        ? widget.anime.description
                        : findDescription(snap.data);
                    final loading = snap.connectionState == ConnectionState.waiting && d.isEmpty;
                    return AnimatedSwitcher(
                      duration: Motion.base,
                      child: loading
                          ? const Shimmer(
                              key: ValueKey('desc-loading'),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                ShimmerBox(height: 12, radius: 6),
                                SizedBox(height: 8),
                                ShimmerBox(height: 12, radius: 6),
                                SizedBox(height: 8),
                                ShimmerBox(height: 12, width: 160, radius: 6),
                              ]),
                            )
                          : _ExpandableText(
                              key: const ValueKey('desc'),
                              text: d.isEmpty ? 'Choose an episode below to continue.' : d,
                            ),
                    );
                  },
                ),
                FutureBuilder<List<Anime>>(
                  future: episodes,
                  builder: (context, snap) {
                    final list = snap.data;
                    if (list == null || list.isEmpty) return const SizedBox.shrink();
                    return FadeSlideIn(
                      child: Padding(
                        padding: const EdgeInsets.only(top: 18),
                        child: PulseGlow(
                          radius: 16,
                          child: SizedBox(
                            width: double.infinity,
                            child: FilledButton.icon(
                              onPressed: () {
                                tapHaptic();
                                _play(list, 0);
                              },
                              icon: const Icon(Icons.play_arrow_rounded),
                              label: Text(list.length == 1 ? 'Watch now' : 'Start episode 1'),
                              style: FilledButton.styleFrom(
                                backgroundColor: lime,
                                foregroundColor: const Color(0xFF11140D),
                                padding: const EdgeInsets.symmetric(vertical: 15),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                                textStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15),
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 26),
                const SectionTitle('EPISODES'),
                const SizedBox(height: 12),
              ]),
            ),
          ),
          FutureBuilder<List<Anime>>(
            future: episodes,
            builder: (context, snap) {
              if (snap.connectionState == ConnectionState.waiting) {
                return const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(18, 0, 18, 24),
                    child: Shimmer(
                      child: Column(children: [
                        ShimmerBox(height: 64, radius: 16),
                        SizedBox(height: 10),
                        ShimmerBox(height: 64, radius: 16),
                        SizedBox(height: 10),
                        ShimmerBox(height: 64, radius: 16),
                        SizedBox(height: 10),
                        ShimmerBox(height: 64, radius: 16),
                      ]),
                    ),
                  ),
                );
              }
              if (snap.hasError) {
                return SliverToBoxAdapter(
                  child: ErrorView(
                    message: snap.error.toString(),
                    retry: () => setState(() => episodes = AniApi.episodes(widget.anime)),
                  ),
                );
              }
              final list = snap.data ?? [];
              if (list.isEmpty) {
                return const SliverToBoxAdapter(
                  child: EmptyView(
                    title: 'No episodes found',
                    message: 'The selected source returned no episode links.',
                  ),
                );
              }
              return SliverPadding(
                padding: const EdgeInsets.fromLTRB(18, 0, 18, 32),
                sliver: SliverList.builder(
                  itemCount: list.length,
                  itemBuilder: (context, i) => _EpisodeTile(
                    index: i,
                    episode: list[i],
                    onTap: () => _play(list, i),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _header(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    final tag = widget.heroTag ?? 'poster:${widget.anime.url}';
    return SliverAppBar(
      pinned: true,
      expandedHeight: _expandedHeight,
      backgroundColor: bg,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      flexibleSpace: LayoutBuilder(builder: (context, c) {
        final t = ((c.maxHeight - (top + kToolbarHeight)) / (_expandedHeight - kToolbarHeight))
            .clamp(0.0, 1.0)
            .toDouble();
        return Stack(fit: StackFit.expand, children: [
          // Blurred backdrop that zooms slightly as the header collapses.
          Positioned.fill(
            child: Transform.scale(
              scale: 1.15 + 0.1 * t,
              child: ImageFiltered(
                imageFilter: ui.ImageFilter.blur(sigmaX: 22, sigmaY: 22, tileMode: TileMode.clamp),
                child: PosterImage(url: widget.anime.image, cacheWidth: 360),
              ),
            ),
          ),
          const Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0x990B0D12), Color(0x330B0D12), bg],
                  stops: [0, .45, 1],
                ),
              ),
            ),
          ),
          // Title that fades in once the big header has collapsed.
          Positioned(
            left: 56,
            right: 16,
            top: top,
            height: kToolbarHeight,
            child: IgnorePointer(
              child: Opacity(
                opacity: ((1 - t) * 1.6 - .6).clamp(0.0, 1.0).toDouble(),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(widget.anime.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                ),
              ),
            ),
          ),
          Positioned(
            left: 18,
            right: 18,
            bottom: 18,
            child: Opacity(
              opacity: Curves.easeOut.transform(t).clamp(0.0, 1.0).toDouble(),
              child: Transform.translate(
                offset: Offset(0, (1 - t) * 24),
                child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                  SizedBox(
                    width: 124,
                    height: 186,
                    child: Hero(
                      tag: tag,
                      child: Container(
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(16),
                          boxShadow: const [
                            BoxShadow(color: Color(0x88000000), blurRadius: 22, offset: Offset(0, 10)),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(16),
                          child: PosterImage(url: widget.anime.image, cacheWidth: 372),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: lime.withValues(alpha: .12),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: lime.withValues(alpha: .3)),
                        ),
                        child: Text(widget.anime.source.toUpperCase(),
                            style: const TextStyle(color: lime, fontSize: 10, fontWeight: FontWeight.w800, letterSpacing: 1.2)),
                      ),
                      const SizedBox(height: 10),
                      Text(widget.anime.title,
                          maxLines: 4,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w900, height: 1.1)),
                    ]),
                  ),
                ]),
              ),
            ),
          ),
        ]);
      }),
    );
  }
}

class _ExpandableText extends StatefulWidget {
  const _ExpandableText({super.key, required this.text});
  final String text;

  @override
  State<_ExpandableText> createState() => _ExpandableTextState();
}

class _ExpandableTextState extends State<_ExpandableText> {
  bool expanded = false;

  @override
  Widget build(BuildContext context) {
    final long = widget.text.length > 200;
    return AnimatedSize(
      duration: Motion.base,
      curve: Motion.emphasized,
      alignment: Alignment.topCenter,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(
          widget.text,
          maxLines: expanded || !long ? null : 4,
          overflow: expanded || !long ? TextOverflow.visible : TextOverflow.ellipsis,
          style: const TextStyle(color: Color(0xFFB0B5C1), height: 1.5),
        ),
        if (long)
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => setState(() => expanded = !expanded),
            child: Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(expanded ? 'Show less' : 'Read more',
                  style: const TextStyle(color: lime, fontWeight: FontWeight.w800)),
            ),
          ),
      ]),
    );
  }
}

class _EpisodeTile extends StatelessWidget {
  const _EpisodeTile({required this.index, required this.episode, required this.onTap});
  final int index;
  final Anime episode;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return FadeSlideIn(
      delay: index < 10 ? Duration(milliseconds: index * 40) : Duration.zero,
      duration: const Duration(milliseconds: 420),
      offset: const Offset(0, 16),
      child: Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Pressable(
          onTap: onTap,
          scale: .98,
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: hairline),
            ),
            child: Row(children: [
              Container(
                width: 42,
                height: 42,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: lime.withValues(alpha: .13), shape: BoxShape.circle),
                child: Text('${index + 1}', style: const TextStyle(color: lime, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(episode.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(color: lime.withValues(alpha: .12), shape: BoxShape.circle),
                child: const Icon(Icons.play_arrow_rounded, color: lime, size: 20),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}