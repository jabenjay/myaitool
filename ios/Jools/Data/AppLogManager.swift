import Foundation

@MainActor
final class AppLogManager: ObservableObject {
    static let shared = AppLogManager()
    private let maxLines = 300
    @Published private(set) var logs: [String] = []

    private var logFileURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("app_runtime_logs.txt")
    }

    private let formatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "MM-dd HH:mm:ss.SSS"
        df.timeZone = TimeZone.current
        return df
    }()

    private init() {
        loadFromDisk()
        let ver = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        log("SYSTEM", "App启动 | iOS \(UIDevice.current.systemVersion) | Jools v\(ver)")
    }

    private func loadFromDisk() {
        guard let data = try? Data(contentsOf: logFileURL),
              let text = String(data: data, encoding: .utf8) else { return }
        logs = text.components(separatedBy: .newlines).filter { !$0.isEmpty }.suffix(maxLines)
    }

    func log(_ tag: String, _ message: String) {
        let ts = formatter.string(from: Date())
        let entry = "[\(ts)][\(tag)] \(message)"
        logs = (logs + [entry]).suffix(maxLines)
        persist()
    }

    func clear() {
        logs.removeAll()
        try? FileManager.default.removeItem(at: logFileURL)
    }

    func exportAllText() -> String {
        logs.joined(separator: "\n")
    }

    private func persist() {
        let text = logs.joined(separator: "\n")
        try? text.data(using: .utf8)?.write(to: logFileURL, options: .atomic)
    }
}
