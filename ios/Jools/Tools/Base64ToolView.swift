import SwiftUI
import UniformTypeIdentifiers

struct Base64ToolView: View {
    @ObservedObject var prefs: AppPreferences
    @State private var inputText = ""
    @State private var outputText = ""
    @State private var mode = 0 // 0: 编码, 1: 解码
    @State private var showFileImporter = false
    @State private var toastMsg: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                Picker("模式", selection: $mode) {
                    Text("编码 (Encode)").tag(0)
                    Text("解码 (Decode)").tag(1)
                }
                .pickerStyle(.segmented)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(mode == 0 ? "原文 / 文件" : "Base64 字符串")
                            .font(.system(size: 13, weight: .semibold))
                        Spacer()
                        Button("选文件") { showFileImporter = true }
                            .font(.system(size: 12))
                        Button("清空") {
                            inputText = ""
                            outputText = ""
                        }
                        .font(.system(size: 12))
                    }
                    TextEditor(text: $inputText)
                        .font(.system(size: 13, design: .monospaced))
                        .frame(minHeight: 130)
                        .padding(6)
                        .background(Color.primary.opacity(0.04))
                        .cornerRadius(12)
                }
                .padding(14)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)

                Button {
                    convert()
                } label: {
                    Text(mode == 0 ? "执行 Base64 编码" : "执行 Base64 解码")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(inputText.isEmpty)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("输出结果")
                            .font(.system(size: 13, weight: .semibold))
                        Spacer()
                        if !outputText.isEmpty {
                            Button("复制结果") {
                                UIPasteboard.general.string = outputText
                                toastMsg = "已复制"
                            }
                            .font(.system(size: 12))
                        }
                    }
                    TextEditor(text: .constant(outputText))
                        .font(.system(size: 13, design: .monospaced))
                        .frame(minHeight: 130)
                        .padding(6)
                        .background(Color.primary.opacity(0.04))
                        .cornerRadius(12)
                }
                .padding(14)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
            }
            .padding(16)
        }
        .navigationTitle("Base64 编解码")
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.data]) { result in
            if case .success(let url) = result {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url) {
                    if mode == 0 {
                        outputText = data.base64EncodedString()
                        inputText = "[已读取文件: \(url.lastPathComponent), \(data.count) 字节]"
                    } else if let str = String(data: data, encoding: .utf8) {
                        inputText = str
                    }
                }
            }
        }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }

    private func convert() {
        if mode == 0 {
            outputText = Data(inputText.utf8).base64EncodedString()
        } else {
            let clean = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            if let data = Data(base64Encoded: clean, options: .ignoreUnknownCharacters),
               let str = String(data: data, encoding: .utf8) {
                outputText = str
            } else {
                toastMsg = "解码失败：无效的 Base64 内容"
            }
        }
    }
}
