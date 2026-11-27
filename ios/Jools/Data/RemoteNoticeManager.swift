import Foundation
import CryptoKit

enum NoticeType: String {
    case usageNotice
    case announcement
}

struct NoticeDonationConfig: Equatable {
    var enabled: Bool = true
    var title: String = "捐赠支持"
    var description: String = "如果 Jools 对你有帮助，欢迎自愿打赏支持后续维护："
    var alipayUrl: String = "https://qr.alipay.com/fkx19214trgp2tndpljhv7f"
    var alipayLinkText: String = "点击跳转支付宝捐赠"
    var wechatUrl: String = "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"
}

struct PendingNoticeItem: Identifiable, Equatable {
    let id = UUID()
    let type: NoticeType
    let title: String
    let content: String
    let contentHash: String
    let donationConfig: NoticeDonationConfig?
}

@MainActor
final class RemoteNoticeManager {
    static let shared = RemoteNoticeManager()
    private let defaults = UserDefaults.standard
    private let keyNoticeHash = "ack_usage_notice_hash"
    private let keyAnnHash = "ack_announcement_hash"

    private let apiNotice = "https://api.github.com/repos/jabenjay/myaitool/contents/notices/usage_notice/NOTICE.md?ref=main"
    private let apiDonation = "https://api.github.com/repos/jabenjay/myaitool/contents/notices/usage_notice/DONATION.json?ref=main"
    private let apiAnn = "https://api.github.com/repos/jabenjay/myaitool/contents/notices/announcement/ANNOUNCEMENT.md?ref=main"

    private let rawNotice = "https://raw.githubusercontent.com/jabenjay/myaitool/main/notices/usage_notice/NOTICE.md"
    private let rawDonation = "https://raw.githubusercontent.com/jabenjay/myaitool/main/notices/usage_notice/DONATION.json"
    private let rawAnn = "https://raw.githubusercontent.com/jabenjay/myaitool/main/notices/announcement/ANNOUNCEMENT.md"

    private let bundledDefaultNotice = """
# Jools 使用须知

在使用本应用前，请仔细阅读并遵守以下条款：

## 1. 非商业用途声明
- 本应用为开源免费工具软件，严禁任何形式的商业售卖、付费代下、捆绑收费或盈利性分发。

## 2. 分享与二次构建规范
- 欢迎在遵守开源协议的前提下进行技术交流、分享或基于源码进行二次构建。
- 任何形式的分享、转载或二次发布，均须在显眼位置注明出处、原仓库地址（github.com/jabenjay/myaitool）及原作者信息。

## 3. 免责声明
- 本应用按“现状（AS IS）”提供，开发者不承担因使用本应用、第三方网页内容或操作失误导致的任何直接或间接损失。
"""

    func checkPendingNotices() async -> [PendingNoticeItem] {
        let savedNoticeHash = defaults.string(forKey: keyNoticeHash) ?? ""
        let savedAnnHash = defaults.string(forKey: keyAnnHash) ?? ""
        var result: [PendingNoticeItem] = []

        let remoteNotice = await fetchLatestGitHubFile(apiUrl: apiNotice, rawUrl: rawNotice)
        let remoteDonationRaw = await fetchLatestGitHubFile(apiUrl: apiDonation, rawUrl: rawDonation)
        let donationCfg = parseDonationConfig(remoteDonationRaw)
        let noticeText = remoteNotice ?? (savedNoticeHash.isEmpty ? bundledDefaultNotice : nil)

        if let text = noticeText, !text.isEmpty {
            let combined = (remoteDonationRaw?.isEmpty == false) ? "\(text)\n---\n\(remoteDonationRaw!)" : text
            let hash = sha256(combined)
            if hash != savedNoticeHash {
                result.append(PendingNoticeItem(
                    type: .usageNotice,
                    title: "使用须知",
                    content: text,
                    contentHash: hash,
                    donationConfig: donationCfg
                ))
            }
        }

        if let annText = await fetchLatestGitHubFile(apiUrl: apiAnn, rawUrl: rawAnn), !annText.isEmpty {
            let hash = sha256(annText)
            if hash != savedAnnHash {
                result.append(PendingNoticeItem(
                    type: .announcement,
                    title: "应用公告",
                    content: annText,
                    contentHash: hash,
                    donationConfig: nil
                ))
            }
        }

        return result
    }

    func markAcknowledged(_ item: PendingNoticeItem) {
        let key = item.type == .usageNotice ? keyNoticeHash : keyAnnHash
        defaults.set(item.contentHash, forKey: key)
    }

    private func fetchLatestGitHubFile(apiUrl: String, rawUrl: String) async -> String? {
        if let url = URL(string: apiUrl) {
            var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 7)
            req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
            req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
            req.setValue("Jools-Notice/1.0", forHTTPHeaderField: "User-Agent")
            if let (data, resp) = try? await URLSession.shared.data(for: req),
               let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let b64 = (json["content"] as? String)?.replacingOccurrences(of: "\n", with: ""),
               let decoded = Data(base64Encoded: b64),
               let text = String(data: decoded, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty {
                return text
            }
        }

        let cacheBust = "\(rawUrl)?t=\(Int(Date().timeIntervalSince1970 * 1000))"
        let candidates = GitHubMirrorHelper.buildCandidateURLs(cacheBust)
        for cand in candidates {
            guard let u = URL(string: cand) else { continue }
            var req = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
            req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
            if let (data, resp) = try? await URLSession.shared.data(for: req),
               let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode),
               let text = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty {
                return text
            }
        }
        return nil
    }

    private func parseDonationConfig(_ raw: String?) -> NoticeDonationConfig {
        guard let raw, let data = raw.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return NoticeDonationConfig()
        }
        return NoticeDonationConfig(
            enabled: (obj["enabled"] as? Bool) ?? true,
            title: (obj["title"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "捐赠支持",
            description: (obj["description"] as? String) ?? "如果 Jools 对你有帮助，欢迎自愿打赏支持后续维护：",
            alipayUrl: (obj["alipay_url"] as? String) ?? "https://qr.alipay.com/fkx19214trgp2tndpljhv7f",
            alipayLinkText: (obj["alipay_link_text"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "点击跳转支付宝捐赠",
            wechatUrl: (obj["wechat_url"] as? String) ?? "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"
        )
    }

    private func sha256(_ text: String) -> String {
        let digest = SHA256.hash(data: Data(text.trimmingCharacters(in: .whitespacesAndNewlines).utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
