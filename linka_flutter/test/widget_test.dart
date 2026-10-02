import 'package:flutter_test/flutter_test.dart';

import 'package:linka/main.dart';

void main() {
  testWidgets('Home screen shows LINKA and both link buttons', (WidgetTester tester) async {
    await tester.pumpWidget(const LinkaApp());

    expect(find.text('LINKA'), findsOneWidget);
    expect(find.text('LINK TO PC'), findsOneWidget);
    expect(find.text('LINK TO PHONE'), findsOneWidget);
  });
}
