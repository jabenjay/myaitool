import SwiftUI

enum JoolsRoute: Hashable {
    case tool(JoolsToolId)
    case browserWithUrl(String)
    case proxy
    case about
    case logs
}

struct MainTabView: View {
    @ObservedObject var prefs: AppPreferences
    @StateObject private var proxyManager = ProxyManager.shared

    @State private var selectedTab: Int = 0
    @State private var path: [JoolsRoute] = []
    @State private var pendingNotices: [PendingNoticeItem] = []
    @State private var startupUpdate: UpdateCheckResult?

    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                TabView(selection: $selectedTab) {
                    ToolboxHomeView(
                        prefs: prefs,
                        onOpenTool: { tool in
                            path.append(.tool(tool))
                        },
                        onOpenScanner: {
                            path.append(.tool(.qrScanner))
                        }
                    )
                    .tabItem {
                        Label("工具箱", systemImage: "square.grid.2x2.fill")
                    }
                    .tag(0)

                    SettingsView(
                        prefs: prefs,
                        onOpenProxy: { path.append(.proxy) },
                        onOpenAbout: { path.append(.about) }
                    )
                    .tabItem {
                        Label("设置", systemImage: "gearshape.fill")
                    }
                    .tag(1)
                }
                .navigationDestination(for: JoolsRoute.self) { route in
                    switch route {
                    case .tool(let tool):
                        switch tool {
                        case .base64: Base64ToolView(prefs: prefs)
                        case .formatFactory: FormatFactoryView(prefs: prefs)
                        case .fileCompressor: FileCompressorView(prefs: prefs)
                        case .qrGenerator: QrToolView(prefs: prefs)
                        case .qrScanner: QrScannerView { url in path.append(.browserWithUrl(url)) }
                        case .browser: BrowserView(prefs: prefs, initialUrl: "")
                        case .pushGit: PushGitView(prefs: prefs)
                        }
                    case .browserWithUrl(let u):
                        BrowserView(prefs: prefs, initialUrl: u)
                    case .proxy:
                        ProxySettingsView(prefs: prefs, proxyManager: proxyManager)
                    case .about:
                        AboutView(
                            prefs: prefs,
                            onOpenRepo: { url in path.append(.browserWithUrl(url)) },
                            onOpenLogs: { path.append(.logs) }
                        )
                    case .logs:
                        LogViewerView()
                    }
                }
            }

            if let item = pendingNotices.first {
                NoticeConfirmSheet(item: item) {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.8)) {
                        RemoteNoticeManager.shared.markAcknowledged(item)
                        if !pendingNotices.isEmpty { pendingNotices.removeFirst() }
                    }
                }
                .transition(.opacity.combined(with: .scale(scale: 0.95)))
                .zIndex(100)
            }
        }
        .preferredColorScheme(prefs.themeMode.colorScheme)
        .task {
            let notices = await RemoteNoticeManager.shared.checkPendingNotices()
            if !notices.isEmpty {
                withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                    pendingNotices = notices
                }
            }
            let upd = await AppUpdateChecker.checkLatestRelease()
            if case .updateAvailable = upd { startupUpdate = upd }
        }
        .sheet(item: $startupUpdate) { upd in
            if case let .updateAvailable(cur, lat, name, notes, dl) = upd {
                UpdateSheet(current: cur, latest: lat, name: name, notes: notes, downloadUrl: dl)
            }
        }
    }
}
