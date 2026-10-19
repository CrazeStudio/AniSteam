import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:anisteam/main.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('AniSteam app launches with navigation', (tester) async {
    await tester.pumpWidget(const AniSteamApp());
    await tester.pump();
    expect(find.text('AniSteam'), findsWidgets);
    expect(find.byType(AniSteamLogo), findsWidgets);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('History'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });

  testWidgets('Settings keeps service address private and exposes connection check', (tester) async {
    await tester.pumpWidget(const AniSteamApp());
    await tester.pump();
    await tester.tap(find.byIcon(Icons.tune_rounded));
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('SERVICE STATUS'), findsOneWidget);
    expect(find.text('Check connection'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    expect(find.text('API CONNECTION'), findsNothing);
    expect(find.text('Save URL'), findsNothing);
    expect(find.text('Test API'), findsNothing);
  });

  test('ignores an obsolete saved endpoint', () async {
    SharedPreferences.setMockInitialValues({
      'api_base_url': 'https://stale.invalid',
    });
    expect(await AniApi.baseUrl(), defaultApiBase);
  });

  test('watch history preserves its series title and reads older entries', () {
    const item = Anime(
      title: 'Episode 4',
      url: 'https://example.invalid/watch/episode-4',
      image: 'https://example.invalid/poster.jpg',
      source: 'ToonStream',
      seriesTitle: 'Example Anime',
    );
    final restored = Anime.fromJson(item.toJson());
    expect(restored.title, 'Episode 4');
    expect(restored.seriesTitle, 'Example Anime');

    final legacy = Anime.fromJson({
      'title': 'Older saved episode',
      'url': 'https://example.invalid/old-episode',
    });
    expect(legacy.seriesTitle, isEmpty);
  });

}
