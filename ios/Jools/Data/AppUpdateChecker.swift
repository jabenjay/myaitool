import Foundation

enum UpdateChannel: String, CaseIterable, Identifiable {
    case all = "all"
    case release = "release"
    case prerelease = "prerelease"
    case beta = "beta"
    case debug = "debug"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: return "全部"
        case .release: return "release"
        case .prerelease: return "prerelease"
        case .beta: return "beta"
        case .debug: return "debug"
        }
    }
}

enum UpdateCheckResult: Identifiable {
    case upToDate(current: String)
    case updateAvailable(current: String, latest: String, name: String, notes: String, downloadUrl: String)
    case error(message: String)

    var id: String {
        switch self {
        case .upToDate(let c): return "ok_\(c)"
        case .updateAvailable(_, let l, _, _, _): return "upd_\(l)"
        case .error(let m): return "err_\(m)"
        }
    }
}

@MainActor
final class AppUpdateChecker {
    static let repoURL = "https://github.com/jabenjay/myaitool"
    private static let releasesAPI = "https://api.github.com/repos/jabenjay/myaitool/releases?per_page=20"
    private static let channelKey = "update_channel"

    static var currentVersionName: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    static func getSelectedChannel() -> UpdateChannel {
        UpdateChannel(rawValue: UserDefaults.standard.string(forKey: channelKey) ?? "") ?? .all
    }

    static func setSelectedChannel(_ channel: UpdateChannel) {
        UserDefaults.standard.set(channel.rawValue, forKey: channelKey)
    }

    static func checkLatestRelease(channel: UpdateChannel? = nil) async -> UpdateCheckResult {
        let resolvedChannel = channel ?? getSelectedChannel()
        let current = currentVersionName
        guard let url = URL(string: releasesAPI) else { return .error(message: "无效的检查地址") }
        var req = URLRequest(url: url, timeoutInterval: 12)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("Jools-iOS", forHTTPHeaderField: "User-Agent")

        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            if let http = resp as? HTTPURLResponse {
                if http.statusCode == 404 { return .upToDate(current: current) }
                if !(200...299).contains(http.statusCode) {
                    return .error(message: "检查失败 (HTTP \(http.statusCode))")
                }
            }
            guard let arr = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                return .upToDate(current: current)
            }
            guard let matched = arr.first(where: { matchesChannel($0, channel: resolvedChannel) }) else {
                return .upToDate(current: current)
            }
            let tag = (matched["tag_name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let name = ((matched["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 } ?? tag
            let rawNotes = (matched["body"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let notes = rawNotes.components(separatedBy: .newlines)
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("**Full Changelog**") }
                .joined(separator: "\n")
            let htmlUrl = (matched["html_url"] as? String) ?? "\(repoURL)/releases"

            var ipaUrl: String? = nil
            if let assets = matched["assets"] as? [[String: Any]] {
                for a in assets {
                    let dl = a["browser_download_url"] as? String ?? ""
                    if dl.lowercased().hasSuffix(".ipa") {
                        ipaUrl = dl
                        break
                    }
                }
            }

            if !tag.isEmpty && compareVersions(remote: tag, local: current) > 0 {
                return .updateAvailable(
                    current: current,
                    latest: tag,
                    name: name,
                    notes: notes,
                    downloadUrl: ipaUrl ?? htmlUrl
                )
            }
            return .upToDate(current: current)
        } catch {
            return .error(message: error.localizedDescription)
        }
    }

    private static func matchesChannel(_ release: [String: Any], channel: UpdateChannel) -> Bool {
        if (release["draft"] as? Bool) == true { return false }
        let tag = (release["tag_name"] as? String ?? "").lowercased()
        let isPre = (release["prerelease"] as? Bool) ?? false
        switch channel {
        case .all: return true
        case .release: return !isPre && !tag.contains("-beta") && !tag.contains("-pre") && !tag.contains("-debug")
        case .prerelease: return tag.contains("-pre") || (isPre && !tag.contains("beta") && !tag.contains("debug"))
        case .beta: return tag.contains("beta")
        case .debug: return tag.contains("debug")
        }
    }

    private static func compareVersions(remote: String, local: String) -> Int {
        let rClean = remote.trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
        let lClean = local.trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
        let rParts = rClean.components(separatedBy: CharacterSet.decimalDigits.inverted).compactMap { Int($0) }
        let lParts = lClean.components(separatedBy: CharacterSet.decimalDigits.inverted).compactMap { Int($0) }
        let count = max(rParts.count, lParts.count)
        for i in 0..<count {
            let r = i < rParts.count ? rParts[i] : 0
            let l = i < lParts.count ? lParts[i] : 0
            if r != l { return r < l ? -1 : 1 }
        }
        return 0
    }
}
