import SwiftUI

@main
struct JoolsApp: App {
    @StateObject private var prefs = AppPreferences.shared
    @StateObject private var logger = AppLogManager.shared

    var body: some Scene {
        WindowGroup {
            MainTabView(prefs: prefs)
                .environmentObject(logger)
        }
    }
}
