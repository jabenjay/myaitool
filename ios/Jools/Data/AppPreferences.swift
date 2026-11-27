import Foundation
import SwiftUI

enum JoolsToolId: String, CaseIterable, Identifiable {
    case browser = "browser"
    case base64 = "base64"
    case qrGenerator = "qr_generator"
    case formatFactory = "format_factory"
    case fileCompressor = "file_compressor"
    case pushGit = "push_git"
    case ipaManager = "ipa_manager"
    case qrScanner = "qr_scanner"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .browser: return "轻量浏览器"
        case .base64: return "Base64编码"
        case .qrGenerator: return "二维码工具"
        case .formatFactory: return "格式工厂"
        case .fileCompressor: return "文件压缩"
        case .pushGit: return "PushGit"
        case .ipaManager: return "IPA管理"
        case .qrScanner: return "扫一扫"
        }
    }

    var subtitle: String {
        switch self {
        case .browser: return "Safari 风格多标签、书签与浮窗播放"
        case .base64: return "文本与文件编解码、长文本定位"
        case .qrGenerator: return "自定义样式生成与相册保存"
        case .formatFactory: return "图片、视频、音频格式转换"
        case .fileCompressor: return "ZIP 与媒体体积压缩"
        case .pushGit: return "推送文件至 GitHub 并触发构建"
        case .ipaManager: return "IPA 分库管理、P12 签名与安装"
        case .qrScanner: return "相机扫码与相册二维码识别"
        }
    }

    var systemIcon: String {
        switch self {
        case .browser: return "safari.fill"
        case .base64: return "chevron.left.forwardslash.chevron.right"
        case .qrGenerator: return "qrcode"
        case .formatFactory: return "arrow.triangle.2.circlepath"
        case .fileCompressor: return "archivebox.fill"
        case .pushGit: return "arrow.up.circle.fill"
        case .ipaManager: return "signature"
        case .qrScanner: return "qrcode.viewfinder"
        }
    }

    var accentColor: Color {
        switch self {
        case .browser: return Color(red: 0.04, green: 0.52, blue: 1.00)
        case .base64: return Color(red: 0.35, green: 0.34, blue: 0.84)
        case .qrGenerator: return Color(red: 0.19, green: 0.69, blue: 0.35)
        case .formatFactory: return Color(red: 0.69, green: 0.32, blue: 0.87)
        case .fileCompressor: return Color(red: 0.96, green: 0.55, blue: 0.14)
        case .pushGit: return Color(red: 0.16, green: 0.64, blue: 0.45)
        case .ipaManager: return Color(red: 0.93, green: 0.27, blue: 0.45)
        case .qrScanner: return Color(red: 0.12, green: 0.68, blue: 0.85)
        }
    }

    var gradientColors: [Color] {
        switch self {
        case .browser: return [Color(red: 0.18, green: 0.62, blue: 1.0), Color(red: 0.02, green: 0.42, blue: 0.95)]
        case .base64: return [Color(red: 0.45, green: 0.44, blue: 0.96), Color(red: 0.28, green: 0.27, blue: 0.78)]
        case .qrGenerator: return [Color(red: 0.25, green: 0.78, blue: 0.45), Color(red: 0.12, green: 0.58, blue: 0.30)]
        case .formatFactory: return [Color(red: 0.78, green: 0.42, blue: 0.95), Color(red: 0.56, green: 0.22, blue: 0.78)]
        case .fileCompressor: return [Color(red: 1.00, green: 0.65, blue: 0.24), Color(red: 0.88, green: 0.45, blue: 0.08)]
        case .pushGit: return [Color(red: 0.22, green: 0.74, blue: 0.52), Color(red: 0.11, green: 0.52, blue: 0.35)]
        case .ipaManager: return [Color(red: 0.98, green: 0.36, blue: 0.52), Color(red: 0.82, green: 0.18, blue: 0.36)]
        case .qrScanner: return [Color(red: 0.22, green: 0.78, blue: 0.94), Color(red: 0.06, green: 0.54, blue: 0.76)]
        }
    }
}

@MainActor
final class AppPreferences: ObservableObject {
    static let shared = AppPreferences()
    private let defaults = UserDefaults.standard

    @Published var uiStyle: AppUIStyle {
        didSet { defaults.set(uiStyle.rawValue, forKey: "ui_style") }
    }
    @Published var themeMode: AppThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: "theme_mode") }
    }
    @Published var homeColumns: Int {
        didSet { defaults.set(homeColumns, forKey: "home_columns") }
    }
    @Published var browserHomeUrl: String {
        didSet { defaults.set(browserHomeUrl, forKey: "browser_home_url") }
    }
    @Published var githubMirrorEnabled: Bool {
        didSet { defaults.set(githubMirrorEnabled, forKey: "github_mirror_enabled") }
    }
    @Published var toolOrder: [JoolsToolId] {
        didSet { defaults.set(toolOrder.map { $0.rawValue }, forKey: "tool_order") }
    }

    private init() {
        self.uiStyle = AppUIStyle(rawValue: defaults.string(forKey: "ui_style") ?? "") ?? .liquidGlass
        self.themeMode = AppThemeMode(rawValue: defaults.string(forKey: "theme_mode") ?? "") ?? .system
        let cols = defaults.integer(forKey: "home_columns")
        self.homeColumns = (1...4).contains(cols) ? cols : 3
        self.browserHomeUrl = defaults.string(forKey: "browser_home_url") ?? BookmarkManager.defaultHomeUrl
        if defaults.object(forKey: "github_mirror_enabled") == nil {
            self.githubMirrorEnabled = true
        } else {
            self.githubMirrorEnabled = defaults.bool(forKey: "github_mirror_enabled")
        }
        let savedOrder = defaults.stringArray(forKey: "tool_order") ?? []
        let parsed = savedOrder.compactMap { JoolsToolId(rawValue: $0) }
        let missing = JoolsToolId.allCases.filter { !parsed.contains($0) }
        self.toolOrder = parsed + missing
    }

    func moveTool(from source: Int, to destination: Int) {
        guard toolOrder.indices.contains(source),
              toolOrder.indices.contains(destination),
              source != destination else { return }
        var copy = toolOrder
        let item = copy.remove(at: source)
        copy.insert(item, at: destination)
        toolOrder = copy
    }
}
