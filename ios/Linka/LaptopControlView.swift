import SwiftUI

/// Mirrors LaptopControlScreen.kt's LiveControl - live frame display + gesture-to-
/// mouse-command translation + on-screen key buttons + a diffed text field that sends
/// only the actual keystroke delta (matches the Android version's diffing exactly).
/// Pinch-to-zoom/pan and hold-to-right-click (both present in the Android version) are
/// intentionally left out of this first pass - right-click needs SwiftUI's
/// LongPressGesture+DragGesture composition to recover a tap location, which is more
/// unverified gesture-code surface for a secondary feature. Left-click + move-by-drag
/// covers the primary use case without it.
struct LaptopControlView: View {
    let token: String
    let onForget: () -> Void

    @StateObject private var viewModel: ControlSocketViewModel
    @State private var typedText: String = ""
    @State private var previousTypedText: String = ""

    init(token: String, onForget: @escaping () -> Void) {
        self.token = token
        self.onForget = onForget
        _viewModel = StateObject(wrappedValue: ControlSocketViewModel(
            url: "wss://elene-backend-717899371194.us-central1.run.app/laptop/ws/phone/\(token)"
        ))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(statusText)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(statusColor)
                Spacer()
                Button("UNPAIR", action: onForget)
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
                                        let n = normalized(value.location, in: geo.size, imageSize: frame.size)
                                        let distance = hypot(value.translation.width, value.translation.height)
                                        if distance > 4 {
                                            viewModel.sendMouseMove(x: n.x, y: n.y)
                                        } else {
                                            viewModel.sendMouseClick(x: n.x, y: n.y, button: "left")
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
                    Button("▲") { viewModel.sendScroll(dy: 40) }
                    Button("▼") { viewModel.sendScroll(dy: -40) }
                    Button("ENTER") { viewModel.sendKeyPress(key: "enter") }
                    Button("TAB") { viewModel.sendKeyPress(key: "tab") }
                    Button("ESC") { viewModel.sendKeyPress(key: "esc") }
                    Button("⌫") { viewModel.sendKeyPress(key: "backspace") }
                }
                .font(.system(size: 11, design: .monospaced))
                .buttonStyle(.bordered)

                TextField("Type to send keystrokes", text: $typedText)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 12, design: .monospaced))
                    .onChange(of: typedText) { newValue in
                        applyTextDiff(old: previousTypedText, new: newValue)
                        previousTypedText = newValue
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
        case .waitingForAgent: return "Waiting for PC agent..."
        case .connected: return "Connected"
        }
    }

    private var statusColor: Color {
        viewModel.connState == .connected ? Theme.color : .gray
    }

    /// Same diff logic as the Android version: only the actual added/removed
    /// characters get sent, not the whole field on every keystroke.
    private func applyTextDiff(old: String, new: String) {
        if new.count > old.count && new.hasPrefix(old) {
            viewModel.sendKeyText(text: String(new.dropFirst(old.count)))
        } else if new.count < old.count && old.hasPrefix(new) {
            for _ in 0..<(old.count - new.count) { viewModel.sendKeyPress(key: "backspace") }
        } else if new != old {
            for _ in 0..<old.count { viewModel.sendKeyPress(key: "backspace") }
            if !new.isEmpty { viewModel.sendKeyText(text: new) }
        }
    }
}

/// Normalized [0,1] position of `point` within an aspect-fit image displayed in a
/// `containerSize` box - the letterbox math behind Android's videoRect/
/// controlVideoRect, without the zoom/pan terms (not implemented this pass).
func normalized(_ point: CGPoint, in containerSize: CGSize, imageSize: CGSize) -> (x: Double, y: Double) {
    guard containerSize.width > 0, containerSize.height > 0,
          imageSize.width > 0, imageSize.height > 0 else { return (0, 0) }

    let containerAspect = containerSize.width / containerSize.height
    let imageAspect = imageSize.width / imageSize.height
    let displayWidth: CGFloat
    let displayHeight: CGFloat
    if containerAspect > imageAspect {
        displayHeight = containerSize.height
        displayWidth = displayHeight * imageAspect
    } else {
        displayWidth = containerSize.width
        displayHeight = displayWidth / imageAspect
    }
    let originX = (containerSize.width - displayWidth) / 2
    let originY = (containerSize.height - displayHeight) / 2
    let nx = min(max((point.x - originX) / displayWidth, 0), 1)
    let ny = min(max((point.y - originY) / displayHeight, 0), 1)
    return (Double(nx), Double(ny))
}
