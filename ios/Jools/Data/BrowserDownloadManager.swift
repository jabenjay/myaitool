import Foundation

enum BrowserDownloadStatus: String, Codable {
    case downloading
    case completed
    case failed
}

struct BrowserDownloadItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var fileName: String
    var sourceUrl: String
    var mimeType: String
    var status: BrowserDownloadStatus
    var progress: Double
    var downloadedBytes: Int64
    var totalBytes: Int64
    var localFileName: String?
    var createdAt: TimeInterval
    var errorMessage: String?
    var importedToUnsignedIpa: Bool = false
}

@MainActor
final class BrowserDownloadManager: ObservableObject {
    static let shared = BrowserDownloadManager()

    @Published var items: [BrowserDownloadItem] = []

    private let fm = FileManager.default
    private var downloadsDir: URL {
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("Downloads", isDirectory: true)
    }
    private var metaURL: URL { downloadsDir.appendingPathComponent("downloads_meta.json") }
    private var activeTasks: [String: Task<Void, Never>] = [:]

    private init() {
        ensureDir()
        loadMeta()
    }

    private func ensureDir() {
        if !fm.fileExists(atPath: downloadsDir.path) {
            try? fm.createDirectory(at: downloadsDir, withIntermediateDirectories: true)
        }
    }

    private func loadMeta() {
        guard let data = try? Data(contentsOf: metaURL),
              let decoded = try? JSONDecoder().decode([BrowserDownloadItem].self, from: data) else {
            return
        }
        items = decoded.map { item in
            if item.status == .downloading {
                var copy = item
                copy.status = .failed
                copy.errorMessage = "下载已中断"
                return copy
            }
            return item
        }
    }

    private func saveMeta() {
        if let data = try? JSONEncoder().encode(items) {
            try? data.write(to: metaURL, options: .atomic)
        }
    }

    func localFileURL(for item: BrowserDownloadItem) -> URL? {
        guard let name = item.localFileName else { return nil }
        let u = downloadsDir.appendingPathComponent(name)
        return fm.fileExists(atPath: u.path) ? u : nil
    }

    func startDownload(urlStr: String, suggestedName: String? = nil, mimeType: String = "") {
        ensureDir()
        guard let url = URL(string: urlStr) else { return }

        let rawName = (suggestedName?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? (url.lastPathComponent.isEmpty ? "download_\(Int(Date().timeIntervalSince1970))" : url.lastPathComponent)
        let safeName = rawName.replacingOccurrences(of: "/", with: "_")
        let id = UUID().uuidString

        let item = BrowserDownloadItem(
            id: id,
            fileName: safeName,
            sourceUrl: urlStr,
            mimeType: mimeType,
            status: .downloading,
            progress: 0,
            downloadedBytes: 0,
            totalBytes: -1,
            localFileName: nil,
            createdAt: Date().timeIntervalSince1970,
            errorMessage: nil,
            importedToUnsignedIpa: false
        )
        items.insert(item, at: 0)
        saveMeta()

        let task = Task {
            let candidates = GitHubMirrorHelper.buildCandidateURLs(urlStr)
            var lastError: Error = NSError(domain: "JoolsDownload", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "下载失败"
            ])
            var succeeded = false

            for candStr in candidates {
                guard let candURL = URL(string: candStr) else { continue }
                var destURLToClean: URL? = nil
                do {
                    var req = URLRequest(url: candURL, timeoutInterval: 60)
                    req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")

                    let (bytes, response) = try await URLSession.shared.bytes(for: req)
                    if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                        throw NSError(domain: "JoolsDownload", code: http.statusCode, userInfo: [
                            NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"
                        ])
                    }

                    let respMime = (response.mimeType ?? "").lowercased()
                    let extLower = (safeName as NSString).pathExtension.lowercased()
                    let requiresBinary = ["ipa", "zip", "p12", "mobileprovision", "apk"].contains(extLower)
                    if requiresBinary && respMime.contains("text/html") {
                        throw NSError(domain: "JoolsDownload", code: -2, userInfo: [
                            NSLocalizedDescriptionKey: "镜像返回了网页而非安装包"
                        ])
                    }

                    let expected = response.expectedContentLength
                    var resolvedName = safeName
                    if let respName = response.suggestedFilename, !respName.isEmpty, respName != "Unknown", !respName.lowercased().hasSuffix(".html") {
                        resolvedName = respName.replacingOccurrences(of: "/", with: "_")
                    }

                    let storedName = "\(Int(Date().timeIntervalSince1970))_\(resolvedName)"
                    let destURL = downloadsDir.appendingPathComponent(storedName)
                    destURLToClean = destURL
                    if fm.fileExists(atPath: destURL.path) { try? fm.removeItem(at: destURL) }
                    fm.createFile(atPath: destURL.path, contents: nil)
                    let handle = try FileHandle(forWritingTo: destURL)
                    defer { try? handle.close() }

                    var buffer = Data()
                    buffer.reserveCapacity(64 * 1024)
                    var received: Int64 = 0
                    var lastUiUpdate = Date()
                    var headerChecked = false
                    let needsZipHeader = ["ipa", "zip"].contains((resolvedName as NSString).pathExtension.lowercased())

                    for try await byte in bytes {
                        if Task.isCancelled { throw CancellationError() }
                        buffer.append(byte)
                        received += 1

                        if needsZipHeader && !headerChecked && received >= 4 {
                            headerChecked = true
                            if buffer[0] != 0x50 || buffer[1] != 0x4B {
                                throw NSError(domain: "JoolsDownload", code: -3, userInfo: [
                                    NSLocalizedDescriptionKey: "下载内容不是有效的 ZIP/IPA 安装包（可能被镜像拦截）"
                                ])
                            }
                        }

                        if buffer.count >= 32 * 1024 {
                            try handle.write(contentsOf: buffer)
                            buffer.removeAll(keepingCapacity: true)
                            if Date().timeIntervalSince(lastUiUpdate) >= 0.25 {
                                lastUiUpdate = Date()
                                updateProgress(id: id, downloaded: received, total: expected, fileName: resolvedName)
                            }
                        }
                    }
                    if !buffer.isEmpty {
                        try handle.write(contentsOf: buffer)
                    }
                    if needsZipHeader && received < 22 {
                        throw NSError(domain: "JoolsDownload", code: -4, userInfo: [
                            NSLocalizedDescriptionKey: "下载的文件不完整（大小异常）"
                        ])
                    }

                    var importedIpa = false
                    if resolvedName.lowercased().hasSuffix(".ipa") {
                        if (try? IpaManager.shared.importIpa(from: destURL, asSigned: false)) != nil {
                            importedIpa = true
                        }
                    }

                    if let idx = items.firstIndex(where: { $0.id == id }) {
                        items[idx].fileName = resolvedName
                        items[idx].status = .completed
                        items[idx].progress = 1.0
                        items[idx].downloadedBytes = received
                        items[idx].totalBytes = max(expected, received)
                        items[idx].localFileName = storedName
                        items[idx].importedToUnsignedIpa = importedIpa
                        saveMeta()
                    }
                    succeeded = true
                    break
                } catch {
                    if let d = destURLToClean { try? fm.removeItem(at: d) }
                    lastError = error
                    if Task.isCancelled { break }
                }
            }

            if !succeeded {
                if let idx = items.firstIndex(where: { $0.id == id }) {
                    items[idx].status = .failed
                    items[idx].errorMessage = lastError.localizedDescription
                    saveMeta()
                }
            }
            activeTasks.removeValue(forKey: id)
        }
        activeTasks[id] = task
    }

    private func updateProgress(id: String, downloaded: Int64, total: Int64, fileName: String) {
        guard let idx = items.firstIndex(where: { $0.id == id }) else { return }
        items[idx].fileName = fileName
        items[idx].downloadedBytes = downloaded
        items[idx].totalBytes = total
        items[idx].progress = total > 0 ? min(1.0, Double(downloaded) / Double(total)) : 0
    }

    func importToIpaManager(_ item: BrowserDownloadItem) -> Bool {
        guard let url = localFileURL(for: item) else { return false }
        if (try? IpaManager.shared.importIpa(from: url, asSigned: false)) != nil {
            if let idx = items.firstIndex(where: { $0.id == item.id }) {
                items[idx].importedToUnsignedIpa = true
                saveMeta()
            }
            return true
        }
        return false
    }

    func deleteItem(_ item: BrowserDownloadItem) {
        activeTasks[item.id]?.cancel()
        activeTasks.removeValue(forKey: item.id)
        if let local = localFileURL(for: item) {
            try? fm.removeItem(at: local)
        }
        items.removeAll { $0.id == item.id }
        saveMeta()
    }

    func clearCompleted() {
        let toRemove = items.filter { $0.status != .downloading }
        for item in toRemove {
            if let local = localFileURL(for: item) {
                try? fm.removeItem(at: local)
            }
        }
        items.removeAll { $0.status != .downloading }
        saveMeta()
    }
}
