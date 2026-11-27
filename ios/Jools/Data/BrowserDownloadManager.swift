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
            do {
                var req = URLRequest(url: url, timeoutInterval: 60)
                req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")

                let (bytes, response) = try await URLSession.shared.bytes(for: req)
                if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                    throw NSError(domain: "JoolsDownload", code: http.statusCode, userInfo: [
                        NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"
                    ])
                }

                let expected = response.expectedContentLength
                var resolvedName = safeName
                if let respName = response.suggestedFilename, !respName.isEmpty, respName != "Unknown" {
                    resolvedName = respName.replacingOccurrences(of: "/", with: "_")
                }

                let storedName = "\(Int(Date().timeIntervalSince1970))_\(resolvedName)"
                let destURL = downloadsDir.appendingPathComponent(storedName)
                if fm.fileExists(atPath: destURL.path) { try? fm.removeItem(at: destURL) }
                fm.createFile(atPath: destURL.path, contents: nil)
                let handle = try FileHandle(forWritingTo: destURL)
                defer { try? handle.close() }

                var buffer = Data()
                buffer.reserveCapacity(64 * 1024)
                var received: Int64 = 0
                var lastUiUpdate = Date()

                for try await byte in bytes {
                    if Task.isCancelled { throw CancellationError() }
                    buffer.append(byte)
                    received += 1
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
            } catch {
                if let idx = items.firstIndex(where: { $0.id == id }) {
                    items[idx].status = .failed
                    items[idx].errorMessage = error.localizedDescription
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
