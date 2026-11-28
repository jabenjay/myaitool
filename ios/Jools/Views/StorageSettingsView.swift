import SwiftUI
import UIKit

struct StorageSettingsView: View {
    @ObservedObject var prefs: AppPreferences
    @State private var showBrowser = false

    private var docsURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("当前输出主路径")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)

                    Text("文件 App / 我的 iPhone / Jools")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.accentColor)

                    Text("各功能输出文件将自动保存到 App 文档目录及以下子目录：")
                        .font(.system(size: 13))
                        .foregroundColor(.primary.opacity(0.85))
                        .padding(.top, 4)

                    Text(
                        """
                        • Jools/files      （构建日志、导出文本）
                        • Jools/Downloads  （浏览器下载）
                        • Jools/IPA        （证书与安装包）
                        • Jools/           （格式转换与媒体输出）
                        """
                    )
                    .font(.system(size: 12.5, design: .monospaced))
                    .foregroundColor(.secondary)
                    .lineSpacing(4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)

                Button {
                    showBrowser = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "folder.fill")
                            .font(.system(size: 17, weight: .semibold))
                        Text("浏览当前文件")
                            .font(.system(size: 15.5, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .foregroundColor(.white)
                    .background(Color.accentColor)
                    .cornerRadius(18)
                }
                .buttonStyle(JoolsScaleButtonStyle())
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("保存路径")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showBrowser) {
            NavigationStack {
                AppFolderBrowserView(rootURL: docsURL, prefs: prefs)
            }
        }
    }
}

private struct AppFolderEntry: Identifiable {
    let url: URL
    let name: String
    let isDirectory: Bool
    let size: Int64
    let modifiedDate: Date
    let childCount: Int

    var id: String { url.path }
}

private struct AppFolderBrowserView: View {
    let rootURL: URL
    @ObservedObject var prefs: AppPreferences
    @Environment(\.dismiss) private var dismiss

    @State private var currentURL: URL
    @State private var entries: [AppFolderEntry] = []
    @State private var previewEntry: AppFolderEntry?
    @State private var previewText: String = ""
    @State private var isTextPreview = false
    @State private var shareURL: URL?
    @State private var deleteTarget: AppFolderEntry?
    @State private var errorMessage: String?

    init(rootURL: URL, prefs: AppPreferences) {
        self.rootURL = rootURL
        self.prefs = prefs
        _currentURL = State(initialValue: rootURL)
    }

    private var relativePathTitle: String {
        let rootPath = rootURL.standardizedFileURL.path
        let curPath = currentURL.standardizedFileURL.path
        if curPath == rootPath {
            return "Jools"
        }
        let rel = curPath.replacingOccurrences(of: rootPath, with: "").trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return "Jools/\(rel)"
    }

    private var isAtRoot: Bool {
        currentURL.standardizedFileURL.path == rootURL.standardizedFileURL.path
    }

    var body: some View {
        List {
            if !isAtRoot {
                Button {
                    currentURL = currentURL.deletingLastPathComponent()
                    reloadEntries()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.uturn.backward.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.accentColor)
                        Text("返回上一级")
                            .font(.system(size: 15, weight: .medium))
                        Spacer()
                    }
                    .padding(.vertical, 2)
                }
            }

            if entries.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "folder")
                        .font(.system(size: 34))
                        .foregroundColor(.secondary.opacity(0.6))
                    Text("当前文件夹为空")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 36)
                .listRowBackground(Color.clear)
            } else {
                ForEach(entries) { entry in
                    Button {
                        handleTap(entry)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: iconName(for: entry))
                                .font(.system(size: 22))
                                .foregroundColor(entry.isDirectory ? .orange : .accentColor)
                                .frame(width: 28)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(entry.name)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)
                                Text(subtitle(for: entry))
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: entry.isDirectory ? "chevron.right" : "ellipsis.circle")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 3)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            deleteTarget = entry
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                        if !entry.isDirectory {
                            Button {
                                shareURL = entry.url
                            } label: {
                                Label("分享", systemImage: "square.and.arrow.up")
                            }
                            .tint(.blue)
                        }
                    }
                }
            }
        }
        .navigationTitle(relativePathTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("关闭") { dismiss() }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    reloadEntries()
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .onAppear { reloadEntries() }
        .sheet(item: $previewEntry) { entry in
            NavigationStack {
                VStack(alignment: .leading, spacing: 0) {
                    if isTextPreview {
                        ScrollView {
                            Text(previewText)
                                .font(.system(size: 12, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .textSelection(.enabled)
                        }
                    } else {
                        VStack(spacing: 14) {
                            Image(systemName: iconName(for: entry))
                                .font(.system(size: 48))
                                .foregroundColor(.accentColor)
                            Text(entry.name)
                                .font(.system(size: 17, weight: .semibold))
                                .multilineTextAlignment(.center)
                            Text(subtitle(for: entry))
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)

                            Button {
                                let url = entry.url
                                previewEntry = nil
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                    shareURL = url
                                }
                            } label: {
                                Label("分享 / 导出文件", systemImage: "square.and.arrow.up")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.accentColor)
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }
                            .padding(.top, 8)
                        }
                        .padding(24)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .navigationTitle(entry.name)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("关闭") { previewEntry = nil }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        HStack(spacing: 14) {
                            if isTextPreview {
                                Button {
                                    UIPasteboard.general.string = previewText
                                } label: {
                                    Image(systemName: "doc.on.doc")
                                }
                            }
                            Button {
                                let url = entry.url
                                previewEntry = nil
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                    shareURL = url
                                }
                            } label: {
                                Image(systemName: "square.and.arrow.up")
                            }
                        }
                    }
                }
            }
        }
        .sheet(isPresented: Binding(get: { shareURL != nil }, set: { if !$0 { shareURL = nil } })) {
            if let url = shareURL {
                ActivityShareSheet(items: [url])
            }
        }
        .alert("确认删除？", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } })) {
            Button("取消", role: .cancel) { deleteTarget = nil }
            Button("删除", role: .destructive) {
                if let target = deleteTarget {
                    deleteEntry(target)
                }
                deleteTarget = nil
            }
        } message: {
            Text("确定删除“\(deleteTarget?.name ?? "")”吗？删除后无法恢复。")
        }
        .alert("操作失败", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func reloadEntries() {
        let fm = FileManager.default
        if !fm.fileExists(atPath: rootURL.path) {
            try? fm.createDirectory(at: rootURL, withIntermediateDirectories: true)
        }
        let keys: [URLResourceKey] = [.isDirectoryKey, .fileSizeKey, .contentModificationDateKey]
        guard let urls = try? fm.contentsOfDirectory(at: currentURL, includingPropertiesForKeys: keys, options: [.skipsHiddenFiles]) else {
            entries = []
            return
        }
        let mapped: [AppFolderEntry] = urls.compactMap { url in
            let values = try? url.resourceValues(forKeys: Set(keys))
            let isDir = values?.isDirectory ?? false
            let size = Int64(values?.fileSize ?? 0)
            let date = values?.contentModificationDate ?? Date.distantPast
            let childCount: Int
            if isDir {
                childCount = (try? fm.contentsOfDirectory(atPath: url.path).filter { !$0.hasPrefix(".") }.count) ?? 0
            } else {
                childCount = 0
            }
            return AppFolderEntry(
                url: url,
                name: url.lastPathComponent,
                isDirectory: isDir,
                size: size,
                modifiedDate: date,
                childCount: childCount
            )
        }
        entries = mapped.sorted { a, b in
            if a.isDirectory != b.isDirectory {
                return a.isDirectory && !b.isDirectory
            }
            if a.isDirectory {
                return a.name.localizedStandardCompare(b.name) == .orderedAscending
            }
            return a.modifiedDate > b.modifiedDate
        }
    }

    private func handleTap(_ entry: AppFolderEntry) {
        if entry.isDirectory {
            currentURL = entry.url
            reloadEntries()
            return
        }
        let ext = entry.url.pathExtension.lowercased()
        let textExts: Set<String> = ["txt", "log", "json", "md", "xml", "csv", "plist", "swift", "kt", "py", "sh", "yml", "yaml", "html", "css", "js", "conf", "ini"]
        if textExts.contains(ext), let data = try? Data(contentsOf: entry.url) {
            let maxBytes = 200 * 1024
            if data.count > maxBytes, let partial = String(data: data.prefix(maxBytes), encoding: .utf8) {
                previewText = partial + "\n\n...(文件较大，仅预览前 200 KB，可通过右上角分享查看完整文件)"
            } else if let text = String(data: data, encoding: .utf8) {
                previewText = text.isEmpty ? "(空文件)" : text
            } else {
                isTextPreview = false
                previewEntry = entry
                return
            }
            isTextPreview = true
            previewEntry = entry
        } else {
            isTextPreview = false
            previewEntry = entry
        }
    }

    private func deleteEntry(_ entry: AppFolderEntry) {
        do {
            try FileManager.default.removeItem(at: entry.url)
            reloadEntries()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func iconName(for entry: AppFolderEntry) -> String {
        if entry.isDirectory { return "folder.fill" }
        switch entry.url.pathExtension.lowercased() {
        case "txt", "log", "md", "json", "xml", "csv", "plist": return "doc.text.fill"
        case "png", "jpg", "jpeg", "gif", "webp", "heic": return "photo.fill"
        case "mp4", "mov", "m4v", "mkv": return "film.fill"
        case "mp3", "m4a", "wav", "aac", "flac": return "music.note"
        case "zip", "rar", "7z", "tar", "gz", "ipa": return "archivebox.fill"
        default: return "doc.fill"
        }
    }

    private func subtitle(for entry: AppFolderEntry) -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd HH:mm"
        let dateStr = df.string(from: entry.modifiedDate)
        if entry.isDirectory {
            return "\(entry.childCount) 项 · \(dateStr)"
        }
        let sizeStr = ByteCountFormatter.string(fromByteCount: entry.size, countStyle: .file)
        return "\(sizeStr) · \(dateStr)"
    }
}

private struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
