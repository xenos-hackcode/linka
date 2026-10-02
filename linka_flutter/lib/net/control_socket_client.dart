import 'dart:async';
import 'dart:typed_data';

import 'package:web_socket_channel/web_socket_channel.dart';

import 'link_message.dart';

enum SocketConnState { connecting, open, closed }

/// initialDelayMs -> doubling -> maxDelayMs, matching ReconnectPolicy in
/// core's ControlSocketClient.kt (2000ms -> ... -> 30000ms cap).
class ReconnectPolicy {
  final int initialDelayMs;
  final int maxDelayMs;
  const ReconnectPolicy({this.initialDelayMs = 2000, this.maxDelayMs = 30000});
  int nextDelay(int currentDelayMs) =>
      (currentDelayMs * 2).clamp(0, maxDelayMs);
}

/// Dart port of core's KtorControlSocketClient - one control WebSocket
/// connection: text frames decode to [LinkMessage], binary frames (JPEG
/// screen captures) pass through raw. No Kotlin/Native interop path exists
/// from Dart into `:core`, so this reimplements the same wire behavior
/// directly against the backend rather than wrapping it.
class ControlSocketClient {
  final String url;
  final ReconnectPolicy? reconnect;

  ControlSocketClient(this.url, {this.reconnect = const ReconnectPolicy()});

  final _connectionStateController =
      StreamController<SocketConnState>.broadcast();
  final _incomingController = StreamController<LinkMessage>.broadcast();
  final _framesController = StreamController<Uint8List>.broadcast();

  Stream<SocketConnState> get connectionState => _connectionStateController.stream;
  Stream<LinkMessage> get incoming => _incomingController.stream;
  Stream<Uint8List> get frames => _framesController.stream;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _reconnectTimer;
  bool _manuallyDisconnected = false;
  int _currentDelayMs = 0;
  bool _connectLoopRunning = false;

  void connect() {
    _manuallyDisconnected = false;
    if (_connectLoopRunning) return;
    _connectLoopRunning = true;
    _currentDelayMs = reconnect?.initialDelayMs ?? 0;
    _attemptConnect();
  }

  void _attemptConnect() {
    if (_manuallyDisconnected) {
      _connectLoopRunning = false;
      return;
    }
    _connectionStateController.add(SocketConnState.connecting);
    try {
      final channel = WebSocketChannel.connect(Uri.parse(url));
      _channel = channel;
      channel.ready.then((_) {
        if (_manuallyDisconnected) return;
        _currentDelayMs = reconnect?.initialDelayMs ?? 0;
        _connectionStateController.add(SocketConnState.open);
      }).catchError((_) {
        // Falls through to onDone below once the stream errors out too.
      });
      _sub = channel.stream.listen(
        (data) {
          if (data is String) {
            try {
              _incomingController.add(LinkMessage.decode(data));
            } catch (_) {
              // Unparseable frame - matches the Kotlin client's
              // runCatching { ... }.getOrNull() no-op on decode failure.
            }
          } else if (data is List<int>) {
            _framesController.add(Uint8List.fromList(data));
          }
        },
        onDone: _handleClosed,
        onError: (_) => _handleClosed(),
        cancelOnError: true,
      );
    } catch (_) {
      _handleClosed();
    }
  }

  void _handleClosed() {
    _channel = null;
    _connectionStateController.add(SocketConnState.closed);
    if (_manuallyDisconnected) {
      _connectLoopRunning = false;
      return;
    }
    final policy = reconnect;
    if (policy == null) {
      _connectLoopRunning = false;
      return;
    }
    _reconnectTimer = Timer(Duration(milliseconds: _currentDelayMs), () {
      _currentDelayMs = policy.nextDelay(_currentDelayMs);
      _attemptConnect();
    });
  }

  void send(LinkMessage message) {
    // Best-effort: matches the Kotlin client, which doesn't queue sends made
    // while the socket happens to be down between reconnect attempts.
    try {
      _channel?.sink.add(message.encode());
    } catch (_) {}
  }

  void disconnect() {
    _manuallyDisconnected = true;
    _reconnectTimer?.cancel();
    _sub?.cancel();
    _channel?.sink.close();
    _channel = null;
    _connectionStateController.add(SocketConnState.closed);
  }

  void dispose() {
    disconnect();
    _connectionStateController.close();
    _incomingController.close();
    _framesController.close();
  }
}
