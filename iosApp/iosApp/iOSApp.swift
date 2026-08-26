import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Inicializa Koin del lado iOS una sola vez, antes de crear la
        // primera vista Compose (Fase 0 — ver MainViewController.kt).
        MainViewControllerKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
