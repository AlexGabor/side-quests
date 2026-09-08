import SwiftUI
import PacerShared

@main
struct iOSApp: App {
    init() {
        KoinKt.setupKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
