import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'theme.dart';

void main() {
  runApp(const LinkaApp());
}

class LinkaApp extends StatelessWidget {
  const LinkaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Linka',
      debugShowCheckedModeBanner: false,
      theme: LinkaTheme.data(),
      home: const HomeScreen(),
    );
  }
}
