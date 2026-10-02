import SwiftUI

/// Root screen - mirrors ControllerHomeScreen.kt. No role picker on iOS yet since only
/// the Controller role exists here for now (see PLANNING.md §5) - this is the app's
/// actual entry screen, not nested under anything.
struct ControllerHomeView: View {
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("LINKA")
                    .font(.system(size: 20, weight: .bold, design: .monospaced))
                    .foregroundColor(Theme.color)
                Text("View and control a PC or phone that's already running the matching " +
                     "Linka agent role. This app only ever connects OUT to something " +
                     "else - it never captures or controls this device.")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.gray)

                NavigationLink("LINK TO PC") { LaptopControlContainerView() }
                    .buttonStyle(.bordered)
                NavigationLink("LINK TO PHONE") { PhoneControlContainerView() }
                    .buttonStyle(.bordered)

                Spacer()
            }
            .padding(20)
        }
    }
}

/// Decides between the pairing screen and the live control screen based on whether a
/// laptop token is already saved - mirrors the top-level `if (savedToken.isNullOrBlank())`
/// branch in LaptopControlScreen.kt.
struct LaptopControlContainerView: View {
    @State private var token: String? = TokenStore.laptopToken

    var body: some View {
        Group {
            if let token {
                LaptopControlView(token: token) {
                    TokenStore.forgetLaptopToken()
                    self.token = nil
                }
            } else {
                PairingSetupView(
                    icon: "🖥",
                    headline: "Not paired yet",
                    bodyText: "Run the Linka Windows agent on the target machine. It " +
                        "prints a pairing code - enter it below or scan its QR code.",
                    buttonLabel: "PAIR"
                ) { code in
                    TokenStore.laptopToken = code
                    self.token = code
                }
            }
        }
    }
}

struct PhoneControlContainerView: View {
    @State private var token: String? = TokenStore.phoneToken

    var body: some View {
        Group {
            if let token {
                PhoneControlView(token: token) {
                    TokenStore.forgetPhoneToken()
                    self.token = nil
                }
            } else {
                PairingSetupView(
                    icon: "📱",
                    headline: "Not linked yet",
                    bodyText: "On the target phone, open Linka, pick Controllee, and " +
                        "enter the pairing code it shows - or scan its QR code.",
                    buttonLabel: "LINK"
                ) { code in
                    TokenStore.phoneToken = code
                    self.token = code
                }
            }
        }
    }
}
