import SwiftUI
import AVFoundation
import PhotosUI
import CoreImage
import UIKit

struct QrCameraPreview: UIViewRepresentable {
    let isActive: Bool
    let onDetected: (String) -> Void
    let onPermissionDenied: () -> Void

    final class PreviewUIView: UIView {
        var previewLayer: AVCaptureVideoPreviewLayer?

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer?.frame = bounds
        }
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        var isActive: Bool
        let onDetected: (String) -> Void
        let onPermissionDenied: () -> Void
        var session: AVCaptureSession?
        private var isConfigured = false

        init(isActive: Bool, onDetected: @escaping (String) -> Void, onPermissionDenied: @escaping () -> Void) {
            self.isActive = isActive
            self.onDetected = onDetected
            self.onPermissionDenied = onPermissionDenied
        }

        func startIfAuthorized(in view: PreviewUIView) {
            let status = AVCaptureDevice.authorizationStatus(for: .video)
            switch status {
            case .authorized:
                configureAndStart(in: view)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { [weak self, weak view] granted in
                    DispatchQueue.main.async {
                        guard let self, let view else { return }
                        if granted {
                            self.configureAndStart(in: view)
                        } else {
                            self.onPermissionDenied()
                        }
                    }
                }
            case .denied, .restricted:
                DispatchQueue.main.async {
                    self.onPermissionDenied()
                }
            @unknown default:
                break
            }
        }

        private func configureAndStart(in view: PreviewUIView) {
            if isConfigured {
                if let session, !session.isRunning {
                    DispatchQueue.global(qos: .userInitiated).async {
                        session.startRunning()
                    }
                }
                return
            }

            let session = AVCaptureSession()
            session.beginConfiguration()

            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else {
                session.commitConfiguration()
                return
            }
            session.addInput(input)

            let output = AVCaptureMetadataOutput()
            if session.canAddOutput(output) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main)
                let wanted: [AVMetadataObject.ObjectType] = [.qr, .ean13, .ean8, .code128, .dataMatrix, .pdf417]
                output.metadataObjectTypes = wanted.filter { output.availableMetadataObjectTypes.contains($0) }
            }
            session.commitConfiguration()

            let preview = AVCaptureVideoPreviewLayer(session: session)
            preview.videoGravity = .resizeAspectFill
            preview.frame = view.bounds
            view.previewLayer?.removeFromSuperlayer()
            view.previewLayer = preview
            view.layer.insertSublayer(preview, at: 0)

            self.session = session
            self.isConfigured = true

            DispatchQueue.global(qos: .userInitiated).async {
                session.startRunning()
            }
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard isActive else { return }
            if let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
               let val = obj.stringValue, !val.isEmpty {
                isActive = false
                DispatchQueue.main.async {
                    self.onDetected(val)
                }
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(isActive: isActive, onDetected: onDetected, onPermissionDenied: onPermissionDenied)
    }

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.backgroundColor = .black
        context.coordinator.startIfAuthorized(in: view)
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {
        context.coordinator.isActive = isActive
        uiView.previewLayer?.frame = uiView.bounds
        if isActive, let session = context.coordinator.session, !session.isRunning {
            DispatchQueue.global(qos: .userInitiated).async {
                session.startRunning()
            }
        }
    }

    static func dismantleUIView(_ uiView: PreviewUIView, coordinator: Coordinator) {
        let session = coordinator.session
        DispatchQueue.global(qos: .userInitiated).async {
            session?.stopRunning()
        }
    }
}

struct QrScannerView: View {
    let onOpenUrlInBrowser: (String) -> Void
    @State private var scannedValue: String?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var permissionDenied = false
    @State private var isScanningActive = true

    var body: some View {
        ZStack(alignment: .bottom) {
            QrCameraPreview(
                isActive: isScanningActive,
                onDetected: { code in
                    handleScannedCode(code)
                },
                onPermissionDenied: {
                    permissionDenied = true
                }
            )
            .ignoresSafeArea()

            if permissionDenied {
                VStack(spacing: 12) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 32))
                        .foregroundColor(.white)
                    Text("未获得相机权限，请在系统设置中开启相机访问权限")
                        .font(.system(size: 13.5))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    Button("前往系统设置") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(20)
                .background(.ultraThinMaterial)
                .cornerRadius(18)
                .padding(24)
                .frame(maxHeight: .infinity, alignment: .center)
            }

            VStack(spacing: 12) {
                if let val = scannedValue {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("识别结果").font(.system(size: 13, weight: .bold))
                        Text(val).font(.system(size: 13)).textSelection(.enabled)
                        HStack(spacing: 10) {
                            Button("复制") { UIPasteboard.general.string = val }
                                .buttonStyle(.bordered)
                            if isHttpUrl(val) {
                                Button("在浏览器打开") { onOpenUrlInBrowser(val) }
                                    .buttonStyle(.borderedProminent)
                            } else if let appUrl = URL(string: val),
                                      let scheme = appUrl.scheme,
                                      !scheme.isEmpty,
                                      scheme.lowercased() != "file" {
                                Button("打开链接") {
                                    UIApplication.shared.open(appUrl)
                                }
                                .buttonStyle(.borderedProminent)
                            }
                            Button("继续扫") {
                                scannedValue = nil
                                isScanningActive = true
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.ultraThinMaterial)
                    .cornerRadius(16)
                }

                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    Label("从相册识别二维码", systemImage: "photo.on.rectangle")
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial)
                        .clipShape(Capsule())
                }
            }
            .padding(16)
        }
        .navigationTitle("扫一扫")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if scannedValue == nil {
                isScanningActive = true
            }
        }
        .onChange(of: selectedPhoto) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let uiImg = UIImage(data: data),
                   let ciImg = CIImage(image: uiImg) {
                    let detector = CIDetector(
                        ofType: CIDetectorTypeQRCode,
                        context: nil,
                        options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
                    )
                    let features = detector?.features(in: ciImg) as? [CIQRCodeFeature]
                    if let msg = features?.first?.messageString {
                        handleScannedCode(msg)
                    }
                }
            }
        }
    }

    private func isHttpUrl(_ str: String) -> Bool {
        let lower = str.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return lower.hasPrefix("http://") || lower.hasPrefix("https://")
    }

    private func handleScannedCode(_ raw: String) {
        let code = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else { return }
        isScanningActive = false
        scannedValue = code
        UINotificationFeedbackGenerator().notificationOccurred(.success)

        if isHttpUrl(code) {
            onOpenUrlInBrowser(code)
        } else if let url = URL(string: code),
                  let scheme = url.scheme?.lowercased(),
                  !scheme.isEmpty,
                  scheme != "file" {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }
}
