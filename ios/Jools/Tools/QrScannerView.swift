import SwiftUI
import AVFoundation
import PhotosUI
import CoreImage

struct QrCameraPreview: UIViewRepresentable {
    let onDetected: (String) -> Void

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onDetected: (String) -> Void
        var session: AVCaptureSession?
        init(onDetected: @escaping (String) -> Void) { self.onDetected = onDetected }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            if let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
               let val = obj.stringValue, !val.isEmpty {
                DispatchQueue.main.async {
                    self.onDetected(val)
                }
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(onDetected: onDetected) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        let session = AVCaptureSession()
        context.coordinator.session = session

        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return view }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(context.coordinator, queue: .main)
            output.metadataObjectTypes = [.qr, .ean13, .ean8, .code128]
        }

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = UIScreen.main.bounds
        view.layer.addSublayer(preview)

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        uiView.layer.sublayers?.first?.frame = uiView.bounds
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.session?.stopRunning()
    }
}

struct QrScannerView: View {
    let onOpenUrlInBrowser: (String) -> Void
    @State private var scannedValue: String?
    @State private var selectedPhoto: PhotosPickerItem?

    var body: some View {
        ZStack(alignment: .bottom) {
            QrCameraPreview { code in
                if scannedValue == nil { scannedValue = code }
            }
            .ignoresSafeArea()

            VStack(spacing: 12) {
                if let val = scannedValue {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("识别结果").font(.system(size: 13, weight: .bold))
                        Text(val).font(.system(size: 13)).textSelection(.enabled)
                        HStack(spacing: 10) {
                            Button("复制") { UIPasteboard.general.string = val }
                                .buttonStyle(.bordered)
                            if val.lowercased().hasPrefix("http://") || val.lowercased().hasPrefix("https://") {
                                Button("在浏览器打开") { onOpenUrlInBrowser(val) }
                                    .buttonStyle(.borderedProminent)
                            }
                            Button("继续扫") { scannedValue = nil }
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
        .onChange(of: selectedPhoto) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let uiImg = UIImage(data: data),
                   let ciImg = CIImage(image: uiImg) {
                    let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])
                    let features = detector?.features(in: ciImg) as? [CIQRCodeFeature]
                    if let msg = features?.first?.messageString {
                        scannedValue = msg
                    }
                }
            }
        }
    }
}
