import 'package:flutter/material.dart';

import '../geometry.dart';
import '../net/control_socket_view_model.dart';
import '../theme.dart';

enum ControlMode { laptop, phone }

/// Live-control screen - Flutter port of LaptopControlView.swift
/// (ControlMode.laptop: mouse move/click, diffed keystrokes, scroll+enter/
/// tab/esc/backspace) and PhoneControlView.swift (ControlMode.phone:
/// tap/swipe, whole-text key_text on every change, back/home/recents).
/// Pinch-to-zoom/pan and hold-to-right-click are left out here too, same
/// call the native screens made.
class ControlScreen extends StatefulWidget {
  final ControlMode mode;
  final String url;
  final String title;
  final String unpairLabel;
  final VoidCallback onUnpair;

  const ControlScreen({
    super.key,
    required this.mode,
    required this.url,
    required this.title,
    required this.unpairLabel,
    required this.onUnpair,
  });

  @override
  State<ControlScreen> createState() => _ControlScreenState();
}

class _ControlScreenState extends State<ControlScreen> {
  late final ControlSocketViewModel _viewModel;
  final _textController = TextEditingController();
  String _previousTypedText = '';
  Offset? _dragStart;

  bool get _isLaptop => widget.mode == ControlMode.laptop;

  @override
  void initState() {
    super.initState();
    _viewModel = ControlSocketViewModel(url: widget.url)
      ..addListener(_onModelChanged)
      ..connect();
  }

  void _onModelChanged() => setState(() {});

  @override
  void dispose() {
    _viewModel.removeListener(_onModelChanged);
    _viewModel.disconnect();
    _viewModel.dispose();
    _textController.dispose();
    super.dispose();
  }

  String get _statusText => switch (_viewModel.connState) {
        ConnState.disconnected => 'Disconnected',
        ConnState.connecting => 'Connecting...',
        ConnState.waitingForAgent =>
          _isLaptop ? 'Waiting for PC agent...' : 'Waiting for the other phone...',
        ConnState.connected => 'Connected',
      };

  Color get _statusColor =>
      _viewModel.connState == ConnState.connected ? LinkaTheme.accent : Colors.grey;

  void _applyTextDiff(String oldText, String newText) {
    if (newText.length > oldText.length && newText.startsWith(oldText)) {
      _viewModel.sendKeyText(text: newText.substring(oldText.length));
    } else if (newText.length < oldText.length && oldText.startsWith(newText)) {
      for (var i = 0; i < oldText.length - newText.length; i++) {
        _viewModel.sendKeyPress(key: 'backspace');
      }
    } else if (newText != oldText) {
      for (var i = 0; i < oldText.length; i++) {
        _viewModel.sendKeyPress(key: 'backspace');
      }
      if (newText.isNotEmpty) _viewModel.sendKeyText(text: newText);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: Text(widget.title, style: LinkaTheme.label),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              color: Colors.black.withValues(alpha: 0.45),
              child: Row(
                children: [
                  Text(_statusText, style: LinkaTheme.label.copyWith(color: _statusColor)),
                  const Spacer(),
                  TextButton(
                    onPressed: widget.onUnpair,
                    child: Text(
                      widget.unpairLabel,
                      style: LinkaTheme.label.copyWith(color: Colors.grey),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(child: _buildFrame()),
            Container(
              padding: const EdgeInsets.all(10),
              color: Colors.black.withValues(alpha: 0.55),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildQuickKeys(),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _textController,
                    decoration: InputDecoration(
                      hintText: _isLaptop ? 'Type to send keystrokes' : 'Type to send text',
                      border: const OutlineInputBorder(),
                    ),
                    style: const TextStyle(fontFamily: 'monospace'),
                    onChanged: (newValue) {
                      if (_isLaptop) {
                        _applyTextDiff(_previousTypedText, newValue);
                        _previousTypedText = newValue;
                      } else {
                        _viewModel.sendKeyText(text: newValue);
                      }
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickKeys() {
    if (_isLaptop) {
      return Wrap(
        spacing: 8,
        children: [
          OutlinedButton(onPressed: () => _viewModel.sendScroll(dy: 40), child: const Text('▲')),
          OutlinedButton(onPressed: () => _viewModel.sendScroll(dy: -40), child: const Text('▼')),
          OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'enter'), child: const Text('ENTER')),
          OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'tab'), child: const Text('TAB')),
          OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'esc'), child: const Text('ESC')),
          OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'backspace'), child: const Text('⌫')),
        ],
      );
    }
    return Wrap(
      spacing: 8,
      children: [
        OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'back'), child: const Text('◀ BACK')),
        OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'home'), child: const Text('⌂ HOME')),
        OutlinedButton(onPressed: () => _viewModel.sendKeyPress(key: 'recents'), child: const Text('▢ RECENTS')),
      ],
    );
  }

  Widget _buildFrame() {
    final frame = _viewModel.latestFrame;
    final imageSize = _viewModel.frameSize;

    return LayoutBuilder(builder: (context, constraints) {
      final containerSize = Size(constraints.maxWidth, constraints.maxHeight);
      return Container(
        color: Colors.black,
        width: double.infinity,
        height: double.infinity,
        child: frame == null || imageSize == null
            ? Center(child: Text('No image yet', style: LinkaTheme.body))
            : GestureDetector(
                behavior: HitTestBehavior.opaque,
                onPanStart: (details) => _dragStart = details.localPosition,
                onPanEnd: (details) {
                  final start = _dragStart;
                  if (start == null) return;
                  final end = details.localPosition;
                  final startN = normalized(start, containerSize, imageSize);
                  final endN = normalized(end, containerSize, imageSize);
                  final distance = (end - start).distance;
                  if (_isLaptop) {
                    if (distance > 4) {
                      _viewModel.sendMouseMove(x: endN.dx, y: endN.dy);
                    } else {
                      _viewModel.sendMouseClick(x: endN.dx, y: endN.dy);
                    }
                  } else {
                    if (distance > 8) {
                      _viewModel.sendSwipe(
                        x1: startN.dx, y1: startN.dy, x2: endN.dx, y2: endN.dy, durationMs: 250);
                    } else {
                      _viewModel.sendTap(x: endN.dx, y: endN.dy);
                    }
                  }
                  _dragStart = null;
                },
                child: Image.memory(frame, fit: BoxFit.contain, gaplessPlayback: true),
              ),
      );
    });
  }
}
