import Foundation
import SwiftUI
import WebKit

enum ThemePreference: String, CaseIterable, Codable {
    case system = "跟随系统"
    case light = "浅色模式"
    case dark = "深色模式"
}

enum ToolRoute: String, CaseIterable, Identifiable, Codable {
    case qrScanner = "qr_scanner"
    case browser = "browser"
    case base64 = "base64"
    case qrTool = "qr_tool"
    case formatFactory = "format_factory"
    case compressor = "compressor"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .qrScanner: return "扫描二维码"
        case .browser: return "轻量浏览器"
        case .base64: return "Base64编码"
        case .qrTool: return "二维码工具"
        case .formatFactory: return "格式工厂"
        case .compressor: return "文件压缩"
        }
    }

    var subtitle: String {
        switch self {
        case .qrScanner: return "极速扫码与智能识别跳转"
        case .browser: return "内置广告屏蔽与悬浮播放器"
        case .base64: return "文本与文件 Base64 编解码"
        case .qrTool: return "自定义样式生成与保存"
        case .formatFactory: return "图片格式转换与尺寸缩放"
        case .compressor: return "图片/视频体积无损或有损压缩"
        }
    }

    var iconName: String {
        switch self {
        case .qrScanner: return "qrcode.viewfinder"
        case .browser: return "safari"
        case .base64: return "chevron.left.forwardslash.chevron.right"
        case .qrTool: return "qrcode"
        case .formatFactory: return "wand.and.stars"
        case .compressor: return "archivebox"
        }
    }
}

struct BookmarkItem: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var url: String
}

struct AdBlockSubscription: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var url: String
    var fallbackUrl: String?
    var enabled: Bool
    var ruleCount: Int
    var lastUpdatedMs: Double
    var isBuiltIn: Bool
}

struct DownloadTaskItem: Identifiable, Equatable {
    let id: String
    var fileName: String
    var url: String
    var progress: Double
    var statusText: String
    var isCompleted: Bool
    var localURL: URL?
}

struct UpdateInfo: Identifiable {
    let id = UUID()
    let version: String
    let changelog: String
    let downloadUrl: String
}

final class AppLogManager {
    static let shared = AppLogManager()
    private var entries: [String] = []
    private let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    func log(_ tag: String, _ message: String) {
        let line = "[\(formatter.string(from: Date()))] [\(tag)] \(message)"
        DispatchQueue.main.async {
            self.entries.append(line)
            if self.entries.count > 300 {
                self.entries.removeFirst(self.entries.count - 300)
            }
        }
    }

    func allLogs() -> [String] { entries }
    func clear() { entries.removeAll() }
}

@MainActor
final class AppState: ObservableObject {
    @Published var themePreference: ThemePreference = .system {
        didSet { UserDefaults.standard.set(themePreference.rawValue, forKey: "jools_theme") }
    }
    @Published var toolOrder: [ToolRoute] = ToolRoute.allCases {
        didSet {
            if let data = try? JSONEncoder().encode(toolOrder) {
                UserDefaults.standard.set(data, forKey: "jools_tool_order")
            }
        }
    }
    @Published var bookmarks: [BookmarkItem] = [] {
        didSet {
            if let data = try? JSONEncoder().encode(bookmarks) {
                UserDefaults.standard.set(data, forKey: "jools_bookmarks")
            }
        }
    }
    @Published var homeUrl: String = "https://biglist03.xyz/" {
        didSet { UserDefaults.standard.set(homeUrl, forKey: "jools_home_url") }
    }

    @Published var activeUpdateInfo: UpdateInfo?
    @Published var showUpdateSheet: Bool = false
    @Published var downloads: [DownloadTaskItem] = []

    var preferredColorScheme: ColorScheme? {
        switch themePreference {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var currentAppVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    init() {
        if let rawTheme = UserDefaults.standard.string(forKey: "jools_theme"),
           let parsed = ThemePreference(rawValue: rawTheme) {
            themePreference = parsed
        }
        if let data = UserDefaults.standard.data(forKey: "jools_tool_order"),
           let savedOrder = try? JSONDecoder().decode([ToolRoute].self, from: data),
           !savedOrder.isEmpty {
            let missing = ToolRoute.allCases.filter { !savedOrder.contains($0) }
            toolOrder = savedOrder + missing
        }
        if let savedHome = UserDefaults.standard.string(forKey: "jools_home_url"), !savedHome.isEmpty {
            homeUrl = savedHome
        }
        if let data = UserDefaults.standard.data(forKey: "jools_bookmarks"),
           let savedBm = try? JSONDecoder().decode([BookmarkItem].self, from: data),
           !savedBm.isEmpty {
            bookmarks = savedBm
        } else {
            bookmarks = Self.defaultBookmarks
        }
    }

    static let defaultBookmarks: [BookmarkItem] = [
        BookmarkItem(id: "bm_1", name: "文尼导航", url: "https://biglist03.xyz/"),
        BookmarkItem(id: "bm_2", name: "59导航", url: "https://dh852.cc/?ide=pl2vfit1"),
        BookmarkItem(id: "bm_3", name: "海角57", url: "https://hjtv57.top/?lde=3k8pwlo0"),
        BookmarkItem(id: "bm_4", name: "萝莉岛", url: "https://loli88.top/?lde=7veeox3p"),
        BookmarkItem(id: "bm_5", name: "大门导航", url: "https://vbc04o.csmen1056.cc/%E5%A4%A7%E9%97%A8/1b956a/#shipin"),
        BookmarkItem(id: "bm_6", name: "2025秒播", url: "https://52chengrer.zxmbsp6.cc/2025/"),
        BookmarkItem(id: "bm_7", name: "搜片网", url: "https://52chengren.insearch10.cc/vip/369/")
    ]

    func checkUpdatesOnLaunch() async {
        if let info = await fetchLatestRelease() {
            activeUpdateInfo = info
            showUpdateSheet = true
        }
    }

    func checkUpdatesManually() async -> UpdateInfo? {
        let info = await fetchLatestRelease()
        if let info {
            activeUpdateInfo = info
            showUpdateSheet = true
        }
        return info
    }

    private func fetchLatestRelease() async -> UpdateInfo? {
        guard let url = URL(string: "https://api.github.com/repos/jabenjay/myaitool/releases/latest") else { return nil }
        var req = URLRequest(url: url, timeoutInterval: 10)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode),
                  let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let tag = json["tag_name"] as? String else { return nil }
            let cleanRemote = tag.trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
            guard compareVersion(cleanRemote, currentAppVersion) > 0 else { return nil }

            let rawBody = (json["body"] as? String ?? "").replacingOccurrences(of: "**", with: "")
            let filteredBody = rawBody.components(separatedBy: "\n")
                .filter { !$0.contains("Full Changelog") }
                .map { line -> String in
                    if line.localizedCaseInsensitiveContains("What's Changed") || line.localizedCaseInsensitiveContains("Changelog") {
                        return "# 更新日志"
                    }
                    return line
                }
                .joined(separator: "\n")

            var downloadUrl = json["html_url"] as? String ?? "https://github.com/jabenjay/myaitool/releases/latest"
            if let assets = json["assets"] as? [[String: Any]] {
                if let ipa = assets.first(where: { ($0["name"] as? String ?? "").lowercased().hasSuffix(".ipa") }),
                   let browserUrl = ipa["browser_download_url"] as? String {
                    downloadUrl = browserUrl
                }
            }
            return UpdateInfo(version: tag, changelog: filteredBody, downloadUrl: downloadUrl)
        } catch {
            return nil
        }
    }

    private func compareVersion(_ a: String, _ b: String) -> Int {
        let pa = a.split(separator: ".").compactMap { Int($0) }
        let pb = b.split(separator: ".").compactMap { Int($0) }
        for i in 0..<max(pa.count, pb.count) {
            let va = i < pa.count ? pa[i] : 0
            let vb = i < pb.count ? pb[i] : 0
            if va != vb { return va < vb ? -1 : 1 }
        }
        return 0
    }

    func startDownload(urlString: String, suggestedName: String? = nil) {
        guard let url = URL(string: urlString) else { return }
        let fileName = suggestedName ?? (url.lastPathComponent.isEmpty ? "video_\(Int(Date().timeIntervalSince1970)).mp4" : url.lastPathComponent)
        let id = UUID().uuidString
        let item = DownloadTaskItem(id: id, fileName: fileName, url: urlString, progress: 0, statusText: "正在下载...", isCompleted: false, localURL: nil)
        downloads.insert(item, at: 0)

        Task {
            do {
                let (tempURL, _) = try await URLSession.shared.download(from: url)
                let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
                let dlDir = docs.appendingPathComponent("JoolsDownloads", isDirectory: true)
                try? FileManager.default.createDirectory(at: dlDir, withIntermediateDirectories: true)
                let destURL = dlDir.appendingPathComponent(fileName)
                if FileManager.default.fileExists(atPath: destURL.path) {
                    try? FileManager.default.removeItem(at: destURL)
                }
                try FileManager.default.moveItem(at: tempURL, to: destURL)
                if let idx = downloads.firstIndex(where: { $0.id == id }) {
                    downloads[idx].progress = 1.0
                    downloads[idx].statusText = "已完成 · 文件/JoolsDownloads"
                    downloads[idx].isCompleted = true
                    downloads[idx].localURL = destURL
                }
            } catch {
                if let idx = downloads.firstIndex(where: { $0.id == id }) {
                    downloads[idx].statusText = "失败: \(error.localizedDescription)"
                }
            }
        }
    }
}

@MainActor
final class AdBlockManager: ObservableObject {
    @Published var enabled: Bool {
        didSet { UserDefaults.standard.set(enabled, forKey: "jools_ios_adblock_enabled") }
    }
    @Published var subscriptions: [AdBlockSubscription] = []
    @Published var customDomains: [String] = []
    @Published var totalRuleCount: Int = 0
    @Published var compiledRuleList: WKContentRuleList?

    private let cacheDir: URL = {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("AdBlock", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()

    static let defaultSub = AdBlockSubscription(
        id: "awavenue_default",
        name: "AWAvenue 秋风广告规则",
        url: "https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/AWAvenue-Ads-Rule.txt",
        fallbackUrl: "https://cdn.jsdelivr.net/gh/TG-Twilight/AWAvenue-Ads-Rule@main/AWAvenue-Ads-Rule.txt",
        enabled: true,
        ruleCount: 0,
        lastUpdatedMs: 0,
        isBuiltIn: true
    )

    static let coreFallbackDomains: [String] = [
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "pos.baidu.com", "cpro.baidu.com", "eclick.baidu.com", "cbjs.baidu.com",
        "hm.baidu.com", "union.sogou.com", "tanx.com", "alimama.com", "mmstat.com",
        "cnzz.com", "umeng.com", "51.la", "popads.net", "popcash.net",
        "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.com", "tsyndicate.com", "realsrv.com", "clickadu.com",
        "hilltopads.net", "monetag.com", "taboola.com", "outbrain.com",
        "criteo.com", "adnxs.com", "pubmatic.com", "rubiconproject.com", "openx.net"
    ]

    init() {
        if UserDefaults.standard.object(forKey: "jools_ios_adblock_enabled") == nil {
            self.enabled = true
        } else {
            self.enabled = UserDefaults.standard.bool(forKey: "jools_ios_adblock_enabled")
        }

        if let data = UserDefaults.standard.data(forKey: "jools_ios_adblock_subs"),
           let saved = try? JSONDecoder().decode([AdBlockSubscription].self, from: data),
           !saved.isEmpty {
            self.subscriptions = saved
        } else {
            self.subscriptions = [Self.defaultSub]
        }

        if let data = UserDefaults.standard.data(forKey: "jools_ios_adblock_custom"),
           let savedCustom = try? JSONDecoder().decode([String].self, from: data) {
            self.customDomains = savedCustom
        }
        Task { await recompileContentRuleList() }
    }

    private func persistSubs() {
        if let data = try? JSONEncoder().encode(subscriptions) {
            UserDefaults.standard.set(data, forKey: "jools_ios_adblock_subs")
        }
    }

    private func persistCustom() {
        if let data = try? JSONEncoder().encode(customDomains) {
            UserDefaults.standard.set(data, forKey: "jools_ios_adblock_custom")
        }
    }

    func addSubscription(name: String, url: String) {
        let cleanUrl = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanUrl.isEmpty, !subscriptions.contains(where: { $0.url.caseInsensitiveCompare(cleanUrl) == .orderedSame }) else { return }
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let sub = AdBlockSubscription(
            id: "sub_\(Int(Date().timeIntervalSince1970))",
            name: cleanName.isEmpty ? "自定义订阅" : cleanName,
            url: cleanUrl,
            fallbackUrl: nil,
            enabled: true,
            ruleCount: 0,
            lastUpdatedMs: 0,
            isBuiltIn: false
        )
        subscriptions.append(sub)
        persistSubs()
        Task { await refreshSubscription(id: sub.id) }
    }

    func toggleSubscription(id: String, enabled: Bool) {
        if let idx = subscriptions.firstIndex(where: { $0.id == id }) {
            subscriptions[idx].enabled = enabled
            persistSubs()
            Task { await recompileContentRuleList() }
        }
    }

    func removeSubscription(id: String) {
        subscriptions.removeAll { $0.id == id && !$0.isBuiltIn }
        try? FileManager.default.removeItem(at: cacheDir.appendingPathComponent("\(id).txt"))
        persistSubs()
        Task { await recompileContentRuleList() }
    }

    func addCustomDomain(_ raw: String) {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "||", with: "")
            .replacingOccurrences(of: "https://", with: "")
            .replacingOccurrences(of: "http://", with: "")
            .replacingOccurrences(of: "^", with: "")
            .lowercased()
        guard !clean.isEmpty, !customDomains.contains(clean) else { return }
        customDomains.insert(clean, at: 0)
        persistCustom()
        Task { await recompileContentRuleList() }
    }

    func removeCustomDomain(_ domain: String) {
        customDomains.removeAll { $0.caseInsensitiveCompare(domain) == .orderedSame }
        persistCustom()
        Task { await recompileContentRuleList() }
    }

    func refreshAllEnabled() async -> Int {
        var count = 0
        for sub in subscriptions where sub.enabled {
            if await refreshSubscription(id: sub.id) { count += 1 }
        }
        return count
    }

    @discardableResult
    func refreshSubscription(id: String) async -> Bool {
        guard let idx = subscriptions.firstIndex(where: { $0.id == id }) else { return false }
        let sub = subscriptions[idx]
        let candidates = [sub.url, sub.fallbackUrl].compactMap { $0 }

        var fetchedText: String?
        for urlStr in candidates {
            guard let url = URL(string: urlStr) else { continue }
            var req = URLRequest(url: url, timeoutInterval: 15)
            req.setValue("Jools-AdBlock/1.0", forHTTPHeaderField: "User-Agent")
            if let (data, resp) = try? await URLSession.shared.data(for: req),
               let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode),
               let text = String(data: data, encoding: .utf8), !text.isEmpty {
                fetchedText = text
                break
            }
        }
        guard let text = fetchedText else { return false }
        let fileURL = cacheDir.appendingPathComponent("\(sub.id).txt")
        try? text.write(to: fileURL, atomically: true, encoding: .utf8)
        let domains = parseDomains(from: text)
        subscriptions[idx].ruleCount = domains.count
        subscriptions[idx].lastUpdatedMs = Date().timeIntervalSince1970 * 1000
        persistSubs()
        await recompileContentRuleList()
        return true
    }

    func recompileContentRuleList() async {
        var allDomains = Set(Self.coreFallbackDomains)
        for sub in subscriptions where sub.enabled {
            let fileURL = cacheDir.appendingPathComponent("\(sub.id).txt")
            if let text = try? String(contentsOf: fileURL, encoding: .utf8) {
                allDomains.formUnion(parseDomains(from: text))
            }
        }
        for d in customDomains { allDomains.insert(d.lowercased()) }
        totalRuleCount = allDomains.count

        // WebKit WKContentRuleList supports up to ~50k rules cleanly; chunk domains into regex groups for fast compilation
        let sorted = Array(allDomains).prefix(40000)
        var rulesArray: [[String: Any]] = []
        let chunkSize = 200
        var i = 0
        while i < sorted.count {
            let slice = sorted[i..<min(i + chunkSize, sorted.count)]
            let escaped = slice.map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "|")
            rulesArray.append([
                "trigger": [
                    "url-filter": "^https?://([^/]+\\.)?(\(escaped))(/|:|$)",
                    "load-type": ["third-party"]
                ],
                "action": ["type": "block"]
            ])
            i += chunkSize
        }

        if let jsonData = try? JSONSerialization.data(withJSONObject: rulesArray),
           let jsonStr = String(data: jsonData, encoding: .utf8) {
            WKContentRuleListStore.default().compileContentRuleList(
                forIdentifier: "JoolsAdBlockRules",
                encodedContentRuleList: jsonStr
            ) { [weak self] list, _ in
                Task { @MainActor in
                    self?.compiledRuleList = list
                }
            }
        }
    }

    private func parseDomains(from text: String) -> [String] {
        var result: [String] = []
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty || line.hasPrefix("!") || line.hasPrefix("#") || line.hasPrefix("[") || line.hasPrefix("@@") || line.contains("##") {
                continue
            }
            var candidate = line
            if candidate.hasPrefix("0.0.0.0 ") || candidate.hasPrefix("127.0.0.1 ") {
                candidate = String(candidate.split(separator: " ").dropFirst().first ?? "")
            } else if candidate.hasPrefix("||") {
                candidate = String(candidate.dropFirst(2))
            }
            candidate = candidate.components(separatedBy: "$").first?.components(separatedBy: "^").first?.lowercased() ?? ""
            if candidate.count >= 4, candidate.contains("."), !candidate.contains("/"), !candidate.contains("*"), !candidate.contains(" ") {
                result.append(candidate)
            }
        }
        return result
    }
}
