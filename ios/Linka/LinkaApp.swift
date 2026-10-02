import SwiftUI

@main
struct LinkaApp: App {
    var body: some Scene {
        WindowGroup {
            ControllerHomeView()
                .preferredColorScheme(.dark)
        }
    }
}
