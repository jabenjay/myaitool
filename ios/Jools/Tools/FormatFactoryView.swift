import SwiftUI
import PhotosUI
import AVFoundation
import UniformTypeIdentifiers

enum ImageTargetFormat: String, CaseIterable, Identifiable {
    case png = "PNG"
    case jpeg = "JPEG"
    case heic = "HEIC"
    var id: String { rawValue }
}

struct FormatFactoryView: View {
    @ObservedObject var prefs: AppPreferences
    @State private var tab = 0 // 0: 图片转换, 1: 音视频转码
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var sourceImage: UIImage?
    @State private var targetImageFormat: ImageTargetFormat = .png
    @State private var quality: Double = 0.9
    @State private var exportedFileURL: URL?

    @State private var showMediaImporter = false
    @State private var mediaInputURL: URL?
    @State private var mediaPreset = 0 // 0: MP4, 1: M4A 音频提取
    @State private var isProcessing = false
    @State private var statusMsg = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                Picker("类型", selection: $tab) {
                    Text("图片格式转换").tag(0)
                    Text("音视频转码").tag(1)
                }
                .pickerStyle(.segmented)

                if tab == 0 {
                    VStack(spacing: 12) {
                        PhotosPicker(selection: $selectedPhoto, matching: .images) {
                            Label(sourceImage == nil ? "从相册选择图片" : "重新选择图片", systemImage: "photo.on.rectangle")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.bordered)

                        if let img = sourceImage {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 200)
                                .cornerRadius(12)
                        }

                        Picker("目标格式", selection: $targetImageFormat) {
                            ForEach(ImageTargetFormat.allCases) { f in
                                Text(f.rawValue).tag(f)
                            }
                        }
                        .pickerStyle(.segmented)

                        if targetImageFormat != .png {
                            VStack(alignment: .leading) {
                                Text("压缩质量: \(Int(quality * 100))%").font(.system(size: 12))
                                Slider(value: $quality, in: 0.1...1.0)
                            }
                        }

                        Button {
                            convertImage()
                        } label: {
                            Text("开始转换")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(sourceImage == nil)
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                } else {
                    VStack(spacing: 12) {
                        Button {
                            showMediaImporter = true
                        } label: {
                            Label(mediaInputURL?.lastPathComponent ?? "选择视频或音频文件", systemImage: "film")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.bordered)

                        Picker("输出格式", selection: $mediaPreset) {
                            Text("MP4 视频").tag(0)
                            Text("M4A 音频提取").tag(1)
                        }
                        .pickerStyle(.segmented)

                        Button {
                            transcodeMedia()
                        } label: {
                            Text(isProcessing ? "转码中..." : "开始转码")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(mediaInputURL == nil || isProcessing)
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                }

                if !statusMsg.isEmpty {
                    Text(statusMsg)
                        .font(.system(size: 12.5))
                        .foregroundColor(.secondary)
                }

                if let outUrl = exportedFileURL {
                    ShareLink(item: outUrl) {
                        Label("导出 / 分享结果文件", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding(16)
        }
        .navigationTitle("格式工厂")
        .onChange(of: selectedPhoto) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    sourceImage = img
                    exportedFileURL = nil
                    statusMsg = "已载入图片 (\(Int(img.size.width))×\(Int(img.size.height)))"
                }
            }
        }
        .fileImporter(isPresented: $showMediaImporter, allowedContentTypes: [.movie, .audio, .mpeg4Movie]) { res in
            if case .success(let url) = res {
                let accessed = url.startAccessingSecurityScopedResource()
                let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                try? FileManager.default.removeItem(at: tmp)
                try? FileManager.default.copyItem(at: url, to: tmp)
                if accessed { url.stopAccessingSecurityScopedResource() }
                mediaInputURL = tmp
                exportedFileURL = nil
                statusMsg = "已就绪: \(url.lastPathComponent)"
            }
        }
    }

    private func convertImage() {
        guard let img = sourceImage else { return }
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let ext = targetImageFormat.rawValue.lowercased()
        let outUrl = docs.appendingPathComponent("Jools_\(Int(Date().timeIntervalSince1970)).\(ext)")
        var outData: Data?
        switch targetImageFormat {
        case .png:
            outData = img.pngData()
        case .jpeg:
            outData = img.jpegData(compressionQuality: quality)
        case .heic:
            if let cg = img.cgImage {
                let mData = NSMutableData()
                if let dest = CGImageDestinationCreateWithData(mData, UTType.heic.identifier as CFString, 1, nil) {
                    CGImageDestinationAddImage(dest, cg, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
                    if CGImageDestinationFinalize(dest) { outData = mData as Data }
                }
            }
        }
        if let d = outData {
            try? d.write(to: outUrl)
            exportedFileURL = outUrl
            statusMsg = "转换成功 (\(d.count / 1024) KB)"
        } else {
            statusMsg = "转换失败"
        }
    }

    private func transcodeMedia() {
        guard let inUrl = mediaInputURL else { return }
        isProcessing = true
        statusMsg = "正在转码..."
        let asset = AVURLAsset(url: inUrl)
        let preset = mediaPreset == 0 ? AVAssetExportPresetMediumQuality : AVAssetExportPresetAppleM4A
        let fileType: AVFileType = mediaPreset == 0 ? .mp4 : .m4a
        let ext = mediaPreset == 0 ? "mp4" : "m4a"
        let outUrl = FileManager.default.temporaryDirectory.appendingPathComponent("Jools_\(Int(Date().timeIntervalSince1970)).\(ext)")
        try? FileManager.default.removeItem(at: outUrl)

        guard let exporter = AVAssetExportSession(asset: asset, presetName: preset) else {
            isProcessing = false
            statusMsg = "当前文件不支持该预设"
            return
        }
        exporter.outputURL = outUrl
        exporter.outputFileType = fileType
        exporter.exportAsynchronously {
            DispatchQueue.main.async {
                isProcessing = false
                if exporter.status == .completed {
                    exportedFileURL = outUrl
                    statusMsg = "转码完成"
                } else {
                    statusMsg = "转码失败: \(exporter.error?.localizedDescription ?? "未知错误")"
                }
            }
        }
    }
}
