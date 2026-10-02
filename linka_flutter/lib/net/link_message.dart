import 'dart:convert';

/// Dart port of core's LinkMessage.kt/LinkMessageCodec.kt - every text-frame
/// message exchanged over the control WebSocket, both phone-target and
/// laptop-target protocols. Wire shape is always `{"type": ..., ...fields}`,
/// matching what the Kotlin/Swift/Android clients already speak - keep this
/// file's `type` strings and field names in lockstep with LinkMessage.kt,
/// they are not independently chosen.
sealed class LinkMessage {
  const LinkMessage();

  Map<String, dynamic> _fields();
  String get _type;

  String encode() => jsonEncode({'type': _type, ..._fields()});

  static LinkMessage decode(String text) {
    final obj = jsonDecode(text) as Map<String, dynamic>;
    final type = obj['type'] as String?;
    switch (type) {
      case 'controller_connected':
        return const ControllerConnected();
      case 'controller_disconnected':
        return const ControllerDisconnected();
      case 'controller_offline':
        return const ControllerOffline();
      case 'agent_connected':
        return const AgentConnected();
      case 'agent_disconnected':
        return const AgentDisconnected();
      case 'agent_offline':
        return const AgentOffline();
      case 'info':
        return SessionInfo(
          width: obj['width'] as int,
          height: obj['height'] as int,
        );
      case 'app_access':
        return AppAccessState(allowed: obj['allowed'] as bool);
      case 'chat':
        return ChatMessage(text: obj['text'] as String);
      case 'location':
        return LocationInfo(country: obj['country'] as String);
      default:
        return const UnknownMessage();
    }
  }
}

// --- Outbound control commands ---

class Tap extends LinkMessage {
  final double x;
  final double y;
  const Tap({required this.x, required this.y});
  @override
  String get _type => 'tap';
  @override
  Map<String, dynamic> _fields() => {'x': x, 'y': y};
}

class Swipe extends LinkMessage {
  final double x1, y1, x2, y2;
  final int durationMs;
  const Swipe({
    required this.x1,
    required this.y1,
    required this.x2,
    required this.y2,
    this.durationMs = 250,
  });
  @override
  String get _type => 'swipe';
  @override
  Map<String, dynamic> _fields() =>
      {'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2, 'durationMs': durationMs};
}

class MouseMove extends LinkMessage {
  final double x, y;
  const MouseMove({required this.x, required this.y});
  @override
  String get _type => 'mouse_move';
  @override
  Map<String, dynamic> _fields() => {'x': x, 'y': y};
}

class MouseClick extends LinkMessage {
  final double x, y;
  final String button;
  const MouseClick({required this.x, required this.y, this.button = 'left'});
  @override
  String get _type => 'mouse_click';
  @override
  Map<String, dynamic> _fields() => {'x': x, 'y': y, 'button': button};
}

class Scroll extends LinkMessage {
  final int dy;
  const Scroll({required this.dy});
  @override
  String get _type => 'scroll';
  @override
  Map<String, dynamic> _fields() => {'dy': dy};
}

class KeyText extends LinkMessage {
  final String text;
  const KeyText({required this.text});
  @override
  String get _type => 'key_text';
  @override
  Map<String, dynamic> _fields() => {'text': text};
}

class KeyPress extends LinkMessage {
  final String key;
  const KeyPress({required this.key});
  @override
  String get _type => 'key_press';
  @override
  Map<String, dynamic> _fields() => {'key': key};
}

// --- Inbound presence events ---

class ControllerConnected extends LinkMessage {
  const ControllerConnected();
  @override
  String get _type => 'controller_connected';
  @override
  Map<String, dynamic> _fields() => const {};
}

class ControllerDisconnected extends LinkMessage {
  const ControllerDisconnected();
  @override
  String get _type => 'controller_disconnected';
  @override
  Map<String, dynamic> _fields() => const {};
}

class ControllerOffline extends LinkMessage {
  const ControllerOffline();
  @override
  String get _type => 'controller_offline';
  @override
  Map<String, dynamic> _fields() => const {};
}

class AgentConnected extends LinkMessage {
  const AgentConnected();
  @override
  String get _type => 'agent_connected';
  @override
  Map<String, dynamic> _fields() => const {};
}

class AgentDisconnected extends LinkMessage {
  const AgentDisconnected();
  @override
  String get _type => 'agent_disconnected';
  @override
  Map<String, dynamic> _fields() => const {};
}

class AgentOffline extends LinkMessage {
  const AgentOffline();
  @override
  String get _type => 'agent_offline';
  @override
  Map<String, dynamic> _fields() => const {};
}

class SessionInfo extends LinkMessage {
  final int width, height;
  const SessionInfo({required this.width, required this.height});
  @override
  String get _type => 'info';
  @override
  Map<String, dynamic> _fields() => {'width': width, 'height': height};
}

class AppAccessState extends LinkMessage {
  final bool allowed;
  const AppAccessState({required this.allowed});
  @override
  String get _type => 'app_access';
  @override
  Map<String, dynamic> _fields() => {'allowed': allowed};
}

class ChatMessage extends LinkMessage {
  final String text;
  const ChatMessage({required this.text});
  @override
  String get _type => 'chat';
  @override
  Map<String, dynamic> _fields() => {'text': text};
}

class LocationInfo extends LinkMessage {
  final String country;
  const LocationInfo({required this.country});
  @override
  String get _type => 'location';
  @override
  Map<String, dynamic> _fields() => {'country': country};
}

class UnknownMessage extends LinkMessage {
  const UnknownMessage();
  @override
  String get _type => '';
  @override
  Map<String, dynamic> _fields() => const {};
}
