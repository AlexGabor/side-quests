import SwiftUI
import PacerShared

/// The Compose app, as a SwiftUI view.
///
/// Everything Pacer is lives behind `MainViewController()`; this is only what hosts it. The safe
/// areas are ignored deliberately, so the sheet of paper runs to the edges of the screen the way
/// `enableEdgeToEdge()` makes it on Android — Compose reads the insets itself and lays the content
/// out inside them.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
