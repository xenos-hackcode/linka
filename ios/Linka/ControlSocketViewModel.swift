import Foundation
import UIKit
import LinkaCore

/// Mirrors LaptopConnState/PhoneControlConnState from the Android app - same four
/// states, same meaning, just spelled in Swift.
enum ConnState {
    case disconnected
    case connecting
    case waitingForAgent
    case connected
}

/// Wraps IosControlSocketClient for either the laptop or phone control route - shared
/// by LaptopControlView and PhoneControlView, same as both Android screens share the
/// same shape of client (LaptopControlClient/PhoneControlClient) even though they
/// don't share a base class there either.
@MainActor
final class ControlSocketViewModel: ObservableObject {
    @Published var connState: ConnState = .connecting
    @Published var latestFrame: UIImage?

    private let client: IosControlSocketClient

    init(url: String) {
        client = IosControlSocketClient(url: url)

        client.onConnectionStateChanged = { [weak self] state in
            DispatchQueue.main.async {
                guard let self else { return }
                // NOTE: SocketConnState case names below (.connecting/.open/.closed)
                // are this project's single biggest unverified guess about
                // Kotlin/Native's Swift enum-case naming - if this file fails to
                // compile on first Mac build, check the actual generated case names
                // here first (Xcode's autocomplete on `state.` will show them).
                switch state {
                case .connecting:
                    self.connState = .connecting
                case .open:
                    self.connState = .waitingForAgent
                case .closed:
                    self.connState = .disconnected
                default:
                    break
                }
            }
        }
        client.onAgentConnected = { [weak self] in
            DispatchQueue.main.async { self?.connState = .connected }
        }
        client.onAgentDisconnected = { [weak self] in
            DispatchQueue.main.async {
                guard let self, self.connState != .disconnected else { return }
                self.connState = .waitingForAgent
            }
        }
        client.onFrame = { [weak self] bytes in
            let data = bytes.toData()
            DispatchQueue.main.async {
                self?.latestFrame = UIImage(data: data)
            }
        }
    }

    func connect() {
        connState = .connecting
        client.connect()
    }

    func disconnect() {
        client.disconnect()
    }

    func sendTap(x: Double, y: Double) { client.sendTap(x: x, y: y) }

    func sendSwipe(x1: Double, y1: Double, x2: Double, y2: Double, durationMs: Int64) {
        client.sendSwipe(x1: x1, y1: y1, x2: x2, y2: y2, durationMs: durationMs)
    }

    func sendMouseMove(x: Double, y: Double) { client.sendMouseMove(x: x, y: y) }

    func sendMouseClick(x: Double, y: Double, button: String) {
        client.sendMouseClick(x: x, y: y, button: button)
    }

    func sendScroll(dy: Int32) { client.sendScroll(dy: dy) }

    func sendKeyText(text: String) { client.sendKeyText(text: text) }

    func sendKeyPress(key: String) { client.sendKeyPress(key: key) }
}
