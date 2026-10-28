import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/api.dart';
import 'screens/details_screen.dart';
import 'screens/tabs.dart';
import 'ui/motion.dart';
import 'ui/tokens.dart';

void main() => runApp(const AniSteamApp());

class AniSteamApp extends StatelessWidget {
  const AniSteamApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'AniSteam',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          scaffoldBackgroundColor: bg,
          colorScheme: ColorScheme.fromSeed(seedColor: lime, brightness: Brightness.dark),
          appBarTheme: const AppBarTheme(
            backgroundColor: bg,
            surfaceTintColor: Colors.transparent,
          ),
          cardTheme: CardThemeData(
            color: surface,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          inputDecorationTheme: InputDecorationTheme(
            filled: true,
            fillColor: surface,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        home: const AppShell(),
      );
}

class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  static const _historyKey = 'anisteam_watch_history_v1';
  int _tab = 0;
  String _sourceMode = 'COMBINED';
  List<Anime> _history = const [];
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _restoreState();
  }

  Future<void> _restoreState() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final rawHistory = prefs.getString(_historyKey);
      final rawMode = prefs.getString('anisteam_source_mode');
      final decoded = rawHistory == null ? <Anime>[] : (jsonDecode(rawHistory) as List)
          .whereType<Map>()
          .map((item) => Anime.fromJson(Map<String, dynamic>.from(item)))
          .where((item) => item.url.startsWith('http'))
          .toList();
      if (!mounted) return;
      setState(() {
        _history = decoded;
        if (rawMode == 'COMBINED' || rawMode == 'TOONSTREAM' || rawMode == 'HIANIME') {
          _sourceMode = rawMode!;
        }
        _ready = true;
      });
    } catch (_) {
      if (mounted) setState(() => _ready = true);
    }
  }

  Future<void> _saveHistory() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_historyKey, jsonEncode(_history.map((item) => item.toJson()).toList()));
    } catch (_) {}
  }

  void _recordWatch(Anime anime) {
    setState(() {
      _history = [anime, ..._history.where((item) => item.url != anime.url)].take(100).toList();
    });
    _saveHistory();
  }

  void _removeHistory(Anime anime) {
    setState(() => _history = _history.where((item) => item.url != anime.url).toList());
    _saveHistory();
  }

  void _clearHistory() {
    setState(() => _history = const []);
    _saveHistory();
  }

  Future<void> _setSourceMode(String mode) async {
    setState(() => _sourceMode = mode);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('anisteam_source_mode', mode);
    } catch (_) {}
  }

  void _openAnime(Anime anime, String heroTag) {
    Navigator.of(context).push(
      smoothRoute(DetailsScreen(
        anime: anime,
        heroTag: heroTag,
        onWatch: _recordWatch,
      )),
    );
  }

  void _openHistoryAnime(Anime anime) {
    _openAnime(anime, 'history:${anime.url}');
  }

  @override
  Widget build(BuildContext context) {
    if (!_ready) {
      return const Scaffold(body: Center(child: CircularProgressIndicator(color: lime)));
    }

    final pages = <Widget>[
      HomeTab(
        sourceMode: _sourceMode,
        history: _history,
        onOpen: _openAnime,
        onContinue: _recordWatchAndOpen,
      ),
      SearchTab(sourceMode: _sourceMode, onOpen: _openAnime),
      HistoryTab(
        items: _history,
        onClear: _clearHistory,
        onOpen: _openHistoryAnime,
        onRemove: _removeHistory,
        onRestore: (item, index) => _recordWatch(item),
      ),
      SettingsTab(sourceMode: _sourceMode, onMode: _setSourceMode),
    ];

    return Scaffold(
      body: IndexedStack(index: _tab, children: pages),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Container(
          decoration: const BoxDecoration(
            color: bg,
            border: Border(top: BorderSide(color: hairline)),
          ),
          child: NavigationBar(
            selectedIndex: _tab,
            onDestinationSelected: (index) => setState(() => _tab = index),
            backgroundColor: bg,
            indicatorColor: lime.withValues(alpha: .14),
            destinations: const [
              NavigationDestination(icon: Icon(Icons.home_rounded), label: 'Home'),
              NavigationDestination(icon: Icon(Icons.search_rounded), label: 'Search'),
              NavigationDestination(icon: Icon(Icons.history_rounded), label: 'History'),
              NavigationDestination(icon: Icon(Icons.tune_rounded), label: 'Settings'),
            ],
          ),
        ),
      ),
    );
  }

  void _recordWatchAndOpen(Anime anime) {
    _recordWatch(anime);
    _openAnime(anime, 'continue:${anime.url}');
  }
}
