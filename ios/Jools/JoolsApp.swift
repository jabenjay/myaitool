import SwiftUI
import UIKit
import UserNotifications

final class OrientationManager {
    static let shared = OrientationManager()
    var mask: UIInterfaceOrientationMask = .allButUpsideDown

    func apply(mask: UIInterfaceOrientationMask, preferred: UIInterfaceOrientationMask? = nil) {
        self.mask = mask
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            for window in scene.windows {
                window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
            if let target = preferred {
                scene.requestGeometryUpdate(.iOS(interfaceOrientations: target)) { _ in }
            }
        }
    }
}

final class JoolsAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        OrientationManager.shared.mask
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct JoolsApp: App {
    @UIApplicationDelegateAdaptor(JoolsAppDelegate.self) var appDelegate
    @StateObject private var prefs = AppPreferences.shared
    @StateObject private var logger = AppLogManager.shared

    var body: some Scene {
        WindowGroup {
            MainTabView(prefs: prefs)
                .environmentObject(logger)
        }
    }
}
