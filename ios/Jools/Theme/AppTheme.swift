import SwiftUI

enum AppUIStyle: String, CaseIterable, Identifiable {
    case standard = "STANDARD"
    case liquidGlass = "LIQUID_GLASS"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .standard: return "标准材质"
        case .liquidGlass: return "液态玻璃"
        }
    }
}

enum AppThemeMode: String, CaseIterable, Identifiable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

struct JoolsCardModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let uiStyle: AppUIStyle
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        let isGlass = uiStyle == .liquidGlass
        let bg: Color = {
            if isGlass {
                return colorScheme == .dark
                    ? Color.white.opacity(0.08)
                    : Color.white.opacity(0.68)
            } else {
                return colorScheme == .dark
                    ? Color(red: 0.12, green: 0.13, blue: 0.17)
                    : Color(red: 0.96, green: 0.97, blue: 0.98)
            }
        }()

        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(isGlass ? AnyShapeStyle(.ultraThinMaterial) : AnyShapeStyle(bg))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        colorScheme == .dark
                            ? Color.white.opacity(isGlass ? 0.16 : 0.08)
                            : Color.black.opacity(isGlass ? 0.08 : 0.04),
                        lineWidth: 0.8
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

extension View {
    func joolsCard(uiStyle: AppUIStyle, cornerRadius: CGFloat = 20) -> some View {
        modifier(JoolsCardModifier(uiStyle: uiStyle, cornerRadius: cornerRadius))
    }
}
