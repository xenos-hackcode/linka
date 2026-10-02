import SwiftUI

/// Mirrors PhoneControlScreen.kt's PhoneLiveControl - tap sends `tap`, drag sends
/// `swipe`, typing sends the whole current text on every change (not diffed - matches
/// the Android version's simpler behavior on this screen specifically, unlike the
/// laptop screen's diffed keystrokes). Pinch-to-zoom/pan left out, same call as
/// LaptopControlView.
struct PhoneControlView: View {
    let token: String
    let onForget: () -> Void

    @StateObject private var viewModel: ControlSocketViewModel
    @State private var typedText: String = ""

    init(token: String, onForget: @escaping () -> Void) {
        self.token = token
        self.onForget = onForget
        _viewModel = StateObject(wrappedValue: ControlSocketViewModel(
            url: "wss://elene-backend-717899371194.us-central1.run.app/phone/ws/controller/\(token)"
        ))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(statusText)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(statusColor)
                Spacer()
                Button("UNLINK", action: onForget)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.gray)
            }
            .padding(8)
            .background(Color.black.opacity(0.45))

            GeometryReader { geo in
                ZStack {
                    Color.black
                    if let frame = viewModel.latestFrame {
                        Image(uiImage: frame)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .contentShape(Rectangle())
                            .gesture(
                                DragGesture(minimumDistance: 0)
                                    .onEnded { value in
                                        let start = normalized(value.startLocation, in: geo.size, imageSize: frame.size)
                                        let end = normalized(value.location, in: geo.size, imageSize: frame.size)
                                        let distance = hypot(value.translation.width, value.translation.height)
                                        if distance > 8 {
                                            viewModel.sendSwipe(x1: start.x, y1: start.y, x2: end.x, y2: end.y, durationMs: 250)
                                        } else {
                                            viewModel.sendTap(x: end.x, y: end.y)
                                        }
                                    }
                            )
                    } else {
                        Text("No image yet")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundColor(.gray)
                    }
                }
            }

            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    Button("◀ BACK") { viewModel.sendKeyPress(key: "back") }
                    Button("⌂ HOME") { viewModel.sendKeyPress(key: "home") }
                    Button("▢ RECENTS") { viewModel.sendKeyPress(key: "recents") }
                }
                .font(.system(size: 11, design: .monospaced))
                .buttonStyle(.bordered)

                TextField("Type to send text", text: $typedText)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 12, design: .monospaced))
                    .onChange(of: typedText) { newValue in
                        viewModel.sendKeyText(text: newValue)
                    }
            }
            .padding(10)
            .background(Color.black.opacity(0.55))
        }
        .background(Color.black)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { viewModel.connect() }
        .onDisappear { viewModel.disconnect() }
    }

    private var statusText: String {
        switch viewModel.connState {
        case .disconnected: return "Disconnected"
        case .connecting: return "Connecting..."
        case .waitingForAgent: return "Waiting for the other phone..."
        case .connected: return "Connected"
        }
    }

    private var statusColor: Color {
        viewModel.connState == .connected ? Theme.color : .gray
    }
}
