import Foundation

enum GitHubMirrorHelper {
    private static let mirrorPrefixes = [
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.1888866.xyz/",
        "https://github.akams.cn/"
    ]

    static func isGitHubUrl(_ url: String) -> Bool {
        let lower = url.lowercased()
        return lower.hasPrefix("https://github.com/") ||
            lower.hasPrefix("https://raw.githubusercontent.com/") ||
            lower.hasPrefix("https://gist.githubusercontent.com/") ||
            lower.hasPrefix("https://objects.githubusercontent.com/")
    }

    @MainActor
    static func buildCandidateURLs(_ rawUrl: String) -> [String] {
        guard AppPreferences.shared.githubMirrorEnabled, isGitHubUrl(rawUrl) else {
            return [rawUrl]
        }
        return mirrorPrefixes.map { $0 + rawUrl } + [rawUrl]
    }
}
