import Foundation

struct BookmarkItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var url: String
}

@MainActor
final class BookmarkManager: ObservableObject {
    static let shared = BookmarkManager()
    static let defaultHomeUrl = "https://biglist03.xyz/"

    private let defaults = UserDefaults.standard
    private let keyBookmarks = "bookmarks_json"
    private let keyLegacyBookmarks = "browser_bookmarks_json"
    private let keyHomeUrl = "browser_home_url"
    private let keyV4Migrated = "bookmarks_v4_migrated"

    @Published var bookmarks: [BookmarkItem] = []
    @Published var homeUrl: String = BookmarkManager.defaultHomeUrl

    private init() {
        migrateV4DefaultsIfNeeded()
        self.homeUrl = defaults.string(forKey: keyHomeUrl) ?? Self.defaultHomeUrl
        self.bookmarks = loadBookmarks()
    }

    private func defaultBookmarks() -> [BookmarkItem] {
        [
            BookmarkItem(name: "文尼导航", url: "https://biglist03.xyz/"),
            BookmarkItem(name: "59导航", url: "https://dh852.cc/?ide=pl2vfit1"),
            BookmarkItem(name: "海角57", url: "https://hjtv57.top/?lde=3k8pwlo0"),
            BookmarkItem(name: "萝莉岛", url: "https://loli88.top/?lde=7veeox3p"),
            BookmarkItem(name: "大门导航", url: "https://vbc04o.csmen1056.cc/%E5%A4%A7%E9%97%A8/1b956a/#shipin"),
            BookmarkItem(name: "2025秒播", url: "https://52chengrer.zxmbsp6.cc/2025/"),
            BookmarkItem(name: "搜片网", url: "https://52chengren.insearch10.cc/vip/369/")
        ]
    }

    private func migrateV4DefaultsIfNeeded() {
        if defaults.bool(forKey: keyV4Migrated) { return }
        defaults.set(Self.defaultHomeUrl, forKey: keyHomeUrl)

        var existing: [BookmarkItem] = []
        if let d = defaults.data(forKey: keyBookmarks),
           let decoded = try? JSONDecoder().decode([BookmarkItem].self, from: d) {
            existing = decoded
        } else if let legacyData = defaults.data(forKey: keyLegacyBookmarks) {
            struct LegacyBookmark: Codable {
                var id: String
                var title: String
                var url: String
            }
            if let legacy = try? JSONDecoder().decode([LegacyBookmark].self, from: legacyData) {
                existing = legacy.map { BookmarkItem(id: $0.id, name: $0.title, url: $0.url) }
            }
        }

        var seen = Set<String>()
        var merged: [BookmarkItem] = []
        for item in (existing + defaultBookmarks()) {
            let norm = item.url.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                .lowercased()
            if !norm.isEmpty && !seen.contains(norm) {
                seen.insert(norm)
                merged.append(item)
            }
        }
        persist(merged)
        defaults.set(true, forKey: keyV4Migrated)
    }

    private func loadBookmarks() -> [BookmarkItem] {
        guard let d = defaults.data(forKey: keyBookmarks),
              let decoded = try? JSONDecoder().decode([BookmarkItem].self, from: d),
              !decoded.isEmpty else {
            let defs = defaultBookmarks()
            persist(defs)
            return defs
        }
        return decoded
    }

    private func persist(_ list: [BookmarkItem]) {
        if let d = try? JSONEncoder().encode(list) {
            defaults.set(d, forKey: keyBookmarks)
        }
    }

    func normalizeUrl(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return trimmed
        }
        return "https://\(trimmed)"
    }

    func setHomeUrl(_ url: String) {
        let clean = normalizeUrl(url)
        defaults.set(clean, forKey: keyHomeUrl)
        homeUrl = clean
        AppPreferences.shared.browserHomeUrl = clean
    }

    func addBookmark(name: String, url: String) {
        let cleanUrl = normalizeUrl(url)
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalName = trimmedName.isEmpty
            ? cleanUrl.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "")
            : trimmedName
        bookmarks.append(BookmarkItem(name: finalName, url: cleanUrl))
        persist(bookmarks)
    }

    func updateBookmark(id: String, name: String, url: String) {
        let cleanUrl = normalizeUrl(url)
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalName = trimmedName.isEmpty
            ? cleanUrl.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "")
            : trimmedName
        bookmarks = bookmarks.map {
            $0.id == id ? BookmarkItem(id: id, name: finalName, url: cleanUrl) : $0
        }
        persist(bookmarks)
    }

    func deleteBookmark(id: String) {
        bookmarks.removeAll { $0.id == id }
        persist(bookmarks)
    }

    func deleteBookmarks(at offsets: IndexSet) {
        bookmarks.remove(atOffsets: offsets)
        persist(bookmarks)
    }

    func moveBookmarks(from source: IndexSet, to destination: Int) {
        bookmarks.move(fromOffsets: source, toOffset: destination)
        persist(bookmarks)
    }

    func isBookmarked(_ url: String) -> Bool {
        let norm = url.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            .lowercased()
        return bookmarks.contains {
            $0.url.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                .lowercased() == norm
        }
    }

    func toggleBookmark(name: String, url: String) -> Bool {
        let norm = url.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            .lowercased()
        if let existing = bookmarks.first(where: {
            $0.url.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                .lowercased() == norm
        }) {
            deleteBookmark(id: existing.id)
            return false
        } else {
            addBookmark(name: name, url: url)
            return true
        }
    }
}
