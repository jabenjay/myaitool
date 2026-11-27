import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

enum SimpleZipArchive {
    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320 : (crc >> 1)
            }
        }
        return ~crc
    }

    private static func appendLE16(_ d: inout Data, _ v: UInt16) {
        var le = v.littleEndian
        d.append(Data(bytes: &le, count: 2))
    }

    private static func appendLE32(_ d: inout Data, _ v: UInt32) {
        var le = v.littleEndian
        d.append(Data(bytes: &le, count: 4))
    }

    static func createZip(entries: [(name: String, data: Data)], to outUrl: URL) throws {
        var out = Data()
        var centralDir = Data()
        var offset: UInt32 = 0

        for entry in entries {
            let nameData = Data(entry.name.utf8)
            let crc = crc32(entry.data)
            let size = UInt32(entry.data.count)

            // Local file header
            appendLE32(&out, 0x04034b50)
            appendLE16(&out, 20)
            appendLE16(&out, 0x0800) // UTF-8
            appendLE16(&out, 0) // store
            appendLE16(&out, 0)
            appendLE16(&out, 0)
            appendLE32(&out, crc)
            appendLE32(&out, size)
            appendLE32(&out, size)
            appendLE16(&out, UInt16(nameData.count))
            appendLE16(&out, 0)
            out.append(nameData)
            out.append(entry.data)

            // Central directory header
            appendLE32(&centralDir, 0x02014b50)
            appendLE16(&centralDir, 20)
            appendLE16(&centralDir, 20)
            appendLE16(&centralDir, 0x0800)
            appendLE16(&centralDir, 0)
            appendLE16(&centralDir, 0)
            appendLE16(&centralDir, 0)
            appendLE32(&centralDir, crc)
            appendLE32(&centralDir, size)
            appendLE32(&centralDir, size)
            appendLE16(&centralDir, UInt16(nameData.count))
            appendLE16(&centralDir, 0)
            appendLE16(&centralDir, 0)
            appendLE16(&centralDir, 0)
            appendLE16(&centralDir, 0)
            appendLE32(&centralDir, 0)
            appendLE32(&centralDir, offset)
            centralDir.append(nameData)

            offset = UInt32(out.count)
        }

        let cdOffset = UInt32(out.count)
        let cdSize = UInt32(centralDir.count)
        out.append(centralDir)

        // End of central directory
        appendLE32(&out, 0x06054b50)
        appendLE16(&out, 0)
        appendLE16(&out, 0)
        appendLE16(&out, UInt16(entries.count))
        appendLE16(&out, UInt16(entries.count))
        appendLE32(&out, cdSize)
        appendLE32(&out, cdOffset)
        appendLE16(&out, 0)

        try out.write(to: outUrl, options: .atomic)
    }
}

struct FileCompressorView: View {
    @ObservedObject var prefs: AppPreferences
    @State private var tab = 0 // 0: 图片压缩, 1: 文件ZIP打包
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var origImageData: Data?
    @State private var compressedData: Data?
    @State private var quality: Double = 0.65

    @State private var showFilesImporter = false
    @State private var pickedFiles: [(name: String, data: Data)] = []
    @State private var zipOutputUrl: URL?
    @State private var statusMsg = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                Picker("模式", selection: $tab) {
                    Text("图片压缩").tag(0)
                    Text("ZIP 打包").tag(1)
                }
                .pickerStyle(.segmented)

                if tab == 0 {
                    VStack(spacing: 12) {
                        PhotosPicker(selection: $selectedPhoto, matching: .images) {
                            Label("选择图片", systemImage: "photo")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.bordered)

                        VStack(alignment: .leading) {
                            Text("压缩质量: \(Int(quality * 100))%").font(.system(size: 12))
                            Slider(value: $quality, in: 0.1...0.95)
                        }

                        Button {
                            compressPhoto()
                        } label: {
                            Text("压缩图片")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(origImageData == nil)

                        if let orig = origImageData, let comp = compressedData {
                            Text("原图 \(orig.count / 1024) KB → 压缩后 \(comp.count / 1024) KB")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(.accentColor)
                            Button("保存到相册") {
                                if let img = UIImage(data: comp) {
                                    UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                                    statusMsg = "已保存到相册"
                                }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                } else {
                    VStack(spacing: 12) {
                        Button {
                            showFilesImporter = true
                        } label: {
                            Label("选择要打包的文件 (\(pickedFiles.count))", systemImage: "doc.badge.plus")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.bordered)

                        Button {
                            packZip()
                        } label: {
                            Text("生成 ZIP 压缩包")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(pickedFiles.isEmpty)

                        if let zUrl = zipOutputUrl {
                            ShareLink(item: zUrl) {
                                Label("导出 / 分享 ZIP", systemImage: "square.and.arrow.up")
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                }

                if !statusMsg.isEmpty {
                    Text(statusMsg).font(.system(size: 12)).foregroundColor(.secondary)
                }
            }
            .padding(16)
        }
        .navigationTitle("文件压缩")
        .onChange(of: selectedPhoto) { item in
            Task {
                if let d = try? await item?.loadTransferable(type: Data.self) {
                    origImageData = d
                    compressedData = nil
                }
            }
        }
        .fileImporter(isPresented: $showFilesImporter, allowedContentTypes: [.data], allowsMultipleSelection: true) { res in
            if case .success(let urls) = res {
                var list: [(String, Data)] = []
                for u in urls {
                    let acc = u.startAccessingSecurityScopedResource()
                    if let d = try? Data(contentsOf: u) { list.append((u.lastPathComponent, d)) }
                    if acc { u.stopAccessingSecurityScopedResource() }
                }
                pickedFiles = list
                zipOutputUrl = nil
            }
        }
    }

    private func compressPhoto() {
        guard let d = origImageData, let img = UIImage(data: d) else { return }
        compressedData = img.jpegData(compressionQuality: quality)
    }

    private func packZip() {
        let outUrl = FileManager.default.temporaryDirectory.appendingPathComponent("Jools_Archive_\(Int(Date().timeIntervalSince1970)).zip")
        try? FileManager.default.removeItem(at: outUrl)
        do {
            try SimpleZipArchive.createZip(entries: pickedFiles, to: outUrl)
            zipOutputUrl = outUrl
            statusMsg = "打包成功 (\(pickedFiles.count) 个文件)"
        } catch {
            statusMsg = "打包失败: \(error.localizedDescription)"
        }
    }
}
