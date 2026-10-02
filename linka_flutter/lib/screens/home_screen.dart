import 'package:flutter/material.dart';

import '../theme.dart';
import '../token_store.dart';
import 'control_screen.dart';
import 'pairing_setup_screen.dart';

const _backendBase = 'wss://elene-backend-717899371194.us-central1.run.app';

/// Root screen - Flutter port of ControllerHomeView.swift / ControllerHomeScreen.kt.
/// No role picker yet since only the Controller role exists so far (see
/// PLANNING.md §5) - this is the app's actual entry screen, not nested under
/// anything.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('LINKA', style: LinkaTheme.headline),
              const SizedBox(height: 16),
              const Text(
                "View and control a PC or phone that's already running the "
                'matching Linka agent role. This app only ever connects OUT '
                'to something else - it never captures or controls this '
                'device.',
                style: LinkaTheme.body,
              ),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: () => _openLaptop(context),
                child: const Text('LINK TO PC'),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: () => _openPhone(context),
                child: const Text('LINK TO PHONE'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Decides between the pairing screen and the live control screen based on
  /// whether a token is already saved - mirrors LaptopControlContainerView /
  /// PhoneControlContainerView on iOS and the top-level
  /// `if (savedToken.isNullOrBlank())` branch in the Android screens.
  Future<void> _openLaptop(BuildContext context) async {
    final saved = await TokenStore.laptopToken;
    if (!context.mounted) return;
    if (saved != null && saved.isNotEmpty) {
      Navigator.of(context).push(_laptopControlRoute(context, saved));
      return;
    }
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PairingSetupScreen(
        icon: '🖥',
        headline: 'Not paired yet',
        bodyText: 'Run the Linka Windows agent on the target machine. It '
            'prints a pairing code - enter it below or scan its QR code.',
        buttonLabel: 'PAIR',
        onPaired: (code) async {
          await TokenStore.setLaptopToken(code);
          if (!context.mounted) return;
          Navigator.of(context).pushReplacement(_laptopControlRoute(context, code));
        },
      ),
    ));
  }

  Future<void> _openPhone(BuildContext context) async {
    final saved = await TokenStore.phoneToken;
    if (!context.mounted) return;
    if (saved != null && saved.isNotEmpty) {
      Navigator.of(context).push(_phoneControlRoute(context, saved));
      return;
    }
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PairingSetupScreen(
        icon: '📱',
        headline: 'Not linked yet',
        bodyText: 'On the target phone, open Linka, pick Controllee, and '
            'enter the pairing code it shows - or scan its QR code.',
        buttonLabel: 'LINK',
        onPaired: (code) async {
          await TokenStore.setPhoneToken(code);
          if (!context.mounted) return;
          Navigator.of(context).pushReplacement(_phoneControlRoute(context, code));
        },
      ),
    ));
  }

  MaterialPageRoute _laptopControlRoute(BuildContext context, String token) {
    return MaterialPageRoute(
      builder: (_) => ControlScreen(
        mode: ControlMode.laptop,
        url: '$_backendBase/laptop/ws/phone/$token',
        title: 'PC',
        unpairLabel: 'UNPAIR',
        onUnpair: () async {
          await TokenStore.forgetLaptopToken();
          if (context.mounted) Navigator.of(context).popUntil((r) => r.isFirst);
        },
      ),
    );
  }

  MaterialPageRoute _phoneControlRoute(BuildContext context, String token) {
    return MaterialPageRoute(
      builder: (_) => ControlScreen(
        mode: ControlMode.phone,
        url: '$_backendBase/phone/ws/controller/$token',
        title: 'Phone',
        unpairLabel: 'UNLINK',
        onUnpair: () async {
          await TokenStore.forgetPhoneToken();
          if (context.mounted) Navigator.of(context).popUntil((r) => r.isFirst);
        },
      ),
    );
  }
}
