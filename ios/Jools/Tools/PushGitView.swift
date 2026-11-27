import SwiftUI
import UniformTypeIdentifiers

struct PushGitView: View {
    @ObservedObject var prefs: AppPreferences
    @StateObject private var engine = PushGitEngine()

    @State private var pat = ""
    @State private var connectedUser: String?
    @State private var savedTokens: [SavedGitToken] = []
    @State private var showTokenManager = false
    @State private var newTokenInput = ""

    @State private var targetRepo = ""
    @State private var repos: [String] = []
    @State private var showRepoEditor = false

    @State private var branch = "main"
    @State private var branches: [String] = ["main"]
    @State private var commitMessage = ""

    @State private var selectedFiles: [PushFileEntry] = []
    @State private var diffSummary: GitDiffSummary?
    @State private var selectedUploadPaths = Set<String>()
    @State private var selectedDeletePaths = Set<String>()
    @State private var syncDeletions = false
    @State private var forceFullPush = false
    @State private var showFileImporter = false

    @State private var isInspecting = false
    @State private var isPushing = false
    @State private var pushDone = 0
    @State private var pushTotal = 0
    @State private var pushStatus = ""
    @State private var hasPendingBuildAfterPush = false

    @State private var workflows: [WorkflowInfo] = []
    @State private var selectedWorkflow: WorkflowInfo?
    @State private var workflowInputs: [WorkflowInputField] = []
    @State private var workflowInputValues: [String: String] = [:]
    @State private var showRunWorkflowSheet = false
    @State private var isDispatching = false

    @State private var workflowRuns: [WorkflowRunInfo] = []
    @State private var observedActiveRunIds = Set<Int64>()
    @State private var dismissedRunIds = Set<Int64>()
    @State private var pendingCancelRun: WorkflowRunInfo?

    @State private var showHistorySheet = false
    @State private var toastMsg: String?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    // Card 1: Account + Repo + Edit
                    VStack(alignment: .leading, spacing: 10) {
                        Text("用户与目标仓库").font(.system(size: 14, weight: .bold))

                        Menu {
                            ForEach(savedTokens) { acc in
                                Button("@\(acc.username) (\(String(acc.token.prefix(8)))...)") {
                                    pat = acc.token
                                    connectedUser = acc.username
                                    diffSummary = nil
                                    Task { await refreshMeta(token: acc.token) }
                                }
                            }
                            Divider()
                            Button("管理 Token...") { showTokenManager = true }
                        } label: {
                            HStack {
                                Text(savedTokens.isEmpty ? "点击右上角钥匙图标保存 Token" : "已存账号: \(connectedUser.map { "@\($0)" } ?? "选择用户") (\(savedTokens.count))")
                                    .font(.system(size: 13))
                                    .foregroundColor(.primary)
                                Spacer()
                                Image(systemName: "chevron.down").font(.system(size: 11))
                            }
                            .padding(12)
                            .background(Color.primary.opacity(0.06))
                            .cornerRadius(12)
                        }

                        HStack(spacing: 8) {
                            Menu {
                                ForEach(repos, id: \.self) { r in
                                    Button(r) {
                                        targetRepo = r
                                        diffSummary = nil
                                        Task {
                                            if let bList = try? await engine.listBranches(pat, repo: r), !bList.isEmpty {
                                                branches = bList
                                                if !bList.contains(branch) {
                                                    branch = bList.contains("main") ? "main" : bList[0]
                                                }
                                            }
                                        }
                                    }
                                }
                            } label: {
                                HStack {
                                    Text(targetRepo.isEmpty ? "选择目标仓库" : targetRepo)
                                        .font(.system(size: 14))
                                        .foregroundColor(.accentColor)
                                        .lineLimit(1)
                                    Spacer()
                                    Text(repos.isEmpty ? "待拉取" : "▾ (\(repos.count))")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }
                                .padding(12)
                                .background(Color.primary.opacity(0.06))
                                .cornerRadius(12)
                            }

                            Button {
                                showRepoEditor = true
                            } label: {
                                Label("编辑", systemImage: "pencil")
                                    .font(.system(size: 13, weight: .semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 11)
                            }
                            .buttonStyle(.bordered)
                            .disabled(pat.isEmpty || targetRepo.isEmpty)
                        }
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)

                    // Card 2: Branch & Commit
                    VStack(alignment: .leading, spacing: 10) {
                        Text("分支与提交信息").font(.system(size: 14, weight: .bold))
                        Menu {
                            ForEach(branches, id: \.self) { b in
                                Button(b) { branch = b; diffSummary = nil }
                            }
                        } label: {
                            HStack {
                                Text("分支: \(branch)").font(.system(size: 14)).foregroundColor(.primary)
                                Spacer()
                                Text("▾ (\(branches.count))").font(.system(size: 12)).foregroundColor(.secondary)
                            }
                            .padding(12)
                            .background(Color.primary.opacity(0.06))
                            .cornerRadius(12)
                        }
                        TextField("Commit Message", text: $commitMessage)
                            .textFieldStyle(.roundedBorder)
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)

                    // Card 3: Pick files & Diff
                    VStack(alignment: .leading, spacing: 10) {
                        Text("选择上传内容").font(.system(size: 14, weight: .bold))
                        Button {
                            showFileImporter = true
                        } label: {
                            Label("选择文件 / 文件夹", systemImage: "folder.badge.plus")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.borderedProminent)

                        if let diff = diffSummary {
                            Text("比对结果：新增 \(diff.addedFiles.count) · 修改 \(diff.modifiedFiles.count) · 远程独有 \(diff.deletedPaths.count) · 未变 \(diff.unchangedCount)")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.accentColor)

                            Toggle("全量覆盖推送", isOn: $forceFullPush).font(.system(size: 12))
                            if !diff.deletedPaths.isEmpty {
                                Toggle("同步删除远程独有文件 (\(diff.deletedPaths.count))", isOn: $syncDeletions)
                                    .font(.system(size: 12))
                            }

                            VStack(spacing: 4) {
                                ForEach(diff.addedFiles) { f in
                                    diffCheckRow(label: "新增", color: .green, path: f.relativePath, checked: selectedUploadPaths.contains(f.relativePath)) { chk in
                                        if chk { selectedUploadPaths.insert(f.relativePath) } else { selectedUploadPaths.remove(f.relativePath) }
                                    }
                                }
                                ForEach(diff.modifiedFiles) { f in
                                    diffCheckRow(label: "修改", color: .orange, path: f.relativePath, checked: selectedUploadPaths.contains(f.relativePath)) { chk in
                                        if chk { selectedUploadPaths.insert(f.relativePath) } else { selectedUploadPaths.remove(f.relativePath) }
                                    }
                                }
                            }
                        } else if !selectedFiles.isEmpty {
                            Text("已就绪 \(selectedFiles.count) 个文件，请先比对差异")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.accentColor)
                        }
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)

                    if isPushing && pushTotal > 0 {
                        ProgressView(value: Double(pushDone), total: Double(pushTotal))
                    }
                    if !pushStatus.isEmpty {
                        Text(pushStatus).font(.system(size: 12)).foregroundColor(.secondary)
                    }
                }
                .padding(16)
            }

            // Bottom Action Area
            VStack(spacing: 8) {
                let readyCount: Int = {
                    guard let d = diffSummary else { return 0 }
                    let u = forceFullPush ? selectedFiles.count : d.uploadFiles.filter { selectedUploadPaths.contains($0.relativePath) }.count
                    let del = syncDeletions ? d.deletedPaths.filter { selectedDeletePaths.contains($0) }.count : 0
                    return u + del
                }()

                Button {
                    engine.saveConfig(pat: pat, repo: targetRepo, branch: branch, message: commitMessage)
                    if diffSummary == nil {
                        Task { await runInspect() }
                    } else {
                        Task { await runPush() }
                    }
                } label: {
                    Text(isInspecting ? "正在比对..." : (isPushing ? "正在推送..." : (diffSummary == nil ? "比对仓库差异" : "Push 变动文件 (\(readyCount))")))
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isInspecting || isPushing || pat.isEmpty || selectedFiles.isEmpty || (diffSummary != nil && readyCount == 0))

                HStack(spacing: 8) {
                    Button {
                        Task { await openWorkflowDialog() }
                    } label: {
                        Label("Run Workflow", systemImage: "play.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(hasPendingBuildAfterPush ? Color(red: 0.106, green: 0.557, blue: 0.243) : .secondary)
                    .disabled(pat.isEmpty || targetRepo.isEmpty || isDispatching)

                    Button {
                        Task {
                            if let runs = try? await engine.listWorkflowRuns(pat, repo: targetRepo, page: 1, perPage: 15) {
                                workflowRuns = runs
                            }
                            showHistorySheet = true
                        }
                    } label: {
                        Text("构建历史")
                            .font(.system(size: 13, weight: .medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                    }
                    .buttonStyle(.bordered)
                    .disabled(pat.isEmpty || targetRepo.isEmpty)
                }

                // Active & recently completed runs observed on this screen
                let displayRuns = workflowRuns.filter { run in
                    run.status != "completed" || (observedActiveRunIds.contains(run.id) && !dismissedRunIds.contains(run.id))
                }
                ForEach(displayRuns) { run in
                    let isRunning = run.status != "completed"
                    HStack {
                        Text("#\(run.runNumber) · \(run.name) · \(isRunning ? "构建中" : (run.conclusion == "success" ? "构建成功" : "构建失败"))")
                            .font(.system(size: 12, weight: .medium))
                            .lineLimit(1)
                        Spacer()
                        if isRunning {
                            Button("取消") { pendingCancelRun = run }
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundColor(.red)
                        } else {
                            Button("完成") { dismissedRunIds.insert(run.id) }
                                .font(.system(size: 11.5, weight: .semibold))
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color.accentColor.opacity(0.12))
                    .cornerRadius(10)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
        .navigationTitle("PushGit")
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button { showTokenManager = true } label: { Image(systemName: "key.fill") }
                Button { Task { await refreshMeta(token: pat) } } label: { Image(systemName: "arrow.clockwise") }
            }
        }
        .onAppear {
            pat = engine.loadSavedToken()
            targetRepo = engine.loadSavedRepo()
            branch = engine.loadSavedBranch()
            commitMessage = engine.loadSavedMessage()
            savedTokens = engine.getSavedTokens()
            if !pat.isEmpty {
                Task { await refreshMeta(token: pat) }
            }
        }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.item, .folder], allowsMultipleSelection: true) { res in
            if case .success(let urls) = res {
                var list: [PushFileEntry] = []
                for u in urls {
                    let acc = u.startAccessingSecurityScopedResource()
                    collectLocalUrl(u, base: "", into: &list)
                    if acc { u.stopAccessingSecurityScopedResource() }
                }
                selectedFiles = list
                diffSummary = nil
                pushStatus = "已读取 \(list.count) 个文件"
            }
        }
        .sheet(isPresented: $showTokenManager) { tokenManagerSheet }
        .sheet(isPresented: $showRunWorkflowSheet) { runWorkflowSheet }
        .sheet(isPresented: $showRepoEditor) {
            RepoBrowserEditorSheet(engine: engine, pat: pat, repo: targetRepo, branch: branch)
        }
        .sheet(isPresented: $showHistorySheet) {
            BuildHistorySheet(engine: engine, pat: pat, repo: targetRepo, initialRuns: workflowRuns)
        }
        .alert("确认取消构建", isPresented: Binding(get: { pendingCancelRun != nil }, set: { if !$0 { pendingCancelRun = nil } })) {
            Button("确认取消", role: .destructive) {
                if let target = pendingCancelRun {
                    pendingCancelRun = nil
                    Task {
                        try? await engine.cancelWorkflowRun(pat, repo: targetRepo, runId: target.id)
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        if let runs = try? await engine.listWorkflowRuns(pat, repo: targetRepo) {
                            workflowRuns = runs
                        }
                    }
                }
            }
            Button("暂不取消", role: .cancel) { pendingCancelRun = nil }
        } message: {
            Text("确定要取消 #\(pendingCancelRun?.runNumber ?? 0) · \(pendingCancelRun?.name ?? "") 吗？")
        }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }

    @ViewBuilder
    private func diffCheckRow(label: String, color: Color, path: String, checked: Bool, onToggle: @escaping (Bool) -> Void) -> some View {
        Button { onToggle(!checked) } label: {
            HStack(spacing: 6) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 15))
                    .foregroundColor(checked ? .accentColor : .secondary)
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(color)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(color.opacity(0.15))
                    .cornerRadius(5)
                Text(path)
                    .font(.system(size: 11))
                    .foregroundColor(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(Color.primary.opacity(0.05))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }

    private func collectLocalUrl(_ url: URL, base: String, into out: inout [PushFileEntry]) {
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir) else { return }
        let name = url.lastPathComponent
        if name == ".git" || name == ".gradle" || name == "build" || name == ".DS_Store" { return }
        let rel = base.isEmpty ? name : "\(base)/\(name)"
        if isDir.boolValue {
            if let children = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: nil) {
                for c in children { collectLocalUrl(c, base: rel, into: &out) }
            }
        } else if let d = try? Data(contentsOf: url) {
            out.append(PushFileEntry(relativePath: rel, data: d))
        }
    }

    private func refreshMeta(token: String) async {
        guard !token.isEmpty else { return }
        connectedUser = try? await engine.verifyToken(token)
        if let list = try? await engine.listRepos(token), !list.isEmpty {
            repos = list
            if targetRepo.isEmpty || !list.contains(targetRepo) { targetRepo = list[0] }
        }
        if let bList = try? await engine.listBranches(token, repo: targetRepo), !bList.isEmpty {
            branches = bList
            if !bList.contains(branch) { branch = bList.contains("main") ? "main" : bList[0] }
        }
        if let wfs = try? await engine.listWorkflows(token, repo: targetRepo) {
            workflows = wfs
            if selectedWorkflow == nil { selectedWorkflow = wfs.first }
        }
        if let runs = try? await engine.listWorkflowRuns(token, repo: targetRepo) {
            workflowRuns = runs
            runs.filter { $0.status != "completed" }.forEach { observedActiveRunIds.insert($0.id) }
        }
    }

    private func runInspect() async {
        isInspecting = true
        pushStatus = "正在比对仓库文件..."
        do {
            let diff = try await engine.inspectChanges(pat: pat, repo: targetRepo, branch: branch, files: selectedFiles)
            diffSummary = diff
            selectedUploadPaths = Set(diff.uploadFiles.map { $0.relativePath })
            selectedDeletePaths = Set(diff.deletedPaths)
            pushStatus = diff.uploadFiles.isEmpty && diff.deletedPaths.isEmpty
                ? "与仓库完全一致，无需推送"
                : "比对完成：\(diff.addedFiles.count) 新增，\(diff.modifiedFiles.count) 修改"
        } catch {
            pushStatus = "比对失败: \(error.localizedDescription)"
        }
        isInspecting = false
    }

    private func runPush() async {
        guard let d = diffSummary else { return }
        isPushing = true
        pushDone = 0
        let filtered = GitDiffSummary(
            headSha: d.headSha,
            baseTreeSha: d.baseTreeSha,
            addedFiles: d.addedFiles.filter { selectedUploadPaths.contains($0.relativePath) },
            modifiedFiles: d.modifiedFiles.filter { selectedUploadPaths.contains($0.relativePath) },
            deletedPaths: d.deletedPaths.filter { selectedDeletePaths.contains($0) },
            unchangedCount: d.unchangedCount
        )
        pushTotal = forceFullPush ? selectedFiles.count : filtered.uploadFiles.count
        do {
            let sha = try await engine.pushDiff(
                pat: pat,
                repo: targetRepo,
                branch: branch,
                message: commitMessage,
                diff: filtered,
                syncDeletions: syncDeletions,
                overrideFiles: forceFullPush ? selectedFiles : nil
            ) { done, total, cur in
                DispatchQueue.main.async {
                    pushDone = done
                    pushTotal = total
                    pushStatus = "推送中 (\(done)/\(total)): \(cur)"
                }
            }
            pushStatus = "推送成功 (\(String(sha.prefix(7))))"
            hasPendingBuildAfterPush = true
            diffSummary = nil
        } catch {
            pushStatus = "推送失败: \(error.localizedDescription)"
        }
        isPushing = false
    }

    private func openWorkflowDialog() async {
        guard let wf = selectedWorkflow ?? workflows.first else {
            toastMsg = "未找到可用 Workflow"
            return
        }
        selectedWorkflow = wf
        let nextVer = await engine.fetchNextReleaseVersion(pat, repo: targetRepo)
        let changelog = await engine.fetchChangelog(pat, repo: targetRepo, branch: branch)
        let fields = (try? await engine.fetchWorkflowInputs(pat, repo: targetRepo, workflowPath: wf.path, branch: branch)) ?? []
        workflowInputs = fields
        workflowInputValues = [:]
        for f in fields {
            if !f.options.isEmpty {
                workflowInputValues[f.key] = f.options.contains(f.defaultValue) ? f.defaultValue : f.options[0]
            } else if f.key.lowercased().contains("version"), let nextVer {
                workflowInputValues[f.key] = nextVer
            } else if f.key.lowercased().contains("changelog"), let changelog {
                workflowInputValues[f.key] = changelog
            } else {
                workflowInputValues[f.key] = f.defaultValue
            }
        }
        showRunWorkflowSheet = true
    }

    private var tokenManagerSheet: some View {
        NavigationStack {
            List {
                Section("添加新 Token") {
                    HStack {
                        TextField("输入 ghp_... Token", text: $newTokenInput)
                        Button("保存") {
                            let t = newTokenInput.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !t.isEmpty else { return }
                            Task {
                                if let uname = try? await engine.verifyToken(t) {
                                    savedTokens = engine.saveTokenEntry(username: uname, token: t)
                                    pat = t
                                    connectedUser = uname
                                    newTokenInput = ""
                                    await refreshMeta(token: t)
                                } else {
                                    toastMsg = "Token 无效"
                                }
                            }
                        }
                    }
                }
                Section("已保存账号") {
                    ForEach(savedTokens) { item in
                        Button {
                            pat = item.token
                            connectedUser = item.username
                            showTokenManager = false
                            Task { await refreshMeta(token: item.token) }
                        } label: {
                            VStack(alignment: .leading) {
                                Text("@\(item.username)").font(.system(size: 14, weight: .semibold))
                                Text("\(String(item.token.prefix(10)))...").font(.system(size: 11)).foregroundColor(.secondary)
                            }
                        }
                    }
                    .onDelete { idxs in
                        idxs.forEach { savedTokens = engine.deleteTokenEntry(id: savedTokens[$0].id) }
                    }
                }
            }
            .navigationTitle("管理 Token")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { showTokenManager = false }
                }
            }
        }
    }

    private var runWorkflowSheet: some View {
        NavigationStack {
            Form {
                Section("分支: \(branch)") {
                    ForEach(workflowInputs) { field in
                        if !field.options.isEmpty {
                            Picker(field.key, selection: Binding(
                                get: { workflowInputValues[field.key] ?? field.options[0] },
                                set: { workflowInputValues[field.key] = $0 }
                            )) {
                                ForEach(field.options, id: \.self) { opt in
                                    Text(opt).tag(opt)
                                }
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(field.key).font(.system(size: 12)).foregroundColor(.secondary)
                                TextField(field.description, text: Binding(
                                    get: { workflowInputValues[field.key] ?? "" },
                                    set: { workflowInputValues[field.key] = $0 }
                                ))
                            }
                        }
                    }
                }
            }
            .navigationTitle(selectedWorkflow?.name ?? "Run Workflow")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showRunWorkflowSheet = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isDispatching ? "触发中..." : "Run workflow") {
                        guard let wf = selectedWorkflow else { return }
                        isDispatching = true
                        Task {
                            do {
                                try await engine.dispatchWorkflow(pat, repo: targetRepo, workflowId: wf.id, branch: branch, inputs: workflowInputValues)
                                showRunWorkflowSheet = false
                                hasPendingBuildAfterPush = false
                                toastMsg = "已触发构建"
                                try? await Task.sleep(nanoseconds: 1_500_000_000)
                                if let runs = try? await engine.listWorkflowRuns(pat, repo: targetRepo) {
                                    workflowRuns = runs
                                    runs.filter { $0.status != "completed" }.forEach { observedActiveRunIds.insert($0.id) }
                                }
                            } catch {
                                toastMsg = "触发失败: \(error.localizedDescription)"
                            }
                            isDispatching = false
                        }
                    }
                    .disabled(isDispatching)
                }
            }
        }
    }
}

struct RepoBrowserEditorSheet: View {
    @ObservedObject var engine: PushGitEngine
    let pat: String
    let repo: String
    let branch: String
    @Environment(\.dismiss) private var dismiss

    @State private var currentPath = ""
    @State private var items: [RepoContentItem] = []
    @State private var itemDates: [String: String] = [:]
    @State private var isLoading = false

    @State private var editingPath: String?
    @State private var editingSha: String?
    @State private var editingText = ""
    @State private var commitMsg = ""
    @State private var isSaving = false

    var body: some View {
        NavigationStack {
            Group {
                if let ePath = editingPath {
                    VStack(spacing: 10) {
                        Text(ePath).font(.system(size: 12, weight: .semibold)).frame(maxWidth: .infinity, alignment: .leading)
                        TextEditor(text: $editingText)
                            .font(.system(size: 12, design: .monospaced))
                            .padding(6)
                            .background(Color.primary.opacity(0.05))
                            .cornerRadius(10)
                        TextField("Commit Message", text: $commitMsg).textFieldStyle(.roundedBorder)
                        HStack {
                            Button("返回") { editingPath = nil }.buttonStyle(.bordered)
                            Spacer()
                            Button(isSaving ? "提交中..." : "保存提交") {
                                isSaving = true
                                Task {
                                    try? await engine.updateRepoFile(pat, repo: repo, branch: branch, path: ePath, sha: editingSha, content: editingText, message: commitMsg)
                                    isSaving = false
                                    editingPath = nil
                                    await loadDir(currentPath)
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(isSaving)
                        }
                    }
                    .padding(14)
                } else {
                    List {
                        if !currentPath.isEmpty {
                            Button(".. (返回上级)") {
                                let parent = currentPath.contains("/") ? String(currentPath.split(separator: "/").dropLast().joined(separator: "/")) : ""
                                Task { await loadDir(parent) }
                            }
                        }
                        ForEach(items) { item in
                            Button {
                                if item.type == "dir" {
                                    Task { await loadDir(item.path) }
                                } else {
                                    Task {
                                        if let (sha, text) = try? await engine.fetchRepoFileText(pat, repo: repo, branch: branch, path: item.path) {
                                            editingPath = item.path
                                            editingSha = sha
                                            editingText = text
                                            commitMsg = "Update \(item.name) via PushGit"
                                        }
                                    }
                                }
                            } label: {
                                HStack {
                                    Image(systemName: item.type == "dir" ? "folder.fill" : "doc.text")
                                        .foregroundColor(item.type == "dir" ? .orange : .accentColor)
                                    Text(item.name).font(.system(size: 13.5)).lineLimit(1)
                                    Spacer()
                                    if let dt = itemDates[item.path] {
                                        Text(dt).font(.system(size: 10.5)).foregroundColor(.secondary)
                                    }
                                    if item.type != "dir" {
                                        Text(item.size < 1024 ? "\(item.size) B" : String(format: "%.1f KB", Double(item.size) / 1024.0))
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(repo)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task { await loadDir("") }
        }
    }

    private func loadDir(_ path: String) async {
        isLoading = true
        if let list = try? await engine.listRepoContents(pat, repo: repo, branch: branch, path: path) {
            currentPath = path
            items = list
            itemDates = await engine.fetchDirectoryLastModifiedMap(pat, repo: repo, branch: branch, paths: list.map { $0.path })
        }
        isLoading = false
    }
}

struct BuildHistorySheet: View {
    @ObservedObject var engine: PushGitEngine
    let pat: String
    let repo: String
    let initialRuns: [WorkflowRunInfo]
    @Environment(\.dismiss) private var dismiss

    @State private var runs: [WorkflowRunInfo] = []
    @State private var page = 1
    @State private var hasMore = true
    @State private var isLoadingMore = false
    @State private var expandedRunId: Int64?
    @State private var logsCache: [Int64: String] = [:]

    var body: some View {
        NavigationStack {
            List {
                ForEach(runs) { run in
                    VStack(alignment: .leading, spacing: 8) {
                        Button {
                            if expandedRunId == run.id {
                                expandedRunId = nil
                            } else {
                                expandedRunId = run.id
                                if logsCache[run.id] == nil {
                                    Task {
                                        logsCache[run.id] = (try? await engine.fetchRunLog(pat, repo: repo, runId: run.id)) ?? "获取日志失败"
                                    }
                                }
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("#\(run.runNumber) · \(run.name)").font(.system(size: 13.5, weight: .semibold))
                                    Text("\(run.branch) · \(formatLocalTime(run.createdAt))")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Text(run.status != "completed" ? "运行中" : (run.conclusion == "success" ? "成功" : "失败"))
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(run.status != "completed" ? .blue : (run.conclusion == "success" ? .green : .red))
                            }
                        }
                        .buttonStyle(.plain)

                        if expandedRunId == run.id {
                            let logText = logsCache[run.id] ?? ""
                            let lines = logText.isEmpty ? [] : logText.components(separatedBy: .newlines)
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(logText.isEmpty ? "正在拉取日志..." : "日志 (\(lines.count) 行)")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(.accentColor)
                                    Spacer()
                                    if !logText.isEmpty {
                                        Button("复制") { UIPasteboard.general.string = logText }
                                            .font(.system(size: 11))
                                    }
                                }
                                if !lines.isEmpty {
                                    ScrollView {
                                        LazyVStack(alignment: .leading, spacing: 2) {
                                            ForEach(Array(lines.enumerated()), id: \.offset) { idx, l in
                                                HStack(alignment: .top, spacing: 6) {
                                                    Text("\(idx + 1)")
                                                        .font(.system(size: 9.5, design: .monospaced))
                                                        .foregroundColor(.secondary)
                                                        .frame(width: 28, alignment: .trailing)
                                                    Text(l)
                                                        .font(.system(size: 10, design: .monospaced))
                                                        .textSelection(.enabled)
                                                }
                                            }
                                        }
                                    }
                                    .frame(maxHeight: 220)
                                }
                            }
                            .padding(8)
                            .background(Color.primary.opacity(0.05))
                            .cornerRadius(8)
                        }
                    }
                }

                if hasMore {
                    Button {
                        guard !isLoadingMore else { return }
                        isLoadingMore = true
                        let next = page + 1
                        Task {
                            if let more = try? await engine.listWorkflowRuns(pat, repo: repo, page: next, perPage: 15) {
                                if more.isEmpty {
                                    hasMore = false
                                } else {
                                    let existing = Set(runs.map { $0.id })
                                    runs.append(contentsOf: more.filter { !existing.contains($0.id) })
                                    page = next
                                    hasMore = more.count >= 15
                                }
                            }
                            isLoadingMore = false
                        }
                    } label: {
                        Text(isLoadingMore ? "加载中..." : "点击加载更多 (+15条)")
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .navigationTitle("构建历史 (\(runs.count))")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .onAppear {
                runs = initialRuns
                hasMore = initialRuns.count >= 15
            }
        }
    }

    private func formatLocalTime(_ iso: String) -> String {
        let p = DateFormatter()
        p.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        p.timeZone = TimeZone(abbreviation: "UTC")
        guard let d = p.date(from: iso) else { return iso }
        let out = DateFormatter()
        out.dateFormat = "yyyy-MM-dd HH:mm:ss"
        out.timeZone = TimeZone.current
        return out.string(from: d)
    }
}
