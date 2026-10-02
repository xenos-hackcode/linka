import 'package:flutter/material.dart';

import '../theme.dart';
import 'qr_scanner_screen.dart';

/// Shared pairing-code entry UI - Flutter port of PairingSetupView.swift /
/// PairingSetup.kt. Same three ways in: type the code, scan a QR, or (once
/// paired) get handed off to the live control screen via [onPaired].
class PairingSetupScreen extends StatefulWidget {
  final String icon;
  final String headline;
  final String bodyText;
  final String buttonLabel;
  final void Function(String code) onPaired;

  const PairingSetupScreen({
    super.key,
    required this.icon,
    required this.headline,
    required this.bodyText,
    required this.buttonLabel,
    required this.onPaired,
  });

  @override
  State<PairingSetupScreen> createState() => _PairingSetupScreenState();
}

class _PairingSetupScreenState extends State<PairingSetupScreen> {
  final _controller = TextEditingController();
  String _input = '';

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _scanQr() async {
    final scanned = await Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => const QrScannerScreen()),
    );
    if (scanned == null || !mounted) return;
    final trimmed = scanned.trim();
    _controller.text = trimmed;
    setState(() => _input = trimmed);
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit = _input.trim().length >= 16;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(widget.icon, style: const TextStyle(fontSize: 40)),
                const SizedBox(height: 12),
                Text(
                  widget.headline,
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  widget.bodyText,
                  textAlign: TextAlign.center,
                  style: LinkaTheme.body,
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _controller,
                  autocorrect: false,
                  textCapitalization: TextCapitalization.none,
                  decoration: const InputDecoration(
                    hintText: 'Pairing code',
                    border: OutlineInputBorder(),
                  ),
                  style: const TextStyle(fontFamily: 'monospace'),
                  onChanged: (value) => setState(() => _input = value.trim()),
                ),
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: _scanQr,
                  child: const Text('SCAN QR INSTEAD'),
                ),
                const SizedBox(height: 8),
                FilledButton(
                  onPressed: canSubmit ? () => widget.onPaired(_input) : null,
                  style: FilledButton.styleFrom(
                    backgroundColor: LinkaTheme.accent,
                    foregroundColor: Colors.black,
                    disabledBackgroundColor: Colors.white12,
                  ),
                  child: Text(widget.buttonLabel),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
