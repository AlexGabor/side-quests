import SwiftUI
import BookplateShared

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
