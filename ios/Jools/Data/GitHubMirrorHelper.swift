import Foundation

enum GitHubMirrorHelper {
    private static let mirrorPrefixes = [
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.1888866.xyz/",
        "https://github.akams.cn/"
    ]

    static func unwrapMirrorUrl(_ url: String) -> String {
        for prefix in mirrorPrefixes {
            if url.hasPrefix(prefix) {
                return String(url.dropFirst(prefix.count))
            }
        }
        return url
    }

    static func isGitHubUrl(_ url: String) -> Bool {
        let lower = url.lowercased()
        return lower.hasPrefix("https://github.com/") ||
            lower.hasPrefix("https://raw.githubusercontent.com/") ||
            lower.hasPrefix("https://gist.githubusercontent.com/") ||
            lower.hasPrefix("https://objects.githubusercontent.com/")
    }

    @MainActor
    static func buildCandidateURLs(_ rawUrl: String) -> [String] {
        let baseUrl = unwrapMirrorUrl(rawUrl)
        guard AppPreferences.shared.githubMirrorEnabled, isGitHubUrl(baseUrl) else {
            return [baseUrl]
        }
        return mirrorPrefixes.map { $0 + baseUrl } + [baseUrl]
    }
}
