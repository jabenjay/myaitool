import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ToolboxHomeView: View {
    @ObservedObject var prefs: AppPreferences
    let onOpenTool: (JoolsToolId) -> Void
    let onOpenScanner: () -> Void

    @State private var draggedTool: JoolsToolId?
    @State private var isReorderMode: Bool = false

    var body: some View {
        let colsCount = max(1, min(4, prefs.homeColumns))
        let columns = Array(
            repeating: GridItem(.flexible(), spacing: colsCount >= 3 ? 10 : 12),
            count: colsCount
        )

        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // iOS 风格顶部栏
                HStack(alignment: .center, spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Jools")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                        Text(isReorderMode ? "拖动卡片调整排列顺序" : "轻量、纯粹的个人工具箱")
                            .font(.system(size: 13))
                            .foregroundColor(isReorderMode ? .accentColor : .secondary)
                    }

                    Spacer()

                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.78)) {
                            isReorderMode.toggle()
                        }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: isReorderMode ? "checkmark" : "arrow.up.arrow.down")
                                .font(.system(size: 12.5, weight: .semibold))
                            Text(isReorderMode ? "完成" : "排序")
                                .font(.system(size: 13, weight: .semibold))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(isReorderMode ? Color.accentColor : Color.primary.opacity(0.07))
                        .foregroundColor(isReorderMode ? .white : .primary)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)

                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        onOpenScanner()
                    } label: {
                        Image(systemName: "qrcode.viewfinder")
                            .font(.system(size: 17, weight: .semibold))
                            .frame(width: 36, height: 36)
                            .background(Color.primary.opacity(0.07))
                            .foregroundColor(.primary)
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("扫一扫")
                }
                .padding(.horizontal, 4)
                .padding(.top, 6)

                LazyVGrid(columns: columns, spacing: colsCount >= 3 ? 10 : 12) {
                    ForEach(prefs.toolOrder) { tool in
                        toolTile(tool: tool, colsCount: colsCount)
                            .onDrag {
                                draggedTool = tool
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                return NSItemProvider(object: tool.rawValue as NSString)
                            }
                            .onDrop(
                                of: [UTType.text],
                                delegate: ToolDropDelegate(
                                    targetTool: tool,
                                    prefs: prefs,
                                    draggedTool: $draggedTool
                                )
                            )
                    }
                }

                if !isReorderMode {
                    Text("提示：长按任意工具卡片可直接拖动排序")
                        .font(.system(size: 11.5))
                        .foregroundColor(.secondary.opacity(0.75))
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 4)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }

    @ViewBuilder
    private func toolTile(tool: JoolsToolId, colsCount: Int) -> some View {
        let isDragging = draggedTool == tool
        let corner: CGFloat = colsCount >= 3 ? 20 : 24

        Button {
            if !isReorderMode {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onOpenTool(tool)
            }
        } label: {
            ZStack(alignment: .topTrailing) {
                if colsCount == 1 {
                    HStack(spacing: 14) {
                        squircleIcon(tool: tool, size: 50, iconSize: 22, radius: 14)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(tool.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.primary)
                            Text(tool.subtitle)
                                .font(.system(size: 12.5))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: isReorderMode ? "line.3.horizontal" : "chevron.right")
                            .font(.system(size: 13.5, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: corner)
                } else {
                    VStack(spacing: colsCount == 4 ? 7 : 10) {
                        squircleIcon(
                            tool: tool,
                            size: colsCount == 4 ? 42 : 48,
                            iconSize: colsCount == 4 ? 19 : 21,
                            radius: colsCount == 4 ? 12 : 14
                        )

                        VStack(spacing: 3) {
                            Text(tool.title)
                                .font(.system(size: colsCount == 4 ? 12 : 14, weight: .semibold))
                                .foregroundColor(.primary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.78)
                            if colsCount == 2 {
                                Text(tool.subtitle)
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, colsCount == 4 ? 14 : 16)
                    .padding(.horizontal, colsCount == 4 ? 6 : 10)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: corner)
                }

                if isReorderMode {
                    Image(systemName: "line.3.horizontal.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.accentColor)
                        .background(Circle().fill(Color(uiColor: .systemBackground)))
                        .padding(6)
                }
            }
            .scaleEffect(isDragging ? 1.05 : 1.0)
            .opacity(isDragging ? 0.85 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.72), value: isDragging)
        }
        .buttonStyle(JoolsScaleButtonStyle())
    }

    private func squircleIcon(tool: JoolsToolId, size: CGFloat, iconSize: CGFloat, radius: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: tool.gradientColors,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: size, height: size)
                .shadow(color: tool.accentColor.opacity(0.28), radius: 5, x: 0, y: 3)
            Image(systemName: tool.systemIcon)
                .font(.system(size: iconSize, weight: .semibold))
                .foregroundColor(.white)
        }
    }
}

private struct ToolDropDelegate: DropDelegate {
    let targetTool: JoolsToolId
    let prefs: AppPreferences
    @Binding var draggedTool: JoolsToolId?

    func dropEntered(info: DropInfo) {
        guard let dragged = draggedTool,
              dragged != targetTool,
              let fromIndex = prefs.toolOrder.firstIndex(of: dragged),
              let toIndex = prefs.toolOrder.firstIndex(of: targetTool) else {
            return
        }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.78)) {
            prefs.moveTool(from: fromIndex, to: toIndex)
        }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    func performDrop(info: DropInfo) -> Bool {
        draggedTool = nil
        return true
    }
}
