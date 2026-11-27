import SwiftUI
import UIKit

struct LogViewerView: View {
    @EnvironmentObject private var logger: AppLogManager
    @State private var copiedToast = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                if logger.logs.isEmpty {
                    Text("暂无运行日志")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .padding(16)
                } else {
                    ForEach(Array(logger.logs.enumerated()), id: \.offset) { idx, line in
                        let isCrash = line.contains("[CRASH]") || line.contains("ERROR")
                        HStack(alignment: .top, spacing: 8) {
                            Text("\(idx + 1)")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary.opacity(0.6))
                                .frame(width: 30, alignment: .trailing)
                            Text(line)
                                .font(.system(size: 11.5, design: .monospaced))
                                .foregroundColor(isCrash ? .red : .primary)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
            .padding(14)
        }
        .navigationTitle("运行日志")
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    let all = logger.exportAllText()
                    if !all.isEmpty {
                        UIPasteboard.general.string = all
                        copiedToast = true
                    }
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                Button(role: .destructive) {
                    logger.clear()
                } label: {
                    Image(systemName: "trash")
                }
            }
        }
        .alert("已复制全部日志", isPresented: $copiedToast) {
            Button("确定", role: .cancel) {}
        }
    }
}
