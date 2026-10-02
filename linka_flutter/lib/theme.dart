import 'package:flutter/material.dart';

/// Matches THEME_COLOR = Color(0xFF00E5A0) in the Android app's MainActivity.kt
/// and Theme.swift's Theme.color on iOS - same accent across all three platforms.
class LinkaTheme {
  static const accent = Color(0xFF00E5A0);
  static const background = Colors.black;
  static const secondaryText = Colors.grey;

  static const _mono = 'monospace';

  static const headline = TextStyle(
    fontFamily: _mono,
    fontSize: 20,
    fontWeight: FontWeight.bold,
    color: accent,
  );

  static const body = TextStyle(
    fontFamily: _mono,
    fontSize: 12,
    color: secondaryText,
  );

  static const label = TextStyle(
    fontFamily: _mono,
    fontSize: 11,
  );

  static ThemeData data() {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: background,
      colorScheme: ColorScheme.fromSeed(
        seedColor: accent,
        brightness: Brightness.dark,
      ),
      fontFamily: _mono,
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.white,
          side: const BorderSide(color: Colors.white38),
          textStyle: const TextStyle(fontFamily: _mono, fontSize: 13),
        ),
      ),
    );
  }
}
