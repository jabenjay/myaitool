import SwiftUI
import AVFoundation
import PhotosUI
import CoreImage.CIFilterBuiltins
import UniformTypeIdentifiers
import Compression
import CryptoKit

// MARK: - 1. Base64 Tool
struct Base64ToolView: View {
    @State private var inputText: String = ""
    @State private var outputText: String = ""
    @State private var statusText: String = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                TextEditor(text: $inputText)
                    .frame(height: 150)
                    .padding(8)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        Group {
                            if inputText.isEmpty {
                                Text("输入文本或 Base64 字符串")
                                    .foregroundColor(.secondary)
                                    .padding(14)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                                    .allowsHitTesting(false)
                            }
                        }
                    )

                HStack(spacing: 10) {
                    Button("编码") {
                        outputText = Data(inputText.utf8).base64EncodedString()
                        statusText = "已编码 (${outputText.count} 字符)".replacingOccurrences(of: "$(outputText.count)", with: "\(outputText.count)")
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)

                    Button("解码") {
                        if let data = Data(base64Encoded: inputText.trimmingCharacters(in: .whitespacesAndNewlines)),
                           let str = String(data: data, encoding: .utf8) {
                            outputText = str
                            statusText = "解码成功"
                        } else {
                            statusText = "无效的 Base64 内容"
                        }
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                }

                if !outputText.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("输出结果")
                                .font(.system(size: 13, weight: .semibold))
                            Spacer()
                            Button("复制") {
                                UIPasteboard.general.string = outputText
                                statusText = "已复制到剪贴板"
                            }
                            .font(.system(size: 13))
                        }
                        Text(outputText)
                            .font(.system(size: 13, design: .monospaced))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }

                if !statusText.isEmpty {
                    Text(statusText).font(.system(size: 12)).foregroundColor(.secondary)
                }
            }
            .padding(16)
        }
        .navigationTitle("Base64 编码")
    }
}

// MARK: - 2. QR Scanner
struct QrScannerView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var scannedCode: String?
    @State private var openInBrowserUrl: String?

    var body: some View {
        ZStack {
            CameraQRPreview { code in
                if scannedCode == nil {
                    scannedCode = code
                    handleScanned(code)
                }
            }
            .ignoresSafeArea()

            VStack {
                Spacer()
                if let code = scannedCode {
                    VStack(spacing: 10) {
                        Text(code)
                            .font(.system(size: 13))
                            .lineLimit(3)
                        HStack {
                            Button("复制") { UIPasteboard.general.string = code }
                                .buttonStyle(.bordered)
                            Button("重新扫描") { scannedCode = nil }
                                .buttonStyle(.borderedProminent)
                        }
                    }
                    .padding()
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .padding()
                }
            }
        }
        .navigationTitle("扫描二维码")
    }

    private func handleScanned(_ code: String) {
        let lower = code.lowercased()
        if lower.contains("alipay.com") || lower.hasPrefix("alipays://") {
            let encoded = code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? code
            if let u = URL(string: "alipays://platformapi/startapp?saId=10000007&qrcode=\(encoded)"),
               UIApplication.shared.canOpenURL(u) {
                UIApplication.shared.open(u)
                return
            }
        }
        if lower.hasPrefix("http://") || lower.hasPrefix("https://"), let u = URL(string: code) {
            UIApplication.shared.open(u)
        }
    }
}

private struct CameraQRPreview: UIViewControllerRepresentable {
    let onDetect: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerVC {
        let vc = ScannerVC()
        vc.onDetect = onDetect
        return vc
    }

    func updateUIViewController(_ uiViewController: ScannerVC, context: Context) {}

    final class ScannerVC: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
        var onDetect: ((String) -> Void)?
        private let session = AVCaptureSession()

        override func viewDidLoad() {
            super.viewDidLoad()
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device) else { return }
            if session.canAddInput(input) { session.addInput(input) }
            let output = AVCaptureMetadataOutput()
            if session.canAddOutput(output) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main)
                output.metadataObjectTypes = [.qr]
            }
            let preview = AVCaptureVideoPreviewLayer(session: session)
            preview.frame = view.bounds
            preview.videoGravity = .resizeAspectFill
            view.layer.addSublayer(preview)
            DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if session.isRunning { session.stopRunning() }
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            if let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject, let val = obj.stringValue {
                onDetect?(val)
            }
        }
    }
}

// MARK: - 3. QR Generator Tool
struct QrToolView: View {
    @State private var text: String = ""
    @State private var savedNotice: String = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                TextField("输入文本或网址生成二维码", text: $text)
                    .textFieldStyle(.roundedBorder)

                if !text.isEmpty, let img = makeQR(text) {
                    Image(uiImage: img)
                        .interpolation(.none)
                        .resizable()
                        .frame(width: 220, height: 220)
                        .padding(16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(radius: 4)

                    Button("保存到相册") {
                        UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                        savedNotice = "已保存到相册"
                    }
                    .buttonStyle(.borderedProminent)
                }
                if !savedNotice.isEmpty {
                    Text(savedNotice).font(.system(size: 12)).foregroundColor(.secondary)
                }
            }
            .padding(16)
        }
        .navigationTitle("二维码工具")
    }

    private func makeQR(_ s: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(s.utf8)
        filter.correctionLevel = "M"
        guard let out = filter.outputImage else { return nil }
        let scaled = out.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

// MARK: - 4. Format Factory (Image Format, Aspect-Locked Pixel Resize, Estimated Output Size)
struct FormatFactoryView: View {
    @State private var selectedItem: PhotosPickerItem?
    @State private var originalImage: UIImage?
    @State private var targetFormat: String = "JPEG"
    @State private var quality: Double = 85
    @State private var lockAspect: Bool = true
    @State private var widthText: String = ""
    @State private var heightText: String = ""
    @State private var aspectRatio: Double = 1.0
    @State private var statusText: String = ""

    private let formats = ["JPEG", "PNG"]

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                PhotosPicker(selection: $selectedItem, matching: .images) {
                    Label(originalImage == nil ? "选择图片" : "重新选择图片", systemImage: "photo")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .onChange(of: selectedItem) { item in
                    Task {
                        if let data = try? await item?.loadTransferable(type: Data.self),
                           let img = UIImage(data: data) {
                            originalImage = img
                            let w = Int(img.size.width * img.scale)
                            let h = Int(img.size.height * img.scale)
                            aspectRatio = h > 0 ? Double(w) / Double(h) : 1.0
                            widthText = "\(w)"
                            heightText = "\(h)"
                        }
                    }
                }

                if let img = originalImage {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 180)
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                    HStack {
                        Text("目标格式")
                        Spacer()
                        Picker("目标格式", selection: $targetFormat) {
                            ForEach(formats, id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.menu)
                    }

                    HStack(spacing: 8) {
                        TextField("宽(px)", text: $widthText)
                            .keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: widthText) { val in
                                if lockAspect, let w = Double(val), aspectRatio > 0 {
                                    let h = Int((w / aspectRatio).rounded())
                                    if "\(h)" != heightText { heightText = "\(h)" }
                                }
                            }
                        Button {
                            lockAspect.toggle()
                        } label: {
                            Image(systemName: lockAspect ? "lock.fill" : "lock.open")
                        }
                        TextField("高(px)", text: $heightText)
                            .keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)
                            .onChange(of: heightText) { val in
                                if lockAspect, let h = Double(val), aspectRatio > 0 {
                                    let w = Int((h * aspectRatio).rounded())
                                    if "\(w)" != widthText { widthText = "\(w)" }
                                }
                            }
                    }

                    if targetFormat == "JPEG" {
                        VStack(alignment: .leading) {
                            Text("输出质量: \(Int(quality))%").font(.system(size: 13))
                            Slider(value: $quality, in: 10...100, step: 5)
                        }
                    }

                    let estBytes = estimateOutputBytes()
                    Text("预计输出大小: \(ByteCountFormatter.string(fromByteCount: Int64(estBytes), countStyle: .file))")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)

                    Button("转换并保存到相册") {
                        exportConverted(img)
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                }

                if !statusText.isEmpty {
                    Text(statusText).font(.system(size: 12)).foregroundColor(.secondary)
                }
            }
            .padding(16)
        }
        .navigationTitle("格式工厂")
    }

    private func estimateOutputBytes() -> Int {
        guard let img = originalImage else { return 0 }
        let w = Int(widthText) ?? Int(img.size.width)
        let h = Int(heightText) ?? Int(img.size.height)
        let pixels = max(1, w * h)
        return targetFormat == "PNG" ? Int(Double(pixels) * 1.4) : Int(Double(pixels) * 0.28 * (quality / 85.0))
    }

    private func exportConverted(_ img: UIImage) {
        let w = CGFloat(Int(widthText) ?? Int(img.size.width))
        let h = CGFloat(Int(heightText) ?? Int(img.size.height))
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h))
        let resized = renderer.image { _ in img.draw(in: CGRect(origin: .zero, size: CGSize(width: w, height: h))) }
        let data = targetFormat == "PNG" ? resized.pngData() : resized.jpegData(compressionQuality: quality / 100.0)
        if let data, let outImg = UIImage(data: data) {
            UIImageWriteToSavedPhotosAlbum(outImg, nil, nil, nil)
            statusText = "已保存 (\(ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file)))"
        }
    }
}

// MARK: - 5. File Compressor
struct FileCompressorView: View {
    @State private var selectedItem: PhotosPickerItem?
    @State private var originalData: Data?
    @State private var quality: Double = 60
    @State private var resultText: String = ""

    var body: some View {
        VStack(spacing: 16) {
            PhotosPicker(selection: $selectedItem, matching: .images) {
                Label("选择图片进行压缩", systemImage: "archivebox")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .onChange(of: selectedItem) { item in
                Task {
                    originalData = try? await item?.loadTransferable(type: Data.self)
                }
            }

            if let data = originalData {
                Text("原始大小: \(ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file))")
                    .font(.system(size: 14))
                VStack(alignment: .leading) {
                    Text("压缩质量: \(Int(quality))%").font(.system(size: 13))
                    Slider(value: $quality, in: 10...95, step: 5)
                }
                Button("压缩并保存到相册") {
                    if let img = UIImage(data: data),
                       let compressed = img.jpegData(compressionQuality: quality / 100.0),
                       let outImg = UIImage(data: compressed) {
                        UIImageWriteToSavedPhotosAlbum(outImg, nil, nil, nil)
                        resultText = "压缩后大小: \(ByteCountFormatter.string(fromByteCount: Int64(compressed.count), countStyle: .file))"
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
            if !resultText.isEmpty {
                Text(resultText).font(.system(size: 13)).foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(16)
        .navigationTitle("文件压缩")
    }
}


// MARK: - 6. PushGit Tool
struct PushGitView: View {
    struct SavedToken: Identifiable, Codable, Equatable {
        let id: String
        let username: String
        let token: String
    }

    struct PushFile: Identifiable {
        let id = UUID()
        let path: String
        let data: Data
    }

    struct DiffResult {
        let headSha: String
        let baseTreeSha: String
        let added: [PushFile]
        let modified: [PushFile]
        let deleted: [String]
        let unchangedCount: Int
        var uploadFiles: [PushFile] { added + modified }
    }

    @State private var pat: String = UserDefaults.standard.string(forKey: "jools_pushgit_pat") ?? ""
    @State private var showPat: Bool = false
    @State private var connectedUser: String?
    @State private var savedTokens: [SavedToken] = []
    @State private var showTokenManager: Bool = false
    @State private var newTokenInput: String = ""

    @State private var repoText: String = UserDefaults.standard.string(forKey: "jools_pushgit_repo") ?? "jabenjay/myaitool"
    @State private var branch: String = UserDefaults.standard.string(forKey: "jools_pushgit_branch") ?? "main"
    @State private var commitMessage: String = UserDefaults.standard.string(forKey: "jools_pushgit_msg") ?? "Sync project files via PushGit"
    @State private var targetSubpath: String = ""
    @State private var repos: [String] = []
    @State private var branches: [String] = ["main"]

    @State private var selectedFiles: [PushFile] = []
    @State private var diffResult: DiffResult?
    @State private var syncDeletions: Bool = false
    @State private var showZipImporter = false
    @State private var showFolderImporter = false
    @State private var showFilesImporter = false

    @State private var isInspecting = false
    @State private var isPushing = false
    @State private var statusText = ""

    var body: some View {
        Form {
            Section("已保存账号与目标仓库") {
                if !savedTokens.isEmpty {
                    Picker("切换账号", selection: $pat) {
                        ForEach(savedTokens) { item in
                            Text("@\(item.username)").tag(item.token)
                        }
                    }
                    .onChange(of: pat) { _ in
                        diffResult = nil
                        Task { await fetchMeta() }
                    }
                }
                if !repos.isEmpty {
                    Picker("目标仓库", selection: $repoText) {
                        ForEach(repos, id: \.self) { Text($0).tag($0) }
                    }
                    .onChange(of: repoText) { _ in
                        diffResult = nil
                        Task { await fetchBranchesOnly() }
                    }
                }
                TextField("owner/repo（如 jabenjay/myaitool）", text: $repoText)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                HStack {
                    if showPat {
                        TextField("Personal Access Token (ghp_...)", text: $pat)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    } else {
                        SecureField("Personal Access Token (ghp_...)", text: $pat)
                    }
                    Button { showPat.toggle() } label: {
                        Image(systemName: showPat ? "eye.slash" : "eye")
                    }
                }
                if let user = connectedUser {
                    Label("Connected as @\(user)", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 13))
                        .foregroundColor(.green)
                }
                Button("刷新仓库与分支") {
                    Task { await fetchMeta() }
                }
                .disabled(pat.isEmpty)
            }

            Section("分支与提交信息") {
                TextField("Branch", text: $branch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if !branches.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(branches, id: \.self) { b in
                                Button(b) { branch = b; diffResult = nil }
                                    .buttonStyle(.bordered)
                                    .tint(branch == b ? .accentColor : .secondary)
                            }
                        }
                    }
                }
                TextField("Commit Message", text: $commitMessage)
            }

            Section("选择上传内容（调用系统文件）") {
                TextField("单文件目标路径前缀（可选）", text: $targetSubpath)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                HStack(spacing: 10) {
                    Button("ZIP") { showZipImporter = true }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                    Button("文件夹") { showFolderImporter = true }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                    Button("文件") { showFilesImporter = true }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                }
                if let diff = diffResult {
                    Text("比对结果：新增 \(diff.added.count) · 修改 \(diff.modified.count) · 远程独有 \(diff.deleted.count) · 未变 \(diff.unchangedCount)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.accentColor)
                    if !diff.deleted.isEmpty {
                        Toggle("同步删除远程独有文件 (\(diff.deleted.count))", isOn: $syncDeletions)
                            .font(.system(size: 12))
                    }
                    ForEach(diff.added) { f in Text("[新增] \(f.path)").font(.system(size: 12)).foregroundColor(.green) }
                    ForEach(diff.modified) { f in Text("[修改] \(f.path)").font(.system(size: 12)).foregroundColor(.orange) }
                    ForEach(diff.deleted, id: \.self) { p in Text("[删除] \(p)").font(.system(size: 12)).foregroundColor(.red) }
                } else if !selectedFiles.isEmpty {
                    Text("已就绪 \(selectedFiles.count) 个文件，请先比对差异")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.accentColor)
                    ForEach(selectedFiles) { f in
                        Text(f.path)
                            .font(.system(size: 12))
                            .lineLimit(1)
                    }
                    .onDelete { idxs in selectedFiles.remove(atOffsets: idxs) }
                }
            }

            Section {
                let pushCount = (diffResult?.uploadFiles.count ?? 0) + (syncDeletions ? (diffResult?.deleted.count ?? 0) : 0)
                Button {
                    Task {
                        if diffResult == nil {
                            await inspectDiff()
                        } else {
                            await pushDiffChanges()
                        }
                    }
                } label: {
                    HStack {
                        Spacer()
                        if isInspecting {
                            Text("正在比对...").fontWeight(.semibold)
                        } else if isPushing {
                            Text("正在推送...").fontWeight(.semibold)
                        } else if diffResult == nil {
                            Text("比对仓库差异").fontWeight(.semibold)
                        } else {
                            Text("Push 变动文件 (\(pushCount))").fontWeight(.semibold)
                        }
                        Spacer()
                    }
                }
                .disabled(isInspecting || isPushing || pat.isEmpty || selectedFiles.isEmpty || (diffResult != nil && pushCount == 0))

                if !statusText.isEmpty {
                    Text(statusText)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("PushGit")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showTokenManager = true } label: {
                    Image(systemName: "key")
                }
            }
        }
        .sheet(isPresented: $showTokenManager) {
            NavigationStack {
                List {
                    Section("添加新 Token") {
                        HStack {
                            TextField("输入 ghp_... Token", text: $newTokenInput)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            Button("保存") {
                                Task { await saveNewToken() }
                            }
                            .disabled(newTokenInput.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                    Section("已保存账号") {
                        ForEach(savedTokens) { item in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("@\(item.username)").fontWeight(.semibold)
                                    Text("\(item.token.prefix(10))...").font(.system(size: 11)).foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                pat = item.token
                                connectedUser = item.username
                                showTokenManager = false
                                Task { await fetchMeta() }
                            }
                        }
                        .onDelete { idxs in
                            savedTokens.remove(atOffsets: idxs)
                            persistTokens()
                        }
                    }
                }
                .navigationTitle("管理 Token")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("关闭") { showTokenManager = false }
                    }
                }
            }
        }
        .task {
            loadSavedTokens()
            if !pat.isEmpty { await fetchMeta() }
        }
        .fileImporter(isPresented: $showZipImporter, allowedContentTypes: [.zip], allowsMultipleSelection: false) { res in
            if case .success(let urls) = res, let u = urls.first {
                diffResult = nil
                loadZip(u)
            }
        }
        .fileImporter(isPresented: $showFolderImporter, allowedContentTypes: [.folder], allowsMultipleSelection: false) { res in
            if case .success(let urls) = res, let u = urls.first {
                diffResult = nil
                loadFolder(u)
            }
        }
        .fileImporter(isPresented: $showFilesImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { res in
            if case .success(let urls) = res {
                diffResult = nil
                loadFiles(urls)
            }
        }
    }

    private func loadSavedTokens() {
        if let d = UserDefaults.standard.data(forKey: "jools_pushgit_tokens"),
           let arr = try? JSONDecoder().decode([SavedToken].self, from: d) {
            savedTokens = arr
        }
    }

    private func persistTokens() {
        if let d = try? JSONEncoder().encode(savedTokens) {
            UserDefaults.standard.set(d, forKey: "jools_pushgit_tokens")
        }
    }

    private func saveNewToken() async {
        let t = newTokenInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        if let uJson = try? await ghRequestWithToken(method: "GET", urlStr: "https://api.github.com/user", token: t, body: nil) as? [String: Any],
           let login = uJson["login"] as? String {
            savedTokens.removeAll { $0.token == t }
            savedTokens.insert(SavedToken(id: UUID().uuidString, username: login, token: t), at: 0)
            persistTokens()
            pat = t
            connectedUser = login
            newTokenInput = ""
            await fetchMeta()
        } else {
            statusText = "Token 无效"
        }
    }

    private func cleanRepoName(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "https://github.com/", with: "")
            .replacingOccurrences(of: "http://github.com/", with: "")
            .replacingOccurrences(of: ".git", with: "")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func fetchMeta() async {
        if let uJson = try? await ghRequest(method: "GET", urlStr: "https://api.github.com/user", body: nil) as? [String: Any],
           let login = uJson["login"] as? String {
            connectedUser = login
        }
        if let arr = try? await ghRequest(method: "GET", urlStr: "https://api.github.com/user/repos?affiliation=owner,collaborator,organization_member&per_page=100&sort=updated", body: nil) as? [[String: Any]] {
            let list = arr.compactMap { $0["full_name"] as? String }
            repos = list
            if !list.isEmpty && !list.contains(repoText) {
                repoText = list[0]
            }
        }
        await fetchBranchesOnly()
    }

    private func fetchBranchesOnly() async {
        let repo = cleanRepoName(repoText)
        if let bArr = try? await ghRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/branches?per_page=100", body: nil) as? [[String: Any]] {
            let names = bArr.compactMap { $0["name"] as? String }
            if !names.isEmpty { branches = names }
        }
    }

    private func loadZip(_ url: URL) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            statusText = "读取压缩包失败"
            return
        }
        let entries = parseZipEntries(data)
        selectedFiles = entries
        statusText = "已解析 \(entries.count) 个文件"
    }

    private func loadFolder(_ url: URL) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        var out: [PushFile] = []
        if let enumerator = FileManager.default.enumerator(at: url, includingPropertiesForKeys: [.isDirectoryKey]) {
            for case let fileUrl as URL in enumerator {
                let isDir = (try? fileUrl.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
                if isDir { continue }
                let rel = fileUrl.path.replacingOccurrences(of: url.path + "/", with: "")
                if rel.hasPrefix(".git/") || rel.contains("/.DS_Store") || rel.hasSuffix(".DS_Store") { continue }
                if let d = try? Data(contentsOf: fileUrl) {
                    out.append(PushFile(path: rel, data: d))
                }
            }
        }
        selectedFiles = out
        statusText = "已读取 \(out.count) 个文件"
    }

    private func loadFiles(_ urls: [URL]) {
        let prefix = targetSubpath.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        var out: [PushFile] = []
        for u in urls {
            let accessed = u.startAccessingSecurityScopedResource()
            defer { if accessed { u.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: u) {
                let rel = prefix.isEmpty ? u.lastPathComponent : "\(prefix)/\(u.lastPathComponent)"
                out.append(PushFile(path: rel, data: d))
            }
        }
        selectedFiles = out
        statusText = "已选择 \(out.count) 个文件"
    }

    private func gitBlobSha1(_ data: Data) -> String {
        var headerData = Data("blob \(data.count)\0".utf8)
        headerData.append(data)
        let digest = Insecure.SHA1.hash(data: headerData)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private func inspectDiff() async {
        isInspecting = true
        defer { isInspecting = false }
        let repo = cleanRepoName(repoText)
        let br = branch.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            guard let refObj = try await ghRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/ref/heads/\(br)", body: nil) as? [String: Any],
                  let obj = refObj["object"] as? [String: Any],
                  let headSha = obj["sha"] as? String,
                  let commitObj = try await ghRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/commits/\(headSha)", body: nil) as? [String: Any],
                  let treeObj = commitObj["tree"] as? [String: Any],
                  let baseTreeSha = treeObj["sha"] as? String,
                  let treeResp = try await ghRequest(method: "GET", urlStr: "https://api.github.com/repos/\(repo)/git/trees/\(baseTreeSha)?recursive=1", body: nil) as? [String: Any],
                  let treeArr = treeResp["tree"] as? [[String: Any]] else {
                statusText = "比对失败"
                return
            }
            var remoteBlobs: [String: String] = [:]
            for item in treeArr where (item["type"] as? String) == "blob" {
                if let p = item["path"] as? String, let s = item["sha"] as? String {
                    remoteBlobs[p] = s
                }
            }
            var added: [PushFile] = []
            var modified: [PushFile] = []
            var unchanged = 0
            var localPaths = Set<String>()
            for file in selectedFiles {
                localPaths.insert(file.path)
                if let rSha = remoteBlobs[file.path] {
                    if gitBlobSha1(file.data) != rSha { modified.append(file) }
                    else { unchanged += 1 }
                } else {
                    added.append(file)
                }
            }
            let deleted = remoteBlobs.keys.filter { !localPaths.contains($0) }.sorted()
            diffResult = DiffResult(headSha: headSha, baseTreeSha: baseTreeSha, added: added, modified: modified, deleted: deleted, unchangedCount: unchanged)
            statusText = "比对完成：\(added.count) 新增，\(modified.count) 修改"
        } catch {
            statusText = "比对失败: \(error.localizedDescription)"
        }
    }

    private func pushDiffChanges() async {
        guard let diff = diffResult else { return }
        UserDefaults.standard.set(pat, forKey: "jools_pushgit_pat")
        UserDefaults.standard.set(repoText, forKey: "jools_pushgit_repo")
        UserDefaults.standard.set(branch, forKey: "jools_pushgit_branch")
        UserDefaults.standard.set(commitMessage, forKey: "jools_pushgit_msg")

        isPushing = true
        defer { isPushing = false }
        let repo = cleanRepoName(repoText)
        let br = branch.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            var treeEntries: [[String: Any]] = []
            let filesToUpload = diff.uploadFiles
            for (idx, file) in filesToUpload.enumerated() {
                statusText = "推送中 (\(idx + 1)/\(filesToUpload.count)): \(file.path)"
                let blobBody: [String: Any] = ["content": file.data.base64EncodedString(), "encoding": "base64"]
                if let bResp = try await ghRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/blobs", body: blobBody) as? [String: Any],
                   let bSha = bResp["sha"] as? String {
                    treeEntries.append(["path": file.path, "mode": "100644", "type": "blob", "sha": bSha])
                }
            }
            if syncDeletions {
                for delPath in diff.deleted {
                    treeEntries.append(["path": delPath, "mode": "100644", "type": "blob", "sha": NSNull()])
                }
            }

            let treeBody: [String: Any] = ["base_tree": diff.baseTreeSha, "tree": treeEntries]
            guard let newTree = try await ghRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/trees", body: treeBody) as? [String: Any],
                  let newTreeSha = newTree["sha"] as? String,
                  let newCommit = try await ghRequest(method: "POST", urlStr: "https://api.github.com/repos/\(repo)/git/commits", body: ["message": commitMessage, "tree": newTreeSha, "parents": [diff.headSha]]) as? [String: Any],
                  let newCommitSha = newCommit["sha"] as? String else {
                statusText = "提交失败"
                return
            }

            _ = try await ghRequest(method: "PATCH", urlStr: "https://api.github.com/repos/\(repo)/git/refs/heads/\(br)", body: ["sha": newCommitSha, "force": false])
            diffResult = nil
            statusText = "推送成功: \(filesToUpload.count) 个文件 (\(newCommitSha.prefix(7)))"
        } catch {
            statusText = "推送失败: \(error.localizedDescription)"
        }
    }

    private func ghRequest(method: String, urlStr: String, body: [String: Any]?) async throws -> Any {
        return try await ghRequestWithToken(method: method, urlStr: urlStr, token: pat, body: body)
    }

    private func ghRequestWithToken(method: String, urlStr: String, token: String, body: [String: Any]?) async throws -> Any {
        guard let url = URL(string: urlStr) else { throw URLError(.badURL) }
        var req = URLRequest(url: url, timeoutInterval: 25)
        req.httpMethod = method
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("Bearer \(token.trimmingCharacters(in: .whitespacesAndNewlines))", forHTTPHeaderField: "Authorization")
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw NSError(domain: "GitHub", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode)"])
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    private func parseZipEntries(_ data: Data) -> [PushFile] {
        var result: [PushFile] = []
        var idx = 0
        let bytes = [UInt8](data)
        func u16(_ o: Int) -> Int { Int(bytes[o]) | (Int(bytes[o + 1]) << 8) }
        func u32(_ o: Int) -> Int { Int(bytes[o]) | (Int(bytes[o + 1]) << 8) | (Int(bytes[o + 2]) << 16) | (Int(bytes[o + 3]) << 24) }

        while idx + 30 <= bytes.count {
            if u32(idx) != 0x04034B50 { break }
            let method = u16(idx + 8)
            let compSize = u32(idx + 18)
            let uncompSize = u32(idx + 22)
            let nameLen = u16(idx + 26)
            let extraLen = u16(idx + 28)
            let dataStart = idx + 30 + nameLen + extraLen
            let dataEnd = dataStart + compSize
            if dataEnd > bytes.count { break }

            let nameData = Data(bytes[(idx + 30)..<(idx + 30 + nameLen)])
            let rawName = String(data: nameData, encoding: .utf8) ?? ""
            let name = rawName.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if !name.isEmpty && !rawName.hasSuffix("/") && !name.hasPrefix("__MACOSX/") && !name.hasSuffix(".DS_Store") && !name.hasPrefix(".git/") {
                let slice = Data(bytes[dataStart..<dataEnd])
                if method == 0 {
                    result.append(PushFile(path: name, data: slice))
                } else if method == 8 && uncompSize > 0 {
                    if let decompressed = inflateRaw(slice, expectedSize: uncompSize) {
                        result.append(PushFile(path: name, data: decompressed))
                    }
                }
            }
            idx = dataEnd
        }
        return result
    }

    private func inflateRaw(_ compressed: Data, expectedSize: Int) -> Data? {
        var dest = Data(count: expectedSize + 64)
        let decodedCount = dest.withUnsafeMutableBytes { destPtr in
            compressed.withUnsafeBytes { srcPtr in
                compression_decode_buffer(
                    destPtr.bindMemory(to: UInt8.self).baseAddress!,
                    expectedSize + 64,
                    srcPtr.bindMemory(to: UInt8.self).baseAddress!,
                    compressed.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
        }
        guard decodedCount > 0 else { return nil }
        return dest.prefix(decodedCount)
    }
}
