import SwiftUI
import AVFoundation
import PhotosUI
import CoreImage.CIFilterBuiltins

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
