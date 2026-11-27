import SwiftUI
import AVFoundation

@main
struct JoolsApp: App {
    @StateObject private var appState = AppState()

    init() {
        configureAudioSession()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .preferredColorScheme(appState.preferredColorScheme)
                .task {
                    await appState.checkUpdatesOnLaunch()
                }
        }
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback, options: [])
        try? session.setActive(true)
    }
}
