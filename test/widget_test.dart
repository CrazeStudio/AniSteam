import 'package:flutter_test/flutter_test.dart';
import 'package:anisteam/main.dart';

void main() {
  testWidgets('AniSteam app launches with navigation', (tester) async {
    await tester.pumpWidget(const AniSteamApp());
    await tester.pump();
    expect(find.text('AniSteam'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('History'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });
}
