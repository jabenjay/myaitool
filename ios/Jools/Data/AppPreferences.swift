import Foundation
import SwiftUI

enum JoolsToolId: String, CaseIterable, Identifiable {
    case base64 = "base64"
    case formatFactory = "format_factory"
    case fileCompressor = "file_compressor"
    case qrGenerator = "qr_generator"
    case qrScanner = "qr_scanner"
    case browser = "browser"
    case pushGit = "push_git"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .base64: return "Base64"
        case .formatFactory: return "格式工厂"
        case .fileCompressor: return "文件压缩"
        case .qrGenerator: return "二维码"
        case .qrScanner: return "扫一扫"
        case .browser: return "浏览器"
        case .pushGit: return "PushGit"
        }
    }

    var subtitle: String {
        switch self {
        case .base64: return "文本/文件编解码"
        case .formatFactory: return "图片音视频转码"
        case .fileCompressor: return "ZIP与媒体压缩"
        case .qrGenerator: return "自定义样式生成"
        case .qrScanner: return "扫码与相册识别"
        case .browser: return "多标签/浮窗播放"
        case .pushGit: return "GitHub推送与构建"
        }
    }

    var systemIcon: String {
        switch self {
        case .base64: return "chevron.left.forwardslash.chevron.right"
        case .formatFactory: return "arrow.triangle.2.circlepath"
        case .fileCompressor: return "archivebox.fill"
        case .qrGenerator: return "qrcode"
        case .qrScanner: return "qrcode.viewfinder"
        case .browser: return "safari.fill"
        case .pushGit: return "arrow.up.circle.fill"
        }
    }

    var accentColor: Color {
        switch self {
        case .base64: return Color(red: 0.22, green: 0.49, blue: 0.96)
        case .formatFactory: return Color(red: 0.56, green: 0.35, blue: 0.95)
        case .fileCompressor: return Color(red: 0.92, green: 0.52, blue: 0.14)
        case .qrGenerator: return Color(red: 0.11, green: 0.65, blue: 0.45)
        case .qrScanner: return Color(red: 0.08, green: 0.58, blue: 0.78)
        case .browser: return Color(red: 0.15, green: 0.42, blue: 0.92)
        case .pushGit: return Color(red: 0.18, green: 0.62, blue: 0.34)
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
        self.homeColumns = (2...4).contains(cols) ? cols : 3
        self.browserHomeUrl = defaults.string(forKey: "browser_home_url") ?? "https://cn.pornhub.com/"
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
}
