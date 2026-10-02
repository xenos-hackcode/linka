import SwiftUI

/// Shared pairing-code entry UI - mirrors the PairingSetup/PhonePairingSetup
/// composables in the Android app, which are near-identical apart from copy text.
struct PairingSetupView: View {
    let icon: String
    let headline: String
    let bodyText: String
    let buttonLabel: String
    let onPaired: (String) -> Void

    @State private var input: String = ""
    @State private var showScanner = false

    var body: some View {
        VStack(spacing: 12) {
            Text(icon)
                .font(.system(size: 40, design: .monospaced))
                .foregroundColor(Theme.color)
            Text(headline)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
            Text(bodyText)
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)

            TextField("Pairing code", text: $input)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .onChange(of: input) { newValue in
                    input = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
                }

            Button("SCAN QR INSTEAD") { showScanner = true }
                .buttonStyle(.bordered)

            Button(buttonLabel) {
                if input.count >= 16 { onPaired(input) }
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.color)
            .disabled(input.count < 16)
        }
        .padding(20)
        .sheet(isPresented: $showScanner) {
            QRScannerView { scanned in
                input = scanned.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
    }
}
