import SwiftUI
import ControlyShared

// Hosts the single Compose Multiplatform screen (`MainViewController()`,
// defined in `ios/src/iosMain/kotlin/.../MainViewController.kt`) inside
// SwiftUI — mirrors how `MainActivity.kt` hosts `HomeControlNavHost()` via
// `setContent {}` on Android. `ControlyShared` is the Kotlin/Native
// framework's `baseName` set in `ios/build.gradle.kts`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}
