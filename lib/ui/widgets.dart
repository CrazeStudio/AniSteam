import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/api.dart';
import 'motion.dart';
import 'tokens.dart';

const kPosterGrid = SliverGridDelegateWithFixedCrossAxisCount(
  crossAxisCount: 2,
  mainAxisSpacing: 15,
  crossAxisSpacing: 13,
  childAspectRatio: .59,
);

class AniSteamLogo extends StatelessWidget {
  const AniSteamLogo({super.key, this.size = 34});
  final double size;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(size * 0.27),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF242732), Color(0xFF101218)],
          ),
          border: Border.all(color: lime.withValues(alpha: 0.65), width: 1),
        ),
        child: Stack(alignment: Alignment.center, children: [
          Icon(Icons.play_arrow_rounded, color: const Color(0xFFFF7A18), size: size * 0.88),
          Positioned(
            top: size * 0.17,
            right: size * 0.20,
            child: Container(
              width: size * 0.20,
              height: size * 0.055,
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(size)),
            ),
          ),
          Positioned(
            bottom: size * 0.22,
            right: size * 0.18,
            child: Container(
              width: size * 0.15,
              height: size * 0.05,
              decoration: BoxDecoration(color: const Color(0xFFFFB15A), borderRadius: BorderRadius.circular(size)),
            ),
          ),
        ]),
      );
}

class Brand extends StatelessWidget {
  const Brand({super.key});
  @override
  Widget build(BuildContext context) => const Row(children: [
        AniSteamLogo(size: 34),
        SizedBox(width: 9),
        Text('AniSteam', style: TextStyle(fontWeight: FontWeight.w900)),
      ]);
}

/// Logo with two expanding sonar rings. Replaces plain spinners on key loads.
class LogoLoader extends StatefulWidget {
  const LogoLoader({super.key, this.size = 56});
  final double size;

  @override
  State<LogoLoader> createState() => _LogoLoaderState();
}

class _LogoLoaderState extends State<LogoLoader> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1800),
  )..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  Widget _ring(double p) {
    final s = widget.size;
    return Opacity(
      opacity: ((1 - p) * 0.7).clamp(0.0, 1.0).toDouble(),
      child: Transform.scale(
        scale: 1 + 1.3 * p,
        child: Container(
          width: s,
          height: s,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: lime, width: 2 * (1 - p) + 0.5),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final box = widget.size * 2.4;
    return SizedBox(
      width: box,
      height: box,
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) => Stack(alignment: Alignment.center, children: [
          _ring(_c.value),
          _ring((_c.value + 0.5) % 1.0),
          Transform.scale(
            scale: 1 + 0.06 * math.sin(_c.value * 2 * math.pi),
            child: AniSteamLogo(size: widget.size),
          ),
        ]),
      ),
    );
  }
}

class PosterPlaceholder extends StatelessWidget {
  const PosterPlaceholder({super.key});
  @override
  Widget build(BuildContext context) => Container(
        color: const Color(0xFF1B202B),
        child: const Center(child: Icon(Icons.movie_outlined, color: Color(0xFF6F7787), size: 45)),
      );
}

/// Network poster that fades in once decoded instead of popping in.
class PosterImage extends StatelessWidget {
  const PosterImage({super.key, required this.url, this.cacheWidth, this.fit = BoxFit.cover});
  final String url;
  final int? cacheWidth;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    if (url.isEmpty) return const PosterPlaceholder();
    return Stack(fit: StackFit.expand, children: [
      const ColoredBox(color: Color(0xFF1B202B)),
      Image.network(
        url,
        cacheWidth: cacheWidth,
        fit: fit,
        gaplessPlayback: true,
        filterQuality: FilterQuality.low,
        frameBuilder: (context, child, frame, wasSync) {
          if (wasSync) return child;
          return AnimatedOpacity(
            opacity: frame == null ? 0 : 1,
            duration: const Duration(milliseconds: 520),
            curve: Curves.easeOut,
            child: child,
          );
        },
        errorBuilder: (_, __, ___) => const PosterPlaceholder(),
      ),
    ]);
  }
}

class AnimeCard extends StatelessWidget {
  const AnimeCard({super.key, required this.anime, required this.onTap, required this.heroTag});
  final Anime anime;
  final VoidCallback onTap;
  final String heroTag;

  @override
  Widget build(BuildContext context) {
    final cacheWidth = (MediaQuery.sizeOf(context).width * .5 * MediaQuery.devicePixelRatioOf(context))
        .round()
        .clamp(240, 640)
        .toInt();
    return Pressable(
      onTap: onTap,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: Stack(fit: StackFit.expand, children: [
            Hero(
              tag: heroTag,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(17),
                child: PosterImage(url: anime.image, cacheWidth: cacheWidth),
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: 62,
              child: IgnorePointer(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: const BorderRadius.vertical(bottom: Radius.circular(17)),
                    gradient: const LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Colors.transparent, Color(0xCC000000)],
                    ),
                  ),
                ),
              ),
            ),
            Positioned(left: 8, bottom: 8, child: _SourcePill(anime.source)),
            Positioned(
              right: 8,
              top: 8,
              child: Container(
                padding: const EdgeInsets.all(5),
                decoration: const BoxDecoration(color: Color(0x99000000), shape: BoxShape.circle),
                child: const Icon(Icons.play_arrow_rounded, color: lime, size: 20),
              ),
            ),
          ]),
        ),
        const SizedBox(height: 9),
        Text(anime.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
      ]),
    );
  }
}

class _SourcePill extends StatelessWidget {
  const _SourcePill(this.source);
  final String source;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
        decoration: BoxDecoration(color: const Color(0xAA000000), borderRadius: BorderRadius.circular(8)),
        child: Text(source.toUpperCase(),
            style: const TextStyle(color: Colors.white70, fontSize: 8.5, letterSpacing: 1, fontWeight: FontWeight.w800)),
      );
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key, this.icon});
  final String text;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Row(children: [
        if (icon != null) ...[Icon(icon, color: lime, size: 20), const SizedBox(width: 8)],
        Text(text, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
        const SizedBox(width: 10),
        Expanded(
          child: Align(
            alignment: Alignment.centerLeft,
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: 1),
              duration: const Duration(milliseconds: 900),
              curve: Motion.emphasized,
              builder: (context, v, _) => Container(
                width: 64 * v,
                height: 2,
                decoration: const BoxDecoration(
                  gradient: LinearGradient(colors: [lime, Color(0x00B8F35A)]),
                ),
              ),
            ),
          ),
        ),
      ]);
}

class _PosterSkeleton extends StatelessWidget {
  const _PosterSkeleton();
  @override
  Widget build(BuildContext context) => const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(child: ShimmerBox(radius: 17)),
        SizedBox(height: 9),
        ShimmerBox(height: 12, width: 110, radius: 6),
        SizedBox(height: 6),
        ShimmerBox(height: 9, width: 60, radius: 5),
      ]);
}

class SkeletonGrid extends StatelessWidget {
  const SkeletonGrid({super.key, this.count = 6, this.shrink = false, this.padding = EdgeInsets.zero});
  final int count;
  final bool shrink;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) => GridView.builder(
        shrinkWrap: shrink,
        physics: const NeverScrollableScrollPhysics(),
        padding: padding,
        itemCount: count,
        gridDelegate: kPosterGrid,
        itemBuilder: (_, __) => const _PosterSkeleton(),
      );
}

class HomeSkeleton extends StatelessWidget {
  const HomeSkeleton({super.key});
  @override
  Widget build(BuildContext context) => Shimmer(
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 24),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const ShimmerBox(radius: 24, height: 190),
            const SizedBox(height: 26),
            const ShimmerBox(width: 170, height: 20, radius: 8),
            const SizedBox(height: 14),
            SkeletonGrid(count: 6, shrink: true),
          ]),
        ),
      );
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, required this.retry});
  final String message;
  final VoidCallback retry;

  @override
  Widget build(BuildContext context) {
    final networkFailure = message.contains('Failed host lookup') ||
        message.contains('SocketException') ||
        message.contains('No address associated with hostname');
    final shown = networkFailure
        ? 'AniSteam could not reach its service. Check your internet connection and try again in a moment.'
        : message.replaceFirst('Exception: ', '');
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: FadeSlideIn(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Floating(child: Icon(Icons.cloud_off_rounded, size: 44, color: warm)),
            const SizedBox(height: 12),
            const Text('Could not load data', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 7),
            Text(shown, textAlign: TextAlign.center, style: const TextStyle(color: muted)),
            const SizedBox(height: 14),
            OutlinedButton.icon(
              onPressed: retry,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Try again'),
            ),
          ]),
        ),
      ),
    );
  }
}

class EmptyView extends StatelessWidget {
  const EmptyView({super.key, required this.title, required this.message, this.icon = Icons.video_library_outlined});
  final String title, message;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: FadeSlideIn(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Floating(child: Icon(icon, size: 46, color: const Color(0xFF858B99))),
              const SizedBox(height: 12),
              Text(title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
              const SizedBox(height: 7),
              Text(message, textAlign: TextAlign.center, style: const TextStyle(color: muted, height: 1.4)),
            ]),
          ),
        ),
      );
}