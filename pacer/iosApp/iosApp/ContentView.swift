import SwiftUI
import PacerShared

/// The Compose app, as a SwiftUI view.
///
/// Everything Pacer is lives behind `MainViewController()`; this is only what hosts it. The safe
/// areas are ignored deliberately, so the sheet of paper runs to the edges of the screen the way
/// `enableEdgeToEdge()` makes it on Android — Compose reads the insets itself and lays the content
/// out inside them.
struct ComposeView: UIViewControllerRepresentable {
    let launchUrl: String?

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(launchUrl: launchUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    /// The url the app was opened with, if any.
    ///
    /// SwiftUI delivers `onOpenURL` after the view has already appeared, so the id is what makes a
    /// link take effect: it builds the Compose controller again with the url in hand. Doing so
    /// discards whatever was on screen, which is what a deep link asks for anyway.
    @State private var launchUrl: String?

    var body: some View {
        ComposeView(launchUrl: launchUrl)
            .ignoresSafeArea(.all)
            .id(launchUrl)
            .onOpenURL { url in
                launchUrl = url.absoluteString
            }
    }
}
