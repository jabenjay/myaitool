import SwiftUI

struct ToolboxHomeView: View {
    @ObservedObject var prefs: AppPreferences
    let onOpenTool: (JoolsToolId) -> Void

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: prefs.homeColumns)
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Jools")
                    .font(.system(size: 30, weight: .bold))
                    .padding(.horizontal, 4)
                    .padding(.top, 8)

                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(prefs.toolOrder) { tool in
                        Button {
                            onOpenTool(tool)
                        } label: {
                            VStack(spacing: 10) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(tool.accentColor.opacity(0.15))
                                        .frame(width: 46, height: 46)
                                    Image(systemName: tool.systemIcon)
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundColor(tool.accentColor)
                                }
                                VStack(spacing: 2) {
                                    Text(tool.title)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                    if prefs.homeColumns <= 3 {
                                        Text(tool.subtitle)
                                            .font(.system(size: 10.5))
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .padding(.horizontal, 8)
                            .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
    }
}
