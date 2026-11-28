import Foundation
import CryptoKit

struct SavedGitToken: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var username: String
    var token: String
}

struct PushFileEntry: Identifiable, Equatable {
    let id = UUID().uuidString
    let relativePath: String
    let data: Data
    var sizeBytes: Int { data.count }
}

struct GitDiffSummary: Equatable {
    let headSha: String
    let baseTreeSha: String
    let addedFiles: [PushFileEntry]
    let modifiedFiles: [PushFileEntry]
    let deletedPaths: [String]
    let unchangedCount: Int

    var uploadFiles: [PushFileEntry] { addedFiles + modifiedFiles }
}

struct WorkflowInfo: Identifiable, Equatable {
    let id: Int64
    let name: String
    let path: String
}

struct WorkflowInputField: Identifiable, Equatable {
    var id: String { key }
    let key: String
    let description: String
    let required: Bool
    let defaultValue: String
    let options: [String]
}

struct WorkflowRunInfo: Identifiable, Equatable {
    let id: Int64
    let runNumber: Int
    let name: String
    let status: String
    let conclusion: String
    let branch: String
    let createdAt: String
    let htmlUrl: String
}

struct RepoContentItem: Identifiable, Equatable {
    var id: String { path }
    let name: String
    let path: String
    let type: String
    let sha: String
    let size: Int64
}

@MainActor
final class PushGitEngine: ObservableObject {
    private let defaults = UserDefaults.standard
    private let keyPat = "pushgit_pat"
    private let keyRepo = "pushgit_repo"
    private let keyBranch = "pushgit_branch"
    private let keyMsg = "pushgit_msg"
    private let keyTokens = "pushgit_saved_tokens"

    func loadSavedToken() -> String { defaults.string(forKey: keyPat) ?? "" }
    func loadSavedRepo() -> String { defaults.string(forKey: keyRepo) ?? "jabenjay/myaitool" }
    func loadSavedBranch() -> String { defaults.string(forKey: keyBranch) ?? "main" }
    func loadSavedMessage() -> String { defaults.string(forKey: keyMsg) ?? "Sync project files via PushGit" }

    func saveConfig(pat: String, repo: String, branch: String, message: String) {
        defaults.set(pat.trimmingCharacters(in: .whitespacesAndNewlines), forKey: keyPat)
        defaults.set(repo.trimmingCharacters(in: .whitespacesAndNewlines), forKey: keyRepo)
        defaults.set(branch.trimmingCharacters(in: .whitespacesAndNewlines), forKey: keyBranch)
        defaults.set(message.trimmingCharacters(in: .whitespacesAndNewlines), forKey: keyMsg)
    }

    func getSavedTokens() -> [SavedGitToken] {
        guard let d = defaults.data(forKey: keyTokens),
              let list = try? JSONDecoder().decode([SavedGitToken].self, from: d) else { return [] }
        return list
    }

    func saveTokenEntry(username: String, token: String) -> [SavedGitToken] {
        let clean = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return getSavedTokens() }
        var list = getSavedTokens().filter { $0.token != clean }
        list.insert(SavedGitToken(username: username.isEmpty ? "GitHub User" : username, token: clean), at: 0)
        if let d = try? JSONEncoder().encode(list) { defaults.set(d, forKey: keyTokens) }
        return list
    }

    func deleteTokenEntry(id: String) -> [SavedGitToken] {
        let list = getSavedTokens().filter { $0.id != id }
        if let d = try? JSONEncoder().encode(list) { defaults.set(d, forKey: keyTokens) }
        return list
    }

    func verifyToken(_ pat: String) async throws -> String {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/user", pat: pat, body: nil)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let login = obj?["login"] as? String, !login.isEmpty else {
            throw NSError(domain: "PushGit", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法获取用户名"])
        }
        return login
    }

    func listRepos(_ pat: String) async throws -> [String] {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/user/repos?affiliation=owner,collaborator,organization_member&per_page=100&sort=updated", pat: pat, body: nil)
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { $0["full_name"] as? String }
    }

    func listBranches(_ pat: String, repo: String) async throws -> [String] {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/branches?per_page=100", pat: pat, body: nil)
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { $0["name"] as? String }
    }

    func inspectChanges(pat: String, repo: String, branch: String, files: [PushFileEntry]) async throws -> GitDiffSummary {
        let refData = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/ref/heads/\(branch)", pat: pat, body: nil)
        let refObj = try JSONSerialization.jsonObject(with: refData) as? [String: Any]
        let headSha = ((refObj?["object"] as? [String: Any])?["sha"] as? String) ?? ""

        let commitData = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/commits/\(headSha)", pat: pat, body: nil)
        let commitObj = try JSONSerialization.jsonObject(with: commitData) as? [String: Any]
        let baseTreeSha = ((commitObj?["tree"] as? [String: Any])?["sha"] as? String) ?? ""

        let treeData = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/trees/\(baseTreeSha)?recursive=1", pat: pat, body: nil)
        let treeObj = try JSONSerialization.jsonObject(with: treeData) as? [String: Any]
        let treeArr = (treeObj?["tree"] as? [[String: Any]]) ?? []

        var remoteMap: [String: String] = [:]
        for item in treeArr {
            if (item["type"] as? String) == "blob",
               let path = item["path"] as? String,
               let sha = item["sha"] as? String {
                remoteMap[path] = sha
            }
        }

        var added: [PushFileEntry] = []
        var modified: [PushFileEntry] = []
        var unchanged = 0
        var localPaths = Set<String>()

        for entry in files {
            localPaths.insert(entry.relativePath)
            let localSha = computeGitBlobSha1(entry.data)
            if let rSha = remoteMap[entry.relativePath] {
                if rSha == localSha { unchanged += 1 } else { modified.append(entry) }
            } else {
                added.append(entry)
            }
        }
        let deleted = remoteMap.keys.filter { !localPaths.contains($0) }.sorted()

        return GitDiffSummary(
            headSha: headSha,
            baseTreeSha: baseTreeSha,
            addedFiles: added,
            modifiedFiles: modified,
            deletedPaths: deleted,
            unchangedCount: unchanged
        )
    }

    func pushDiff(
        pat: String,
        repo: String,
        branch: String,
        message: String,
        diff: GitDiffSummary,
        syncDeletions: Bool,
        overrideFiles: [PushFileEntry]?,
        onProgress: (Int, Int, String) -> Void
    ) async throws -> String {
        let toUpload = overrideFiles ?? diff.uploadFiles
        var treeEntries: [[String: Any]] = []
        let total = toUpload.count

        for (idx, file) in toUpload.enumerated() {
            onProgress(idx + 1, max(total, 1), file.relativePath)
            let blobBody: [String: Any] = [
                "content": file.data.base64EncodedString(),
                "encoding": "base64"
            ]
            let blobResp = try await apiRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/blobs", pat: pat, body: blobBody)
            let blobObj = try JSONSerialization.jsonObject(with: blobResp) as? [String: Any]
            let blobSha = (blobObj?["sha"] as? String) ?? ""
            treeEntries.append([
                "path": file.relativePath,
                "mode": "100644",
                "type": "blob",
                "sha": blobSha
            ])
        }

        if syncDeletions {
            for delPath in diff.deletedPaths {
                treeEntries.append([
                    "path": delPath,
                    "mode": "100644",
                    "type": "blob",
                    "sha": NSNull()
                ])
            }
        }

        let treeBody: [String: Any] = [
            "base_tree": diff.baseTreeSha,
            "tree": treeEntries
        ]
        let newTreeResp = try await apiRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/trees", pat: pat, body: treeBody)
        let newTreeSha = ((try JSONSerialization.jsonObject(with: newTreeResp) as? [String: Any])?["sha"] as? String) ?? ""

        let commitBody: [String: Any] = [
            "message": message.isEmpty ? "Sync project files via PushGit" : message,
            "tree": newTreeSha,
            "parents": [diff.headSha]
        ]
        let newCommitResp = try await apiRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/commits", pat: pat, body: commitBody)
        let newCommitSha = ((try JSONSerialization.jsonObject(with: newCommitResp) as? [String: Any])?["sha"] as? String) ?? ""

        let refBody: [String: Any] = ["sha": newCommitSha, "force": false]
        _ = try await apiRequest(method: "PATCH", urlStr: "https://api.github.com/repos/\(repo)/git/refs/heads/\(branch)", pat: pat, body: refBody)
        return newCommitSha
    }

    func listWorkflows(_ pat: String, repo: String) async throws -> [WorkflowInfo] {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/actions/workflows", pat: pat, body: nil)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let arr = (obj?["workflows"] as? [[String: Any]]) ?? []
        return arr.compactMap { w in
            guard let id = w["id"] as? Int64,
                  let name = w["name"] as? String,
                  let path = w["path"] as? String else { return nil }
            return WorkflowInfo(id: id, name: name, path: path)
        }
    }

    func fetchWorkflowInputs(_ pat: String, repo: String, workflowPath: String, branch: String) async throws -> [WorkflowInputField] {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/contents/\(workflowPath)?ref=\(branch)", pat: pat, body: nil)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let b64 = (obj?["content"] as? String ?? "").replacingOccurrences(of: "\n", with: "")
        guard let raw = Data(base64Encoded: b64), let yaml = String(data: raw, encoding: .utf8) else { return [] }
        return parseWorkflowDispatchInputs(yaml)
    }

    func dispatchWorkflow(_ pat: String, repo: String, workflowId: Int64, branch: String, inputs: [String: String]) async throws {
        let body: [String: Any] = [
            "ref": branch.isEmpty ? "main" : branch,
            "inputs": inputs
        ]
        _ = try await apiRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/actions/workflows/\(workflowId)/dispatches", pat: pat, body: body)
    }

    func listWorkflowRuns(_ pat: String, repo: String, page: Int = 1, perPage: Int = 15) async throws -> [WorkflowRunInfo] {
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/actions/runs?per_page=\(perPage)&page=\(page)", pat: pat, body: nil)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let arr = (obj?["workflow_runs"] as? [[String: Any]]) ?? []
        return arr.compactMap { r in
            guard let id = r["id"] as? Int64 else { return nil }
            return WorkflowRunInfo(
                id: id,
                runNumber: (r["run_number"] as? Int) ?? 0,
                name: (r["name"] as? String) ?? "",
                status: (r["status"] as? String) ?? "",
                conclusion: (r["conclusion"] as? String) ?? "",
                branch: (r["head_branch"] as? String) ?? "",
                createdAt: (r["created_at"] as? String) ?? "",
                htmlUrl: (r["html_url"] as? String) ?? ""
            )
        }
    }

    func cancelWorkflowRun(_ pat: String, repo: String, runId: Int64) async throws {
        _ = try await apiRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/actions/runs/\(runId)/cancel", pat: pat, body: [:])
    }

    func fetchRunLog(_ pat: String, repo: String, runId: Int64) async throws -> String {
        let jobsData = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/actions/runs/\(runId)/jobs", pat: pat, body: nil)
        let jobsObj = try JSONSerialization.jsonObject(with: jobsData) as? [String: Any]
        let jobs = (jobsObj?["jobs"] as? [[String: Any]]) ?? []
        let failedJob = jobs.first { ($0["conclusion"] as? String) == "failure" } ?? jobs.first
        guard let jobId = failedJob?["id"] as? Int64 else {
            throw NSError(domain: "PushGit", code: -1, userInfo: [NSLocalizedDescriptionKey: "未找到任务日志"])
        }
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/actions/jobs/\(jobId)/logs") else { return "" }
        var req = URLRequest(url: url, timeoutInterval: 30)
        req.setValue("Bearer \(pat.trimmingCharacters(in: .whitespacesAndNewlines))", forHTTPHeaderField: "Authorization")
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        let (data, _) = try await URLSession.shared.data(for: req)
        return String(data: data, encoding: .utf8) ?? ""
    }

    func fetchNextReleaseVersion(_ pat: String, repo: String) async -> String? {
        if let d = try? await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/releases/latest", pat: pat, body: nil),
           let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
           let tag = obj["tag_name"] as? String, !tag.isEmpty {
            let clean = tag.trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
            var parts = clean.split(separator: ".").compactMap { Int($0) }
            if !parts.isEmpty {
                while parts.count < 3 { parts.append(0) }
                parts[parts.count - 1] += 1
                return parts.map(String.init).joined(separator: ".")
            }
        }
        return nil
    }

    func fetchChangelog(_ pat: String, repo: String, branch: String, file: String = "CHANGELOG_IOS.md") async -> String? {
        guard let d = try? await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/contents/\(file)?ref=\(branch)", pat: pat, body: nil),
              let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
              let b64 = (obj["content"] as? String)?.replacingOccurrences(of: "\n", with: ""),
              let raw = Data(base64Encoded: b64) else { return nil }
        guard let text = String(data: raw, encoding: .utf8) else { return nil }
        let cleaned = text.components(separatedBy: .newlines)
            .drop(while: { let t = $0.trimmingCharacters(in: .whitespaces); return t.isEmpty || t.hasPrefix("# ") })
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }

    func listRepoContents(_ pat: String, repo: String, branch: String, path: String) async throws -> [RepoContentItem] {
        let encodedPath = path.split(separator: "/").map { String($0).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? String($0) }.joined(separator: "/")
        let urlStr = encodedPath.isEmpty
            ? "https://api.github.com/repos/\(repo)/contents?ref=\(branch)"
            : "https://api.github.com/repos/\(repo)/contents/\(encodedPath)?ref=\(branch)"
        let data = try await apiRequest(method: "GET", urlStr: urlStr, pat: pat, body: nil)
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { o in
            guard let name = o["name"] as? String,
                  let p = o["path"] as? String,
                  let type = o["type"] as? String,
                  let sha = o["sha"] as? String else { return nil }
            let size = (o["size"] as? NSNumber)?.int64Value ?? 0
            return RepoContentItem(name: name, path: p, type: type, sha: sha, size: size)
        }.sorted { ($0.type == "dir" ? 0 : 1, $0.name.lowercased()) < ($1.type == "dir" ? 0 : 1, $1.name.lowercased()) }
    }

    func fetchDirectoryLastModifiedMap(_ pat: String, repo: String, branch: String, paths: [String]) async -> [String: String] {
        let isoParser = DateFormatter()
        isoParser.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        isoParser.timeZone = TimeZone(abbreviation: "UTC")
        let localFmt = DateFormatter()
        localFmt.dateFormat = "MM-dd HH:mm"
        localFmt.timeZone = TimeZone.current

        var out: [String: String] = [:]
        await withTaskGroup(of: (String, String?).self) { group in
            for p in paths.prefix(30) {
                group.addTask {
                    let q = p.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? p
                    guard let d = try? await self.apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/commits?sha=\(branch)&path=\(q)&per_page=1", pat: pat, body: nil),
                          let arr = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]],
                          let iso = ((arr.first?["commit"] as? [String: Any])?["committer"] as? [String: Any])?["date"] as? String,
                          let date = isoParser.date(from: iso) else { return (p, nil) }
                    return (p, localFmt.string(from: date))
                }
            }
            for await (k, v) in group {
                if let v { out[k] = v }
            }
        }
        return out
    }

    func fetchRepoFileText(_ pat: String, repo: String, branch: String, path: String) async throws -> (String, String) {
        let encodedPath = path.split(separator: "/").map { String($0).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? String($0) }.joined(separator: "/")
        let data = try await apiRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/contents/\(encodedPath)?ref=\(branch)", pat: pat, body: nil)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let sha = (obj?["sha"] as? String) ?? ""
        let b64 = (obj?["content"] as? String ?? "").replacingOccurrences(of: "\n", with: "")
        guard let raw = Data(base64Encoded: b64), let text = String(data: raw, encoding: .utf8) else {
            throw NSError(domain: "PushGit", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法解码文件内容"])
        }
        return (sha, text)
    }

    func updateRepoFile(_ pat: String, repo: String, branch: String, path: String, sha: String?, content: String, message: String) async throws {
        let encodedPath = path.split(separator: "/").map { String($0).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? String($0) }.joined(separator: "/")
        var body: [String: Any] = [
            "message": message.isEmpty ? "Update \(path) via PushGit" : message,
            "content": Data(content.utf8).base64EncodedString(),
            "branch": branch
        ]
        if let sha, !sha.isEmpty { body["sha"] = sha }
        _ = try await apiRequest(method: "PUT", urlStr: "https://api.github.com/repos/\(repo)/contents/\(encodedPath)", pat: pat, body: body)
    }

    private func computeGitBlobSha1(_ data: Data) -> String {
        let header = Data("blob \(data.count)\0".utf8)
        var hasher = Insecure.SHA1()
        hasher.update(data: header)
        hasher.update(data: data)
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private func parseWorkflowDispatchInputs(_ yaml: String) -> [WorkflowInputField] {
        var result: [WorkflowInputField] = []
        var inDispatch = false
        var inInputs = false
        var inputsIndent = -1
        var currentKey: String?
        var keyIndent = -1
        var desc = ""
        var req = false
        var defVal = ""
        var opts: [String] = []
        var inOptions = false
        var optionsIndent = -1

        func flush() {
            guard let k = currentKey else { return }
            result.append(WorkflowInputField(key: k, description: desc, required: req, defaultValue: defVal, options: opts))
            currentKey = nil; desc = ""; req = false; defVal = ""; opts = []; inOptions = false; optionsIndent = -1
        }

        for raw in yaml.components(separatedBy: .newlines) {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
            let indent = raw.prefix(while: { $0.isWhitespace }).count

            if trimmed.hasPrefix("workflow_dispatch:") {
                inDispatch = true; inInputs = false; continue
            }
            if !inDispatch { continue }
            if trimmed.hasPrefix("inputs:") && !inInputs {
                inInputs = true; inputsIndent = indent; continue
            }
            if inInputs && indent <= inputsIndent { flush(); break }
            if !inInputs { continue }

            if keyIndent == -1 || indent == keyIndent {
                if trimmed.hasSuffix(":") {
                    flush()
                    keyIndent = indent
                    currentKey = String(trimmed.dropLast()).trimmingCharacters(in: .whitespaces)
                }
            } else if indent > keyIndent && currentKey != nil {
                if inOptions {
                    if indent > optionsIndent && trimmed.hasPrefix("-") {
                        let opt = trimmed.dropFirst().trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
                        if !opt.isEmpty { opts.append(opt) }
                        continue
                    } else {
                        inOptions = false
                    }
                }
                if trimmed.hasPrefix("description:") {
                    desc = trimmed.dropFirst("description:".count).trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
                } else if trimmed.hasPrefix("required:") {
                    req = trimmed.dropFirst("required:".count).trimmingCharacters(in: .whitespaces).lowercased() == "true"
                } else if trimmed.hasPrefix("default:") {
                    defVal = trimmed.dropFirst("default:".count).trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
                } else if trimmed.hasPrefix("options:") {
                    inOptions = true; optionsIndent = indent
                }
            }
        }
        flush()
        return result
    }

    private func apiRequest(method: String, urlStr: String, pat: String, body: [String: Any]?) async throws -> Data {
        guard let url = URL(string: urlStr) else {
            throw NSError(domain: "PushGit", code: -1, userInfo: [NSLocalizedDescriptionKey: "无效URL"])
        }
        var req = URLRequest(url: url, timeoutInterval: 25)
        req.httpMethod = method
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("Bearer \(pat.trimmingCharacters(in: .whitespacesAndNewlines))", forHTTPHeaderField: "Authorization")
        req.setValue("Jools-PushGit-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let body {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            let msg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String
            throw NSError(domain: "PushGit", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "GitHub API \(http.statusCode): \(msg ?? "")"])
        }
        return data
    }
}
