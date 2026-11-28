import SwiftUI
import UIKit
import UniformTypeIdentifiers
import Security
import CryptoKit
import Citadel
import NIOCore
import NIOFoundationCompat


struct SshTerminalBlock: Identifiable, Equatable {
    let id: String = UUID().uuidString
    let command: String
    var output: String
    var isError: Bool = false
}

struct SshRemoteFileItem: Identifiable, Equatable {
    var id: String { fullPath }
    let name: String
    let fullPath: String
    let isDir: Bool
    let size: UInt64
    let permissionsText: String
    let modifiedAt: Date?
}

struct SshServerStatusInfo {
    var osInfo: String = "未知系统"
    var uptime: String = "--"
    var memoryInfo: String = "--"
    var diskInfo: String = "--"
}

private enum SshIosPage {
    case serverList
    case serverEditor
    case keyManager
    case keyEditor
    case terminal
    case sftpFiles
}

struct SshView: View {
    @ObservedObject var prefs: AppPreferences
    @ObservedObject private var repo = SshRepository.shared
    @Environment(\.dismiss) private var dismiss

    @State private var page: SshIosPage = .serverList

    // Host editor draft
    @State private var editingServerId: String?
    @State private var draftName = ""
    @State private var draftHost = ""
    @State private var draftPort = "22"
    @State private var draftUsername = "root"
    @State private var draftAuthType: SshAuthType = .password
    @State private var draftPassword = ""
    @State private var draftKeyId = ""
    @State private var draftExistingPassphrase = ""
    @State private var draftPassphraseInput = ""
    @State private var draftLastConnected: TimeInterval = 0

    // Key editor state
    @State private var keyReturnToServerEditor = false
    @State private var keyNameInput = ""
    @State private var keyPemInput = ""
    @State private var showKeyFileImporter = false
    @State private var generatedPublicKey = ""
    @State private var editingKeyItem: SshKeyItem?

    // Active connection
    @State private var activeProfile: SshServerProfile?
    @State private var client: SSHClient?
    @State private var isConnecting = false
    @State private var statusInfo = SshServerStatusInfo()

    // Terminal
    @State private var terminalLog = ""
    @State private var terminalBlocks: [SshTerminalBlock] = []
    @State private var commandInput = ""
    @State private var isRunningCmd = false
    @FocusState private var isTerminalInputFocused: Bool
    @State private var showQuickCmdEditor = false
    @State private var editingQuickCmd: SshQuickCommand?
    @State private var quickCmdTitleInput = ""
    @State private var quickCmdBodyInput = ""

    // SFTP
    @State private var currentPath = "/"
    @State private var remoteFiles: [SshRemoteFileItem] = []
    @State private var isLoadingFiles = false
    @State private var showUploadImporter = false
    @State private var showMkdirAlert = false
    @State private var mkdirInput = ""
    @State private var selectedFile: SshRemoteFileItem?
    @State private var showFileActions = false
    @State private var renameTarget: SshRemoteFileItem?
    @State private var renameInput = ""
    @State private var previewFile: SshRemoteFileItem?
    @State private var previewContent = ""
    @State private var shareFileURL: URL?
    @State private var toastMsg: String?

    var body: some View {
        VStack(spacing: 0) {
            if isConnecting {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("正在连接 SSH 服务器...")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                switch page {
                case .serverList: serverListView
                case .serverEditor: serverEditorView
                case .keyManager: keyManagerView
                case .keyEditor: keyEditorView
                case .terminal: terminalView
                case .sftpFiles: sftpFilesView
                }
            }
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .navigationTitle(navTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(page != .serverList)
        .toolbar {
            if page != .serverList {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        handleBack()
                    } label: {
                        HStack(spacing: 3) {
                            Image(systemName: "chevron.left")
                            Text("返回")
                        }
                    }
                }
            }
            ToolbarItemGroup(placement: .primaryAction) {
                toolbarActions
            }
        }
        .onDisappear { disconnect() }
        .sheet(item: $editingKeyItem) { keyItem in
            SshKeyDetailSheet(keyItem: keyItem) { updated in
                repo.upsertKey(updated)
                toastMsg = "密钥已更新保存"
            }
        }
        .fileImporter(
            isPresented: $showKeyFileImporter,
            allowedContentTypes: [.data, .plainText],
            allowsMultipleSelection: false
        ) { res in
            if case .success(let urls) = res, let u = urls.first {
                let acc = u.startAccessingSecurityScopedResource()
                defer { if acc { u.stopAccessingSecurityScopedResource() } }
                if let str = try? String(contentsOf: u, encoding: .utf8), !str.isEmpty {
                    keyPemInput = str
                    if keyNameInput.isEmpty {
                        keyNameInput = u.deletingPathExtension().lastPathComponent
                    }
                }
            }
        }
        .fileImporter(
            isPresented: $showUploadImporter,
            allowedContentTypes: [.item],
            allowsMultipleSelection: false
        ) { res in
            if case .success(let urls) = res, let u = urls.first {
                uploadLocalFile(u)
            }
        }
        .alert("新建文件夹", isPresented: $showMkdirAlert) {
            TextField("文件夹名称", text: $mkdirInput)
            Button("创建") { createRemoteDir() }
            Button("取消", role: .cancel) {}
        }
        .alert("重命名", isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
            TextField("新名称", text: $renameInput)
            Button("确定") { performRename() }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog(selectedFile?.name ?? "文件操作", isPresented: $showFileActions, titleVisibility: .visible) {
            if let f = selectedFile {
                Button("下载并导出") { downloadRemoteFile(f) }
                Button("查看 / 编辑文本") { openTextPreview(f) }
                Button("重命名") {
                    renameTarget = f
                    renameInput = f.name
                }
                Button("删除", role: .destructive) { deleteRemoteItem(f) }
            }
            Button("取消", role: .cancel) {}
        }
        .sheet(item: $previewFile) { f in
            NavigationStack {
                TextEditor(text: $previewContent)
                    .font(.system(size: 13, design: .monospaced))
                    .padding(10)
                    .navigationTitle(f.name)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { previewFile = nil }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("保存") { saveTextPreview(f) }
                        }
                    }
            }
        }
        .sheet(isPresented: Binding(get: { shareFileURL != nil }, set: { if !$0 { shareFileURL = nil } })) {
            if let u = shareFileURL {
                SshShareSheet(items: [u])
            }
        }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }

    private var navTitle: String {
        switch page {
        case .serverList: return "SSH 工具"
        case .serverEditor: return editingServerId == nil ? "添加主机" : "编辑主机"
        case .keyManager: return "私钥管理"
        case .keyEditor: return "添加新密钥"
        case .terminal: return activeProfile?.name ?? "SSH 终端"
        case .sftpFiles: return "文件管理"
        }
    }

    @ViewBuilder
    private var toolbarActions: some View {
        switch page {
        case .serverList:
            Button { page = .keyManager } label: { Image(systemName: "key.fill") }
            Button { openServerEditor(nil) } label: { Image(systemName: "plus") }
        case .keyManager:
            Button { openKeyEditor(returnToServer: false) } label: { Image(systemName: "plus") }
        case .terminal:
            Button {
                page = .sftpFiles
                refreshFiles(path: currentPath)
            } label: {
                Image(systemName: "folder.fill")
            }
            Button { refreshStatus() } label: { Image(systemName: "arrow.clockwise") }
            Button { disconnect() } label: { Image(systemName: "xmark.circle").foregroundColor(.red) }
        case .sftpFiles:
            Button { refreshFiles(path: currentPath) } label: { Image(systemName: "arrow.clockwise") }
            Button { showUploadImporter = true } label: { Image(systemName: "square.and.arrow.up") }
            Button {
                mkdirInput = ""
                showMkdirAlert = true
            } label: { Image(systemName: "folder.badge.plus") }
        default:
            EmptyView()
        }
    }

    private func handleBack() {
        switch page {
        case .serverList: dismiss()
        case .serverEditor: page = .serverList
        case .keyManager: page = .serverList
        case .keyEditor: page = keyReturnToServerEditor ? .serverEditor : .keyManager
        case .sftpFiles: page = .terminal
        case .terminal: disconnect()
        }
    }

    // MARK: - Server List
    private var serverListView: some View {
        ScrollView {
            VStack(spacing: 12) {
                if repo.servers.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "terminal.fill")
                            .font(.system(size: 38))
                            .foregroundColor(.accentColor)
                        Text("暂无保存的服务器").font(.system(size: 16, weight: .bold))
                        Text("支持密码与私钥管理、SFTP 文件管理及终端执行")
                            .font(.system(size: 12.5))
                            .foregroundColor(.secondary)
                        Button("添加主机") { openServerEditor(nil) }
                            .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(28)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                } else {
                    ForEach(repo.servers) { server in
                        Button { connect(server) } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "terminal.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                                    .frame(width: 42, height: 42)
                                    .background(Color.accentColor.gradient)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(server.name).font(.system(size: 15.5, weight: .bold)).foregroundColor(.primary)
                                    Text("\(server.username)@\(server.host):\(server.port)")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                    Text(server.authType.label)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.accentColor)
                                }
                                Spacer()
                                Button { openServerEditor(server) } label: {
                                    Image(systemName: "pencil").padding(6)
                                }
                                .buttonStyle(.plain)
                                Button(role: .destructive) {
                                    repo.deleteServer(id: server.id)
                                } label: {
                                    Image(systemName: "trash").foregroundColor(.red).padding(6)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(14)
                            .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: - Server Editor
    private var serverEditorView: some View {
        let isEditingExistingPass = editingServerId != nil && !draftExistingPassphrase.isEmpty
        return Form {
            Section("主机信息") {
                TextField("主机名称", text: $draftName)
                TextField("主机地址 (IP / 域名)", text: $draftHost)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("用户名", text: $draftUsername)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("端口", text: $draftPort)
                    .keyboardType(.numberPad)
            }

            Section("认证方式") {
                Picker("认证方式", selection: $draftAuthType) {
                    ForEach(SshAuthType.allCases) { t in
                        Text(t.label).tag(t)
                    }
                }
                .pickerStyle(.segmented)

                if draftAuthType == .password {
                    SecureField("登录密码", text: $draftPassword)
                } else {
                    Menu {
                        ForEach(repo.keys) { k in
                            Button {
                                draftKeyId = k.id
                            } label: {
                                if k.id == draftKeyId {
                                    Label(k.name, systemImage: "checkmark")
                                } else {
                                    Text(k.name)
                                }
                            }
                        }
                        if !repo.keys.isEmpty { Divider() }
                        Button {
                            openKeyEditor(returnToServer: true)
                        } label: {
                            Label("添加新密钥", systemImage: "plus")
                        }
                    } label: {
                        HStack {
                            Text("选择私钥").foregroundColor(.primary)
                            Spacer()
                            Text(repo.keys.first(where: { $0.id == draftKeyId })?.name ?? "请选择私钥")
                                .foregroundColor(.secondary)
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }

                    SecureField(
                        isEditingExistingPass ? "输入以更改私钥密码" : "密钥密码（无密码可留空）",
                        text: $draftPassphraseInput
                    )
                }
            }

            Section {
                Button("保存主机配置") {
                    let p = min(65535, max(1, Int(draftPort) ?? 22))
                    let finalPass = draftPassphraseInput.isEmpty ? draftExistingPassphrase : draftPassphraseInput
                    let item = SshServerProfile(
                        id: editingServerId ?? UUID().uuidString,
                        name: draftName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? draftHost.trimmingCharacters(in: .whitespacesAndNewlines) : draftName.trimmingCharacters(in: .whitespacesAndNewlines),
                        host: draftHost.trimmingCharacters(in: .whitespacesAndNewlines),
                        port: p,
                        username: draftUsername.trimmingCharacters(in: .whitespacesAndNewlines),
                        authType: draftAuthType,
                        password: draftPassword,
                        keyId: draftKeyId,
                        keyPassphrase: finalPass,
                        lastConnectedAt: draftLastConnected
                    )
                    repo.upsertServer(item)
                    page = .serverList
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .disabled(
                    draftHost.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    draftUsername.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    (draftAuthType == .privateKey && draftKeyId.isEmpty)
                )
            }
        }
    }

    // MARK: - Key Manager & Editor
    private var keyManagerView: some View {
        List {
            if repo.keys.isEmpty {
                Text("暂无保存的私钥，点击右上角 + 新建或导入")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            } else {
                ForEach(repo.keys) { k in
                    Button {
                        editingKeyItem = k
                    } label: {
                        HStack {
                            Image(systemName: "key.fill").foregroundColor(.accentColor)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(k.name).font(.system(size: 15, weight: .semibold)).foregroundColor(.primary)
                                Text(formatDate(k.createdAt))
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                .onDelete { offsets in
                    for i in offsets { repo.deleteKey(id: repo.keys[i].id) }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var keyEditorView: some View {
        Form {
            Section("密钥信息") {
                TextField("密钥名称", text: $keyNameInput)
                HStack {
                    Button {
                        showKeyFileImporter = true
                    } label: {
                        Label("从文件导入", systemImage: "doc.badge.plus")
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    Spacer()
                    Button {
                        let pair = generateEd25519KeyPair()
                        keyPemInput = pair.privatePem
                        generatedPublicKey = pair.publicLine
                        if keyNameInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            keyNameInput = "Ed25519_\(Int(Date().timeIntervalSince1970) % 10000)"
                        }
                    } label: {
                        Label("新建 Ed25519", systemImage: "wand.and.stars")
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                if !generatedPublicKey.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("已生成公钥（可复制到服务器 authorized_keys）")
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                            Spacer()
                            Button("复制公钥") {
                                UIPasteboard.general.string = generatedPublicKey
                                toastMsg = "已复制公钥"
                            }
                            .font(.system(size: 12, weight: .semibold))
                        }
                        Text(generatedPublicKey)
                            .font(.system(size: 10.5, design: .monospaced))
                            .lineLimit(2)
                    }
                }
            }

            Section("私钥内容 (OpenSSH / PEM)") {
                TextEditor(text: $keyPemInput)
                    .font(.system(size: 12, design: .monospaced))
                    .frame(height: 200)
                    .onChange(of: keyPemInput) { _, nextVal in
                        if let derived = Self.derivePublicKey(from: nextVal) {
                            generatedPublicKey = derived
                        } else if nextVal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            generatedPublicKey = ""
                        }
                    }
            }

            Section {
                Button("保存密钥") {
                    let trimmedPriv = keyPemInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    let trimmedPub = generatedPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    let resolvedPub = trimmedPub.isEmpty ? (Self.derivePublicKey(from: trimmedPriv) ?? "") : trimmedPub
                    let item = SshKeyItem(
                        name: keyNameInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "密钥 \(repo.keys.count + 1)" : keyNameInput.trimmingCharacters(in: .whitespacesAndNewlines),
                        privateKey: trimmedPriv,
                        publicKey: resolvedPub,
                        createdAt: Date().timeIntervalSince1970
                    )
                    repo.upsertKey(item)
                    if keyReturnToServerEditor {
                        draftKeyId = item.id
                        page = .serverEditor
                    } else {
                        page = .keyManager
                    }
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .disabled(keyPemInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }

    // MARK: - Terminal
    private var terminalView: some View {
        VStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("系统: \(statusInfo.osInfo)").font(.system(size: 12, weight: .semibold)).lineLimit(1)
                    Spacer()
                    Text("运行: \(statusInfo.uptime)").font(.system(size: 12)).foregroundColor(.accentColor).lineLimit(1)
                }
                HStack {
                    Text("内存: \(statusInfo.memoryInfo)").font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                    Spacer()
                    Text("磁盘: \(statusInfo.diskInfo)").font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                }
            }
            .padding(12)
            .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 16)
            .padding(.horizontal, 14)
            .padding(.top, 8)

            // 点击黑色终端区域直接弹出输入法输入，回车即发送；长按历史/输出可复制、重跑或收藏为快捷指令
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if !terminalLog.isEmpty {
                            Text(terminalLog.trimmingCharacters(in: .whitespacesAndNewlines))
                                .font(.system(size: 11.5, design: .monospaced))
                                .foregroundColor(Color(red: 0.62, green: 0.70, blue: 0.78))
                                .textSelection(.enabled)
                        }

                        ForEach(terminalBlocks) { block in
                            VStack(alignment: .leading, spacing: 3) {
                                Text("$ \(block.command)")
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                    .foregroundColor(Color(red: 0.35, green: 0.85, blue: 0.55))
                                    .textSelection(.enabled)
                                if !block.output.isEmpty {
                                    Text(block.output)
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(block.isError ? Color(red: 0.98, green: 0.45, blue: 0.45) : Color(red: 0.90, green: 0.93, blue: 0.95))
                                        .textSelection(.enabled)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 2)
                            .contentShape(Rectangle())
                            .contextMenu {
                                Button {
                                    UIPasteboard.general.string = block.command
                                    toastMsg = "已复制命令"
                                } label: {
                                    Label("复制命令", systemImage: "doc.on.doc")
                                }
                                if !block.output.isEmpty {
                                    Button {
                                        UIPasteboard.general.string = block.output
                                        toastMsg = "已复制输出"
                                    } label: {
                                        Label("复制输出", systemImage: "doc.plaintext")
                                    }
                                }
                                Button {
                                    commandInput = block.command
                                    isTerminalInputFocused = true
                                } label: {
                                    Label("填入命令行", systemImage: "text.cursor")
                                }
                                Button {
                                    executeTerminalCommand(overrideCommand: block.command)
                                } label: {
                                    Label("重新执行", systemImage: "arrow.clockwise")
                                }
                                Button {
                                    editingQuickCmd = nil
                                    quickCmdTitleInput = String(block.command.prefix(16))
                                    quickCmdBodyInput = block.command
                                    showQuickCmdEditor = true
                                } label: {
                                    Label("添加到快捷指令", systemImage: "star")
                                }
                                Divider()
                                Button(role: .destructive) {
                                    terminalBlocks.removeAll()
                                    terminalLog = ""
                                } label: {
                                    Label("清空终端", systemImage: "trash")
                                }
                            }
                        }

                        HStack(alignment: .center, spacing: 6) {
                            Text("\(activeProfile?.username ?? "ssh"):\(currentPath)$")
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color(red: 0.35, green: 0.85, blue: 0.55))
                                .lineLimit(1)
                            TextField(
                                "",
                                text: $commandInput,
                                prompt: Text(isRunningCmd ? "执行中..." : "点击终端直接输入，回车发送")
                                    .foregroundColor(Color.white.opacity(0.35))
                            )
                            .focused($isTerminalInputFocused)
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundColor(Color(red: 0.94, green: 0.96, blue: 0.98))
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .submitLabel(.send)
                            .onSubmit {
                                executeTerminalCommand()
                                isTerminalInputFocused = true
                            }
                        }
                        .id("terminal_prompt_bottom")
                        .padding(.top, 4)
                    }
                    .padding(12)
                }
                .background(Color(red: 0.07, green: 0.08, blue: 0.10))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .contentShape(Rectangle())
                .onTapGesture {
                    isTerminalInputFocused = true
                }
                .onChange(of: terminalBlocks.count) { _, _ in
                    withAnimation { proxy.scrollTo("terminal_prompt_bottom", anchor: .bottom) }
                }
            }
            .padding(.horizontal, 14)

            // 快捷指令栏：点击填入命令行并聚焦输入法；长按可立即执行、复制、编辑、删除
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(repo.quickCommands) { qc in
                        Button {
                            commandInput = qc.command
                            isTerminalInputFocused = true
                        } label: {
                            Text(qc.title)
                                .font(.system(size: 11.5, design: .monospaced))
                                .lineLimit(1)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.secondary.opacity(0.14))
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                executeTerminalCommand(overrideCommand: qc.command)
                            } label: {
                                Label("立即执行", systemImage: "play.fill")
                            }
                            Button {
                                UIPasteboard.general.string = qc.command
                                toastMsg = "已复制快捷指令"
                            } label: {
                                Label("复制指令", systemImage: "doc.on.doc")
                            }
                            Button {
                                editingQuickCmd = qc
                                quickCmdTitleInput = qc.title
                                quickCmdBodyInput = qc.command
                                showQuickCmdEditor = true
                            } label: {
                                Label("编辑", systemImage: "pencil")
                            }
                            Button(role: .destructive) {
                                repo.deleteQuickCommand(id: qc.id)
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                    }

                    Button {
                        editingQuickCmd = nil
                        quickCmdTitleInput = ""
                        quickCmdBodyInput = ""
                        showQuickCmdEditor = true
                    } label: {
                        Label("快捷指令", systemImage: "plus")
                            .font(.system(size: 11.5, weight: .semibold))
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.accentColor.opacity(0.14))
                            .foregroundColor(.accentColor)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)

                    if isTerminalInputFocused {
                        Button("收起键盘") {
                            isTerminalInputFocused = false
                        }
                        .font(.system(size: 11.5))
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                    }
                }
                .padding(.horizontal, 14)
            }
            .padding(.bottom, 10)
        }
        .alert(editingQuickCmd == nil ? "添加快捷指令" : "编辑快捷指令", isPresented: $showQuickCmdEditor) {
            TextField("按钮名称（如：查看进程）", text: $quickCmdTitleInput)
            TextField("Shell 指令（如：ps aux）", text: $quickCmdBodyInput)
                .autocapitalization(.none)
                .disableAutocorrection(true)
            Button("保存") {
                let cmd = quickCmdBodyInput.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !cmd.isEmpty else { return }
                let title = quickCmdTitleInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? cmd : quickCmdTitleInput.trimmingCharacters(in: .whitespacesAndNewlines)
                let item = SshQuickCommand(id: editingQuickCmd?.id ?? UUID().uuidString, title: title, command: cmd)
                repo.upsertQuickCommand(item)
            }
            Button("取消", role: .cancel) {}
        }
    }

    // MARK: - SFTP Files
    private var sftpFilesView: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button {
                    if currentPath != "/" {
                        let parent = (currentPath as NSString).deletingLastPathComponent
                        refreshFiles(path: parent.isEmpty ? "/" : parent)
                    }
                } label: {
                    Image(systemName: "arrow.up")
                        .frame(width: 32, height: 32)
                        .background(Color.primary.opacity(0.06))
                        .clipShape(Circle())
                }
                .disabled(currentPath == "/")

                Text(currentPath)
                    .font(.system(size: 13, design: .monospaced))
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial)

            if isLoadingFiles {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(remoteFiles) { f in
                        Button {
                            if f.isDir {
                                refreshFiles(path: f.fullPath)
                            } else {
                                selectedFile = f
                                showFileActions = true
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: f.isDir ? "folder.fill" : "doc.text")
                                    .foregroundColor(f.isDir ? .accentColor : .secondary)
                                    .font(.system(size: 20))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(f.name)
                                        .font(.system(size: 14.5, weight: .medium))
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                    Text(f.isDir ? f.permissionsText : "\(ByteCountFormatter.string(fromByteCount: Int64(f.size), countStyle: .file)) · \(f.permissionsText)")
                                        .font(.system(size: 11.5))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { deleteRemoteItem(f) } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }

    // MARK: - SSH Actions
    private func openServerEditor(_ profile: SshServerProfile?) {
        editingServerId = profile?.id
        draftName = profile?.name ?? ""
        draftHost = profile?.host ?? ""
        draftPort = String(profile?.port ?? 22)
        draftUsername = profile?.username ?? "root"
        draftAuthType = profile?.authType ?? .password
        draftPassword = profile?.password ?? ""
        draftKeyId = (profile?.keyId.isEmpty == false ? profile?.keyId : nil) ?? (repo.keys.first?.id ?? "")
        draftExistingPassphrase = profile?.keyPassphrase ?? ""
        draftPassphraseInput = ""
        draftLastConnected = profile?.lastConnectedAt ?? 0
        page = .serverEditor
    }

    private func openKeyEditor(returnToServer: Bool) {
        keyReturnToServerEditor = returnToServer
        keyNameInput = ""
        keyPemInput = ""
        generatedPublicKey = ""
        page = .keyEditor
    }

    private func connect(_ profile: SshServerProfile) {
        guard !isConnecting else { return }
        isConnecting = true
        Task {
            do {
                let auth: SSHAuthenticationMethod
                if profile.authType == .password {
                    auth = .passwordBased(username: profile.username, password: profile.password)
                } else {
                    let pem = repo.resolvePrivateKey(for: profile)
                    if pem.contains("OPENSSH PRIVATE KEY") && pem.contains("ed25519") {
                        let key = try Curve25519.Signing.PrivateKey(sshEd25519: pem)
                        auth = .ed25519(username: profile.username, privateKey: key)
                    } else {
                        let key = try Insecure.RSA.PrivateKey(sshRsa: pem)
                        auth = .rsa(username: profile.username, privateKey: key)
                    }
                }
                let sshClient = try await SSHClient.connect(
                    host: profile.host,
                    port: profile.port,
                    authenticationMethod: auth,
                    hostKeyValidator: .acceptAnything(),
                    reconnect: .never
                )
                client = sshClient
                activeProfile = profile
                repo.touchLastConnected(id: profile.id)

                let homeDir = try await sshClient.withSFTP { sftp in
                    try await sftp.getRealPath(atPath: ".")
                }
                currentPath = homeDir.isEmpty ? "/" : homeDir
                terminalBlocks = []
                terminalLog = "Connected to \(profile.username)@\(profile.host):\(profile.port) · \(currentPath)"
                page = .terminal
                refreshStatus()
            } catch {
                toastMsg = "连接失败：\(error.localizedDescription)"
            }
            isConnecting = false
        }
    }

    private func disconnect() {
        let c = client
        client = nil
        activeProfile = nil
        remoteFiles = []
        if page == .terminal || page == .sftpFiles {
            page = .serverList
        }
        Task { try? await c?.close() }
    }

    private func refreshStatus() {
        guard let c = client else { return }
        Task {
            let cmd = "echo \"OS=$(uname -sr 2>/dev/null || echo Linux)\"; echo \"UP=$(uptime | sed 's/.*up \\([^,]*\\),.*/\\1/')\"; echo \"MEM=$(free -m 2>/dev/null | awk 'NR==2{printf \"%s/%s MB (%.0f%%)\", $3,$2,$3*100/$2}')\"; echo \"DISK=$(df -h / 2>/dev/null | awk 'NR==2{printf \"%s/%s (%s)\", $3,$2,$5}')\""
            if let buf = try? await c.executeCommand(cmd) {
                let out = String(buffer: buf)
                var info = SshServerStatusInfo()
                for line in out.components(separatedBy: .newlines) {
                    if line.hasPrefix("OS=") { info.osInfo = String(line.dropFirst(3)) }
                    else if line.hasPrefix("UP=") { info.uptime = String(line.dropFirst(3)) }
                    else if line.hasPrefix("MEM=") { info.memoryInfo = String(line.dropFirst(4)) }
                    else if line.hasPrefix("DISK=") { info.diskInfo = String(line.dropFirst(5)) }
                }
                statusInfo = info
            }
        }
    }

    private func executeTerminalCommand(overrideCommand: String? = nil) {
        let raw = overrideCommand ?? commandInput
        let cmd = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cmd.isEmpty, let c = client, !isRunningCmd else { return }
        if overrideCommand == nil { commandInput = "" }
        isRunningCmd = true
        let block = SshTerminalBlock(command: cmd, output: "执行中...", isError: false)
        terminalBlocks.append(block)
        let blockId = block.id
        Task {
            do {
                let escapedDir = currentPath.replacingOccurrences(of: "'", with: "'\\''")
                let buf = try await c.executeCommand("cd '\(escapedDir)' 2>/dev/null; \(cmd)", mergeStreams: true)
                let out = String(buffer: buf).trimmingCharacters(in: CharacterSet.newlines)
                if let idx = terminalBlocks.firstIndex(where: { $0.id == blockId }) {
                    terminalBlocks[idx].output = out
                    terminalBlocks[idx].isError = false
                }
            } catch {
                if let idx = terminalBlocks.firstIndex(where: { $0.id == blockId }) {
                    terminalBlocks[idx].output = "[Error] \(error.localizedDescription)"
                    terminalBlocks[idx].isError = true
                }
            }
            isRunningCmd = false
        }
    }

    private func refreshFiles(path: String) {
        guard let c = client else { return }
        isLoadingFiles = true
        Task {
            do {
                let resolved = try await c.withSFTP { sftp in
                    try await sftp.getRealPath(atPath: path)
                }
                let targetDir = resolved.isEmpty ? path : resolved
                let escapedDir = targetDir.replacingOccurrences(of: "'", with: "'\\''")
                let buf = try await c.executeCommand("LC_ALL=C ls -la '\(escapedDir)'", mergeStreams: true)
                let items = Self.parseLsOutput(String(buffer: buf), baseDir: targetDir).sorted { lhs, rhs in
                    if lhs.isDir != rhs.isDir { return lhs.isDir && !rhs.isDir }
                    return lhs.name.lowercased() < rhs.name.lowercased()
                }
                currentPath = targetDir
                remoteFiles = items
            } catch {
                toastMsg = "读取目录失败：\(error.localizedDescription)"
            }
            isLoadingFiles = false
        }
    }

    private static func parseLsOutput(_ output: String, baseDir: String) -> [SshRemoteFileItem] {
        let base = baseDir.hasSuffix("/") && baseDir != "/" ? String(baseDir.dropLast()) : baseDir
        var results: [SshRemoteFileItem] = []
        let lines = output.components(separatedBy: .newlines)
        for rawLine in lines {
            let line = rawLine.trimmingCharacters(in: CharacterSet(charactersIn: "\r"))
            if line.isEmpty || line.hasPrefix("total ") { continue }
            guard let first = line.first, "-dlcbps".contains(first) else { continue }

            // Parse first 8 whitespace-separated tokens, keeping remainder intact for filenames with spaces
            var idx = line.startIndex
            var tokens: [Substring] = []
            while tokens.count < 8 && idx < line.endIndex {
                while idx < line.endIndex && line[idx].isWhitespace {
                    idx = line.index(after: idx)
                }
                guard idx < line.endIndex else { break }
                let start = idx
                while idx < line.endIndex && !line[idx].isWhitespace {
                    idx = line.index(after: idx)
                }
                tokens.append(line[start..<idx])
            }
            guard tokens.count == 8 else { continue }
            while idx < line.endIndex && line[idx].isWhitespace {
                idx = line.index(after: idx)
            }
            guard idx < line.endIndex else { continue }

            let perms = String(tokens[0])
            guard perms.count >= 10 else { continue }
            var name = String(line[idx...])
            if perms.hasPrefix("l"), let arrowRange = name.range(of: " -> ") {
                name = String(name[..<arrowRange.lowerBound])
            }
            if name == "." || name == ".." || name.isEmpty { continue }

            let isDir = perms.hasPrefix("d")
            let size = UInt64(tokens[4]) ?? 0
            let full = base == "/" ? "/\(name)" : "\(base)/\(name)"

            results.append(SshRemoteFileItem(
                name: name,
                fullPath: full,
                isDir: isDir,
                size: size,
                permissionsText: String(perms.prefix(10)),
                modifiedAt: nil
            ))
        }
        return results
    }

    private func uploadLocalFile(_ url: URL) {
        guard let c = client else { return }
        let acc = url.startAccessingSecurityScopedResource()
        defer { if acc { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return }
        let fileName = url.lastPathComponent
        let dest = currentPath == "/" ? "/\(fileName)" : "\(currentPath)/\(fileName)"
        isLoadingFiles = true
        Task {
            do {
                try await c.withSFTP { sftp in
                    try await sftp.withFile(filePath: dest, flags: [.write, .create, .truncate]) { file in
                        try await file.write(ByteBuffer(data: data))
                    }
                }
                toastMsg = "上传成功：\(fileName)"
                refreshFiles(path: currentPath)
            } catch {
                toastMsg = "上传失败：\(error.localizedDescription)"
                isLoadingFiles = false
            }
        }
    }

    private func downloadRemoteFile(_ item: SshRemoteFileItem) {
        guard let c = client else { return }
        isLoadingFiles = true
        Task {
            do {
                let buf = try await c.withSFTP { sftp in
                    try await sftp.withFile(filePath: item.fullPath, flags: .read) { file in
                        try await file.readAll()
                    }
                }
                let outURL = FileManager.default.temporaryDirectory.appendingPathComponent(item.name)
                try Data(buffer: buf).write(to: outURL, options: .atomic)
                shareFileURL = outURL
            } catch {
                toastMsg = "下载失败：\(error.localizedDescription)"
            }
            isLoadingFiles = false
        }
    }

    private func openTextPreview(_ item: SshRemoteFileItem) {
        guard let c = client else { return }
        Task {
            do {
                let buf = try await c.withSFTP { sftp in
                    try await sftp.withFile(filePath: item.fullPath, flags: .read) { file in
                        try await file.readAll()
                    }
                }
                previewContent = String(buffer: buf)
                previewFile = item
            } catch {
                toastMsg = "读取失败：\(error.localizedDescription)"
            }
        }
    }

    private func saveTextPreview(_ item: SshRemoteFileItem) {
        guard let c = client else { return }
        let data = Data(previewContent.utf8)
        Task {
            do {
                try await c.withSFTP { sftp in
                    try await sftp.withFile(filePath: item.fullPath, flags: [.write, .create, .truncate]) { file in
                        try await file.write(ByteBuffer(data: data))
                    }
                }
                previewFile = nil
                toastMsg = "已保存至服务器"
                refreshFiles(path: currentPath)
            } catch {
                toastMsg = "保存失败：\(error.localizedDescription)"
            }
        }
    }

    private func createRemoteDir() {
        let name = mkdirInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, let c = client else { return }
        let target = currentPath == "/" ? "/\(name)" : "\(currentPath)/\(name)"
        Task {
            do {
                try await c.withSFTP { sftp in
                    try await sftp.createDirectory(atPath: target)
                }
                refreshFiles(path: currentPath)
            } catch {
                toastMsg = "创建失败：\(error.localizedDescription)"
            }
        }
    }

    private func performRename() {
        guard let target = renameTarget, let c = client else { return }
        let newName = renameInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !newName.isEmpty else { return }
        let parent = (target.fullPath as NSString).deletingLastPathComponent
        let newPath = parent == "/" ? "/\(newName)" : "\(parent)/\(newName)"
        Task {
            do {
                try await c.withSFTP { sftp in
                    try await sftp.rename(at: target.fullPath, to: newPath)
                }
                refreshFiles(path: currentPath)
            } catch {
                toastMsg = "重命名失败：\(error.localizedDescription)"
            }
        }
    }

    private func deleteRemoteItem(_ item: SshRemoteFileItem) {
        guard let c = client else { return }
        Task {
            do {
                try await c.withSFTP { sftp in
                    if item.isDir {
                        try await sftp.rmdir(at: item.fullPath)
                    } else {
                        try await sftp.remove(at: item.fullPath)
                    }
                }
                refreshFiles(path: currentPath)
            } catch {
                toastMsg = "删除失败：\(error.localizedDescription)"
            }
        }
    }

    private func formatDate(_ ts: TimeInterval) -> String {
        let f = DateFormatter()
        f.timeZone = TimeZone.current
        f.dateFormat = "MM-dd HH:mm"
        return f.string(from: Date(timeIntervalSince1970: ts))
    }


    static func derivePublicKey(from privateKey: String) -> String? {
        let trimmed = privateKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        func sshStr(_ d: Data) -> Data {
            var out = Data()
            var len = UInt32(d.count).bigEndian
            out.append(Data(bytes: &len, count: 4))
            out.append(d)
            return out
        }
        func sshStr(_ s: String) -> Data { sshStr(Data(s.utf8)) }
        func readSshBytes(_ data: Data, _ offset: inout Int) -> Data? {
            guard offset + 4 <= data.count else { return nil }
            let len = data.subdata(in: offset..<(offset + 4)).withUnsafeBytes {
                UInt32(bigEndian: $0.load(as: UInt32.self))
            }
            offset += 4
            let count = Int(len)
            guard count >= 0, offset + count <= data.count else { return nil }
            let slice = data.subdata(in: offset..<(offset + count))
            offset += count
            return slice
        }

        // 1) OpenSSH Private Key (openssh-key-v1)
        if trimmed.contains("BEGIN OPENSSH PRIVATE KEY") {
            let b64 = trimmed
                .components(separatedBy: .newlines)
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("-----") }
                .joined()
            if let raw = Data(base64Encoded: b64, options: .ignoreUnknownCharacters) {
                let magic = Data("openssh-key-v1\0".utf8)
                if raw.starts(with: magic) {
                    var offset = magic.count
                    _ = readSshBytes(raw, &offset) // ciphername
                    _ = readSshBytes(raw, &offset) // kdfname
                    _ = readSshBytes(raw, &offset) // kdfoptions
                    if offset + 4 <= raw.count {
                        let numKeys = raw.subdata(in: offset..<(offset + 4)).withUnsafeBytes {
                            UInt32(bigEndian: $0.load(as: UInt32.self))
                        }
                        offset += 4
                        if numKeys >= 1, let pubBlob = readSshBytes(raw, &offset) {
                            var pubOff = 0
                            if let typeData = readSshBytes(pubBlob, &pubOff),
                               let keyType = String(data: typeData, encoding: .utf8),
                               !keyType.isEmpty {
                                return "\(keyType) \(pubBlob.base64EncodedString()) jools@ios"
                            }
                        }
                    }
                }
            }
        }

        // 2) ECDSA PEM via CryptoKit
        if let p256 = try? P256.Signing.PrivateKey(pemRepresentation: trimmed) {
            var blob = Data()
            blob.append(sshStr("ecdsa-sha2-nistp256"))
            blob.append(sshStr("nistp256"))
            blob.append(sshStr(p256.publicKey.x963Representation))
            return "ecdsa-sha2-nistp256 \(blob.base64EncodedString()) jools@ios"
        }
        if let p384 = try? P384.Signing.PrivateKey(pemRepresentation: trimmed) {
            var blob = Data()
            blob.append(sshStr("ecdsa-sha2-nistp384"))
            blob.append(sshStr("nistp384"))
            blob.append(sshStr(p384.publicKey.x963Representation))
            return "ecdsa-sha2-nistp384 \(blob.base64EncodedString()) jools@ios"
        }
        if let p521 = try? P521.Signing.PrivateKey(pemRepresentation: trimmed) {
            var blob = Data()
            blob.append(sshStr("ecdsa-sha2-nistp521"))
            blob.append(sshStr("nistp521"))
            blob.append(sshStr(p521.publicKey.x963Representation))
            return "ecdsa-sha2-nistp521 \(blob.base64EncodedString()) jools@ios"
        }

        // 3) PKCS#1 RSA or PKCS#8 RSA (ASN.1 DER)
        func readDerTLV(_ data: Data, _ offset: inout Int) -> (tag: UInt8, value: Data)? {
            guard offset < data.count else { return nil }
            let tag = data[offset]
            offset += 1
            guard offset < data.count else { return nil }
            let firstLen = data[offset]
            offset += 1
            let length: Int
            if firstLen & 0x80 == 0 {
                length = Int(firstLen)
            } else {
                let numBytes = Int(firstLen & 0x7F)
                guard numBytes > 0, numBytes <= 4, offset + numBytes <= data.count else { return nil }
                var acc = 0
                for _ in 0..<numBytes {
                    acc = (acc << 8) | Int(data[offset])
                    offset += 1
                }
                length = acc
            }
            guard offset + length <= data.count else { return nil }
            let val = data.subdata(in: offset..<(offset + length))
            offset += length
            return (tag, val)
        }

        func extractRsaPubFromPkcs1(_ pkcs1: Data) -> String? {
            var off = 0
            guard let seq = readDerTLV(pkcs1, &off), seq.tag == 0x30 else { return nil }
            var innerOff = 0
            guard let ver = readDerTLV(seq.value, &innerOff), ver.tag == 0x02,
                  let n = readDerTLV(seq.value, &innerOff), n.tag == 0x02,
                  let e = readDerTLV(seq.value, &innerOff), e.tag == 0x02 else { return nil }
            var pubBlob = Data()
            pubBlob.append(sshStr("ssh-rsa"))
            pubBlob.append(sshStr(e.value))
            pubBlob.append(sshStr(n.value))
            return "ssh-rsa \(pubBlob.base64EncodedString()) jools@ios"
        }

        let pemB64 = trimmed
            .components(separatedBy: .newlines)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("-----") }
            .joined()
        if let der = Data(base64Encoded: pemB64, options: .ignoreUnknownCharacters) {
            if trimmed.contains("BEGIN RSA PRIVATE KEY") {
                return extractRsaPubFromPkcs1(der)
            }
            if trimmed.contains("BEGIN PRIVATE KEY") {
                var off = 0
                if let top = readDerTLV(der, &off), top.tag == 0x30 {
                    var innerOff = 0
                    _ = readDerTLV(top.value, &innerOff) // version
                    _ = readDerTLV(top.value, &innerOff) // algorithmIdentifier
                    if let oct = readDerTLV(top.value, &innerOff), oct.tag == 0x04 {
                        if let rsa = extractRsaPubFromPkcs1(oct.value) {
                            return rsa
                        }
                    }
                }
            }
        }
        return nil
    }

    private func generateEd25519KeyPair() -> (privatePem: String, publicLine: String) {
        let priv = Curve25519.Signing.PrivateKey()
        let pubRaw = priv.publicKey.rawRepresentation
        let privRaw = priv.rawRepresentation

        func sshStr(_ d: Data) -> Data {
            var out = Data()
            var len = UInt32(d.count).bigEndian
            out.append(Data(bytes: &len, count: 4))
            out.append(d)
            return out
        }
        func sshStr(_ s: String) -> Data { sshStr(Data(s.utf8)) }

        var pubBlob = Data()
        pubBlob.append(sshStr("ssh-ed25519"))
        pubBlob.append(sshStr(pubRaw))
        let pubLine = "ssh-ed25519 " + pubBlob.base64EncodedString() + " jools@ios"

        var privSection = Data()
        var checkInt = UInt32.random(in: 1...UInt32.max).bigEndian
        privSection.append(Data(bytes: &checkInt, count: 4))
        privSection.append(Data(bytes: &checkInt, count: 4))
        privSection.append(sshStr("ssh-ed25519"))
        privSection.append(sshStr(pubRaw))
        var combined = Data()
        combined.append(privRaw)
        combined.append(pubRaw)
        privSection.append(sshStr(combined))
        privSection.append(sshStr("jools@ios"))
        var pad: UInt8 = 1
        while privSection.count % 8 != 0 {
            privSection.append(pad)
            pad &+= 1
        }

        var full = Data("openssh-key-v1\0".utf8)
        full.append(sshStr("none"))
        full.append(sshStr("none"))
        full.append(sshStr(Data()))
        var one = UInt32(1).bigEndian
        full.append(Data(bytes: &one, count: 4))
        full.append(sshStr(pubBlob))
        full.append(sshStr(privSection))

        let b64 = full.base64EncodedString()
        var lines: [String] = ["-----BEGIN OPENSSH PRIVATE KEY-----"]
        var idx = b64.startIndex
        while idx < b64.endIndex {
            let next = b64.index(idx, offsetBy: 70, limitedBy: b64.endIndex) ?? b64.endIndex
            lines.append(String(b64[idx..<next]))
            idx = next
        }
        lines.append("-----END OPENSSH PRIVATE KEY-----")
        return (lines.joined(separator: "\n"), pubLine)
    }
}


private struct SshShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private struct SshKeyDetailSheet: View {
    let keyItem: SshKeyItem
    let onSave: (SshKeyItem) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var editPublicKey: String
    @State private var editPrivateKey: String
    @State private var showPrivateKey: Bool = false
    @State private var showSaveConfirm: Bool = false
    @State private var toastMsg: String?

    init(keyItem: SshKeyItem, onSave: @escaping (SshKeyItem) -> Void) {
        self.keyItem = keyItem
        self.onSave = onSave
        let initialPub = keyItem.publicKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? (SshView.derivePublicKey(from: keyItem.privateKey) ?? "")
            : keyItem.publicKey
        _editPublicKey = State(initialValue: initialPub)
        _editPrivateKey = State(initialValue: keyItem.privateKey)
    }

    private var hasChanges: Bool {
        editPublicKey.trimmingCharacters(in: .whitespacesAndNewlines) != keyItem.publicKey.trimmingCharacters(in: .whitespacesAndNewlines) ||
        editPrivateKey.trimmingCharacters(in: .whitespacesAndNewlines) != keyItem.privateKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var maskedPrivateKeyPreview: String {
        let lines = max(4, min(editPrivateKey.components(separatedBy: .newlines).count, 8))
        return (0..<lines).map { _ in String(repeating: "•", count: 28) }.joined(separator: "\n")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // 公钥区域
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("公钥")
                                .font(.system(size: 14, weight: .semibold))
                            Spacer()
                            Button {
                                UIPasteboard.general.string = editPublicKey
                                toastMsg = "已复制公钥内容"
                            } label: {
                                Image(systemName: "doc.on.doc")
                                    .font(.system(size: 15))
                            }
                            .buttonStyle(.plain)
                        }
                        TextEditor(text: $editPublicKey)
                            .font(.system(size: 12, design: .monospaced))
                            .frame(height: 110)
                            .padding(8)
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                            )
                    }

                    // 私钥区域（小眼睛在复制按钮左侧，默认不显示明文）
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 14) {
                            Text("私钥")
                                .font(.system(size: 14, weight: .semibold))
                            Spacer()
                            Button {
                                showPrivateKey.toggle()
                            } label: {
                                Image(systemName: showPrivateKey ? "eye.slash" : "eye")
                                    .font(.system(size: 15.5))
                            }
                            .buttonStyle(.plain)

                            Button {
                                UIPasteboard.general.string = editPrivateKey
                                toastMsg = "已复制私钥内容"
                            } label: {
                                Image(systemName: "doc.on.doc")
                                    .font(.system(size: 15))
                            }
                            .buttonStyle(.plain)
                        }

                        if showPrivateKey {
                            TextEditor(text: $editPrivateKey)
                                .font(.system(size: 12, design: .monospaced))
                                .frame(height: 190)
                                .padding(8)
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                                )
                        } else {
                            ScrollView {
                                Text(maskedPrivateKeyPreview)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundColor(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(12)
                            }
                            .frame(height: 190)
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                            )
                        }
                    }

                    if let msg = toastMsg {
                        Text(msg)
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundColor(.accentColor)
                    }
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle(keyItem.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("关闭") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("保存") {
                        showSaveConfirm = true
                    }
                    .fontWeight(.semibold)
                    .disabled(!hasChanges || editPrivateKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .foregroundColor(
                        (hasChanges && !editPrivateKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        ? .accentColor
                        : .gray.opacity(0.5)
                    )
                }
            }
            .alert("确认保存", isPresented: $showSaveConfirm) {
                Button("取消", role: .cancel) {}
                Button("确定") {
                    var updated = keyItem
                    updated.publicKey = editPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    updated.privateKey = editPrivateKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    onSave(updated)
                    dismiss()
                }
            } message: {
                Text("确定保存对密钥“\(keyItem.name)”的修改吗？")
            }
        }
    }
}
