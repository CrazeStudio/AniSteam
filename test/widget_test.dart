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
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('History'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });

  testWidgets('Settings opens and saves a custom API URL', (tester) async {
    await tester.pumpWidget(const AniSteamApp());
    await tester.pump();
    await tester.tap(find.byIcon(Icons.tune_rounded));
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('API CONNECTION'), findsOneWidget);
    final urlField = find.byType(TextField).last;
    await tester.enterText(urlField, 'https://example-api.vercel.app');
    await tester.scrollUntilVisible(find.text('Save URL'), 300, scrollable: find.byType(Scrollable).first);
    await tester.tap(find.text('Save URL'));
    await tester.pump(const Duration(milliseconds: 300));

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('api_base_url'), 'https://example-api.vercel.app');
    expect(find.textContaining('API URL saved'), findsOneWidget);
  });
}
