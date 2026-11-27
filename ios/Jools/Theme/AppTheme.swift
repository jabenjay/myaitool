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
                    : Color.white.opacity(0.76)
            } else {
                return Color(uiColor: .secondarySystemGroupedBackground)
            }
        }()

        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(isGlass ? AnyShapeStyle(.regularMaterial) : AnyShapeStyle(bg))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [
                                colorScheme == .dark ? Color.white.opacity(isGlass ? 0.22 : 0.08) : Color.white.opacity(0.9),
                                colorScheme == .dark ? Color.white.opacity(0.04) : Color.black.opacity(0.06)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.75
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .shadow(
                color: Color.black.opacity(colorScheme == .dark ? 0.25 : 0.05),
                radius: isGlass ? 10 : 6,
                x: 0,
                y: 3
            )
    }
}

struct JoolsScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1.0)
            .opacity(configuration.isPressed ? 0.92 : 1.0)
            .animation(.spring(response: 0.24, dampingFraction: 0.72), value: configuration.isPressed)
    }
}

extension View {
    func joolsCard(uiStyle: AppUIStyle, cornerRadius: CGFloat = 20) -> some View {
        modifier(JoolsCardModifier(uiStyle: uiStyle, cornerRadius: cornerRadius))
    }
}
