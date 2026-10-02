import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';

import 'control_socket_client.dart';
import 'link_message.dart';

/// Mirrors LaptopConnState/PhoneControlConnState from the Android app and
/// ConnState in ControlSocketViewModel.swift - same four states, same
/// meaning.
enum ConnState { disconnected, connecting, waitingForAgent, connected }

/// Dart port of ControlSocketViewModel.swift - wraps [ControlSocketClient]
/// for either the laptop or phone control route, translating socket-level
/// state + presence events into the four-state [ConnState] the UI renders.
class ControlSocketViewModel extends ChangeNotifier {
  ConnState connState = ConnState.connecting;
  Uint8List? latestFrame;
  ui.Size? frameSize;

  late final ControlSocketClient _client;
  StreamSubscription? _stateSub;
  StreamSubscription? _incomingSub;
  StreamSubscription? _framesSub;

  ControlSocketViewModel({required String url}) {
    _client = ControlSocketClient(url);

    _stateSub = _client.connectionState.listen((state) {
      switch (state) {
        case SocketConnState.connecting:
          connState = ConnState.connecting;
        case SocketConnState.open:
          connState = ConnState.waitingForAgent;
        case SocketConnState.closed:
          connState = ConnState.disconnected;
      }
      notifyListeners();
    });

    _incomingSub = _client.incoming.listen((message) {
      switch (message) {
        case AgentConnected():
        case ControllerConnected():
          connState = ConnState.connected;
          notifyListeners();
        case AgentDisconnected():
        case ControllerDisconnected():
          if (connState != ConnState.disconnected) {
            connState = ConnState.waitingForAgent;
            notifyListeners();
          }
        default:
          break;
      }
    });

    _framesSub = _client.frames.listen((bytes) async {
      latestFrame = bytes;
      notifyListeners();
      // Screen captures arrive as JPEG bytes (see core's ControlSocketClient
      // binary-frame contract) - decode pixel dimensions so gesture math can
      // map tap/drag coordinates the same way the aspect-fit image is drawn,
      // mirroring UIImage's `.size` on iOS.
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      frameSize = ui.Size(
        frame.image.width.toDouble(),
        frame.image.height.toDouble(),
      );
      notifyListeners();
    });
  }

  void connect() {
    connState = ConnState.connecting;
    notifyListeners();
    _client.connect();
  }

  void disconnect() => _client.disconnect();

  void sendTap({required double x, required double y}) =>
      _client.send(Tap(x: x, y: y));

  void sendSwipe({
    required double x1,
    required double y1,
    required double x2,
    required double y2,
    int durationMs = 250,
  }) =>
      _client.send(Swipe(x1: x1, y1: y1, x2: x2, y2: y2, durationMs: durationMs));

  void sendMouseMove({required double x, required double y}) =>
      _client.send(MouseMove(x: x, y: y));

  void sendMouseClick({required double x, required double y, String button = 'left'}) =>
      _client.send(MouseClick(x: x, y: y, button: button));

  void sendScroll({required int dy}) => _client.send(Scroll(dy: dy));

  void sendKeyText({required String text}) => _client.send(KeyText(text: text));

  void sendKeyPress({required String key}) => _client.send(KeyPress(key: key));

  @override
  void dispose() {
    _stateSub?.cancel();
    _incomingSub?.cancel();
    _framesSub?.cancel();
    _client.dispose();
    super.dispose();
  }
}
