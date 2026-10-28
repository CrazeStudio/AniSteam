import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tokens.dart';

/// Shared timing + curves so every animation in the app feels related.
abstract final class Motion {
  static const Duration fast = Duration(milliseconds: 180);
  static const Duration base = Duration(milliseconds: 320);
  static const Duration slow = Duration(milliseconds: 560);
  static const Curve emphasized = Cubic(0.2, 0.0, 0.0, 1.0);
  static const Curve overshoot = Curves.easeOutBack;

  static bool reduced(BuildContext context) =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  static Duration stagger(int index, {int step = 45, int cap = 8}) =>
      Duration(milliseconds: math.min(index, cap) * step);
}

void tapHaptic() {
  try {
    HapticFeedback.selectionClick().catchError((_) {});
  } catch (_) {}
}

/// One-shot fade + slide (+ optional scale) entrance with a built-in delay.
class FadeSlideIn extends StatefulWidget {
  const FadeSlideIn({
    super.key,
    required this.child,
    this.delay = Duration.zero,
    this.duration = const Duration(milliseconds: 560),
    this.offset = const Offset(0, 22),
    this.beginScale = 1.0,
  });

  final Widget child;
  final Duration delay;
  final Duration duration;
  final Offset offset;
  final double beginScale;

  @override
  State<FadeSlideIn> createState() => _FadeSlideInState();
}

class _FadeSlideInState extends State<FadeSlideIn>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c;
  late final Animation<double> _a;

  @override
  void initState() {
    super.initState();
    final total = widget.delay + widget.duration;
    _c = AnimationController(vsync: this, duration: total);
    final begin = total.inMilliseconds == 0
        ? 0.0
        : widget.delay.inMilliseconds / total.inMilliseconds;
    _a = CurvedAnimation(
      parent: _c,
      curve: Interval(begin, 1.0, curve: Motion.emphasized),
    );
    _c.forward();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (Motion.reduced(context)) return widget.child;
    return AnimatedBuilder(
      animation: _a,
      child: widget.child,
      builder: (context, child) {
        final v = _a.value;
        Widget out = child!;
        if (widget.beginScale != 1.0) {
          out = Transform.scale(
            scale: widget.beginScale + (1 - widget.beginScale) * v,
            child: out,
          );
        }
        return Opacity(
          opacity: v.clamp(0.0, 1.0).toDouble(),
          child: Transform.translate(offset: widget.offset * (1 - v), child: out),
        );
      },
    );
  }
}

/// Springy press-down scale + haptic tick. Wrap any tappable surface.
class Pressable extends StatefulWidget {
  const Pressable({
    super.key,
    required this.child,
    this.onTap,
    this.scale = 0.96,
    this.haptic = true,
  });

  final Widget child;
  final VoidCallback? onTap;
  final double scale;
  final bool haptic;

  @override
  State<Pressable> createState() => _PressableState();
}

class _PressableState extends State<Pressable> {
  bool _down = false;

  void _set(bool v) {
    if (_down != v && mounted) setState(() => _down = v);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => _set(true),
      onTapUp: (_) => _set(false),
      onTapCancel: () => _set(false),
      onTap: widget.onTap == null
          ? null
          : () {
              if (widget.haptic) tapHaptic();
              widget.onTap!();
            },
      child: AnimatedScale(
        scale: _down ? widget.scale : 1.0,
        duration: Duration(milliseconds: _down ? 90 : 320),
        curve: _down ? Curves.easeOut : Curves.easeOutBack,
        child: widget.child,
      ),
    );
  }
}

/// Sweeping highlight over solid white child shapes (see [ShimmerBox]).
class Shimmer extends StatefulWidget {
  const Shimmer({super.key, required this.child});
  final Widget child;

  @override
  State<Shimmer> createState() => _ShimmerState();
}

class _ShimmerState extends State<Shimmer> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  )..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) => ShaderMask(
        blendMode: BlendMode.srcIn,
        shaderCallback: (rect) {
          final t = _c.value;
          return LinearGradient(
            begin: Alignment(-1.6 + 3.2 * t, -0.3),
            end: Alignment(-0.6 + 3.2 * t, 0.3),
            colors: const [Color(0xFF1B202B), Color(0xFF2C3345), Color(0xFF1B202B)],
            stops: const [0.15, 0.5, 0.85],
          ).createShader(rect);
        },
        child: child,
      ),
    );
  }
}

class ShimmerBox extends StatelessWidget {
  const ShimmerBox({super.key, this.radius = 14, this.height, this.width});
  final double radius;
  final double? height;
  final double? width;

  @override
  Widget build(BuildContext context) => Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(radius),
        ),
      );
}

/// Slowly drifting colour orbs. Painted, not blurred, so it stays cheap.
class Aurora extends StatefulWidget {
  const Aurora({
    super.key,
    required this.child,
    this.borderRadius = 24,
    this.colors = const [lime, Color(0xFFFF7A18), Color(0xFF6C7BFF)],
  });

  final Widget child;
  final double borderRadius;
  final List<Color> colors;

  @override
  State<Aurora> createState() => _AuroraState();
}

class _AuroraState extends State<Aurora> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 16),
  )..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final still = Motion.reduced(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(widget.borderRadius),
      child: Stack(children: [
        Positioned.fill(
          child: RepaintBoundary(
            child: AnimatedBuilder(
              animation: _c,
              builder: (context, _) => CustomPaint(
                painter: _AuroraPainter(still ? 0.0 : _c.value, widget.colors),
              ),
            ),
          ),
        ),
        widget.child,
      ]),
    );
  }
}

class _AuroraPainter extends CustomPainter {
  _AuroraPainter(this.t, this.colors);
  final double t;
  final List<Color> colors;

  @override
  void paint(Canvas canvas, Size size) {
    for (var i = 0; i < colors.length; i++) {
      final a = t * 2 * math.pi;
      final cx = size.width * (0.5 + 0.45 * math.cos(a + i * 2.1));
      final cy = size.height * (0.5 + 0.40 * math.sin(2 * a + i * 1.7));
      final r = size.shortestSide * (0.85 + 0.15 * math.sin(a + i));
      final rect = Rect.fromCircle(center: Offset(cx, cy), radius: r);
      final paint = Paint()
        ..shader = RadialGradient(colors: [
          colors[i].withValues(alpha: 0.22),
          colors[i].withValues(alpha: 0.0),
        ]).createShader(rect);
      canvas.drawCircle(rect.center, r, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _AuroraPainter old) => old.t != t;
}

/// Breathing glow behind a child (CTA buttons, status dots).
class PulseGlow extends StatefulWidget {
  const PulseGlow({super.key, required this.child, this.color = lime, this.radius = 20});
  final Widget child;
  final Color color;
  final double radius;

  @override
  State<PulseGlow> createState() => _PulseGlowState();
}

class _PulseGlowState extends State<PulseGlow> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) {
        final t = Curves.easeInOut.transform(_c.value);
        return DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(widget.radius),
            boxShadow: [
              BoxShadow(
                color: widget.color.withValues(alpha: 0.16 + 0.22 * t),
                blurRadius: 8 + 16 * t,
                spreadRadius: 2 * t,
              ),
            ],
          ),
          child: child,
        );
      },
    );
  }
}

/// Gentle idle bob for hero icons and empty states.
class Floating extends StatefulWidget {
  const Floating({super.key, required this.child, this.amplitude = 6});
  final Widget child;
  final double amplitude;

  @override
  State<Floating> createState() => _FloatingState();
}

class _FloatingState extends State<Floating> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 3400),
  )..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) => Transform.translate(
        offset: Offset(0, math.sin(_c.value * 2 * math.pi) * widget.amplitude),
        child: child,
      ),
    );
  }
}

/// Cross-fade + rise for the pushed page; the covered page eases back.
Route<T> smoothRoute<T>(Widget page) {
  return PageRouteBuilder<T>(
    transitionDuration: const Duration(milliseconds: 460),
    reverseTransitionDuration: const Duration(milliseconds: 340),
    pageBuilder: (context, animation, secondary) => page,
    transitionsBuilder: (context, animation, secondary, child) {
      final enter = CurvedAnimation(
        parent: animation,
        curve: Motion.emphasized,
        reverseCurve: Curves.easeInCubic,
      );
      final cover = CurvedAnimation(parent: secondary, curve: Curves.easeOut);
      return ScaleTransition(
        scale: Tween<double>(begin: 1, end: 0.94).animate(cover),
        child: FadeTransition(
          opacity: Tween<double>(begin: 1, end: 0.6).animate(cover),
          child: FadeTransition(
            opacity: enter,
            child: SlideTransition(
              position: Tween<Offset>(begin: const Offset(0, 0.05), end: Offset.zero)
                  .animate(enter),
              child: child,
            ),
          ),
        ),
      );
    },
  );
}

class NavItem {
  const NavItem({required this.icon, required this.selectedIcon, required this.label});
  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

/// Floating pill nav bar with a sliding, overshooting selection indicator.
class FloatingNavBar extends StatelessWidget {
  const FloatingNavBar({
    super.key,
    required this.index,
    required this.items,
    required this.onChanged,
  });

  final int index;
  final List<NavItem> items;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 6, 14, 10),
        child: Container(
          height: 66,
          decoration: BoxDecoration(
            color: const Color(0xFF12151D),
            borderRadius: BorderRadius.circular(26),
            border: Border.all(color: hairline),
            boxShadow: const [
              BoxShadow(color: Color(0x66000000), blurRadius: 24, offset: Offset(0, 8)),
            ],
          ),
          child: LayoutBuilder(builder: (context, box) {
            final w = box.maxWidth / items.length;
            return Stack(children: [
              AnimatedPositioned(
                duration: const Duration(milliseconds: 420),
                curve: Curves.easeOutBack,
                left: w * index + 6,
                top: 7,
                width: w - 12,
                height: 52,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: lime.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(19),
                    border: Border.all(color: lime.withValues(alpha: 0.25)),
                  ),
                ),
              ),
              Row(children: [
                for (var i = 0; i < items.length; i++)
                  Expanded(
                    child: _NavButton(
                      item: items[i],
                      selected: i == index,
                      onTap: () => onChanged(i),
                    ),
                  ),
              ]),
            ]);
          }),
        ),
      ),
    );
  }
}

class _NavButton extends StatelessWidget {
  const _NavButton({required this.item, required this.selected, required this.onTap});
  final NavItem item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            AnimatedSwitcher(
              duration: Motion.base,
              transitionBuilder: (child, a) => ScaleTransition(
                scale: CurvedAnimation(parent: a, curve: Curves.easeOutBack),
                child: FadeTransition(opacity: a, child: child),
              ),
              child: Icon(
                selected ? item.selectedIcon : item.icon,
                key: ValueKey(selected),
                size: 25,
                color: selected ? lime : muted,
              ),
            ),
            const SizedBox(height: 2),
            AnimatedDefaultTextStyle(
              duration: Motion.base,
              style: TextStyle(
                fontSize: 11,
                fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                color: selected ? lime : muted,
              ),
              child: Text(item.label),
            ),
          ]),
        ),
      ),
    );
  }
}

/// Fades/lifts a tab in each time it becomes the active one.
class TabReveal extends StatefulWidget {
  const TabReveal({super.key, required this.active, required this.child});
  final bool active;
  final Widget child;

  @override
  State<TabReveal> createState() => _TabRevealState();
}

class _TabRevealState extends State<TabReveal> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 380),
    value: widget.active ? 1.0 : 0.0,
  );
  late final Animation<double> _a =
      CurvedAnimation(parent: _c, curve: Motion.emphasized);

  @override
  void didUpdateWidget(covariant TabReveal old) {
    super.didUpdateWidget(old);
    if (widget.active && !old.active) _c.forward(from: 0);
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (Motion.reduced(context)) return widget.child;
    return AnimatedBuilder(
      animation: _a,
      child: widget.child,
      builder: (context, child) => Opacity(
        opacity: _a.value.clamp(0.0, 1.0).toDouble(),
        child: Transform.translate(
          offset: Offset(0, 14 * (1 - _a.value)),
          child: child,
        ),
      ),
    );
  }
}