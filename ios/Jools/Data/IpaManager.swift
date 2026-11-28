import Foundation
import Security
import CommonCrypto
import Compression
import CryptoKit
import Network

enum IpaCategory: String, Codable, CaseIterable, Identifiable {
    case unsigned = "未签名"
    case signed = "已签名"
    var id: String { rawValue }
}

struct IpaPackageItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var fileName: String
    var appName: String
    var bundleId: String
    var version: String
    var buildVersion: String
    var minOSVersion: String
    var fileSize: Int64
    var importedAt: TimeInterval
    var isSigned: Bool
    var signedCertName: String?
}

struct P12CertItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var fileName: String
    var password: String
    var commonName: String
    var importedAt: TimeInterval
}

struct ProvisionProfileItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var fileName: String
    var appId: String
    var teamName: String
    var teamId: String
    var expiresAt: TimeInterval?
    var deviceCount: Int
    var profileType: String
    var importedAt: TimeInterval
}

private struct IpaMetadataStore: Codable {
    var unsignedIpas: [IpaPackageItem] = []
    var signedIpas: [IpaPackageItem] = []
    var certs: [P12CertItem] = []
    var profiles: [ProvisionProfileItem] = []
}

@MainActor
final class IpaManager: ObservableObject {
    static let shared = IpaManager()

    @Published var unsignedIpas: [IpaPackageItem] = []
    @Published var signedIpas: [IpaPackageItem] = []
    @Published var certs: [P12CertItem] = []
    @Published var profiles: [ProvisionProfileItem] = []
    @Published var isSigning: Bool = false
    @Published var signingProgressText: String = ""

    private let fm = FileManager.default
    private var baseDir: URL {
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("IPA", isDirectory: true)
    }
    private var unsignedDir: URL { baseDir.appendingPathComponent("Unsigned", isDirectory: true) }
    private var signedDir: URL { baseDir.appendingPathComponent("Signed", isDirectory: true) }
    private var certsDir: URL { baseDir.appendingPathComponent("Certs", isDirectory: true) }
    private var metadataURL: URL { baseDir.appendingPathComponent("metadata.json") }

    private init() {
        ensureDirectories()
        loadMetadata()
    }

    private func ensureDirectories() {
        for dir in [baseDir, unsignedDir, signedDir, certsDir] {
            if !fm.fileExists(atPath: dir.path) {
                try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
            }
        }
    }

    private func loadMetadata() {
        guard let data = try? Data(contentsOf: metadataURL),
              let store = try? JSONDecoder().decode(IpaMetadataStore.self, from: data) else {
            return
        }
        unsignedIpas = store.unsignedIpas.filter { fm.fileExists(atPath: fileURL(for: $0).path) }
        signedIpas = store.signedIpas.filter { fm.fileExists(atPath: fileURL(for: $0).path) }
        certs = store.certs.filter { fm.fileExists(atPath: certURL(for: $0).path) }
        profiles = store.profiles.filter { fm.fileExists(atPath: profileURL(for: $0).path) }
    }

    private func saveMetadata() {
        let store = IpaMetadataStore(
            unsignedIpas: unsignedIpas,
            signedIpas: signedIpas,
            certs: certs,
            profiles: profiles
        )
        if let data = try? JSONEncoder().encode(store) {
            try? data.write(to: metadataURL, options: .atomic)
        }
    }

    func fileURL(for item: IpaPackageItem) -> URL {
        (item.isSigned ? signedDir : unsignedDir).appendingPathComponent(item.fileName)
    }

    func certURL(for cert: P12CertItem) -> URL {
        certsDir.appendingPathComponent(cert.fileName)
    }

    func profileURL(for profile: ProvisionProfileItem) -> URL {
        certsDir.appendingPathComponent(profile.fileName)
    }

    // MARK: - Import IPA
    func importIpa(from sourceURL: URL, asSigned: Bool = false) throws -> IpaPackageItem {
        ensureDirectories()
        let accessing = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessing { sourceURL.stopAccessingSecurityScopedResource() } }

        let origName = sourceURL.lastPathComponent
        let uniqueName = "\(Int(Date().timeIntervalSince1970))_\(origName)"
        let destDir = asSigned ? signedDir : unsignedDir
        let destURL = destDir.appendingPathComponent(uniqueName)
        if fm.fileExists(atPath: destURL.path) {
            try fm.removeItem(at: destURL)
        }
        try fm.copyItem(at: sourceURL, to: destURL)

        let attrs = try fm.attributesOfItem(atPath: destURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        let head = (try? FileHandle(forReadingFrom: destURL))?.readData(ofLength: 4) ?? Data()
        if size < 22 || head.count < 2 || head[0] != 0x50 || head[1] != 0x4B {
            try? fm.removeItem(at: destURL)
            throw NSError(domain: "IpaManager", code: -10, userInfo: [
                NSLocalizedDescriptionKey: "文件不是有效的 IPA/ZIP 安装包（可能下载不完整或被镜像返回了网页）"
            ])
        }
        let info = parseIpaInfoPlist(ipaURL: destURL)

        let item = IpaPackageItem(
            fileName: uniqueName,
            appName: info.appName ?? origName.replacingOccurrences(of: ".ipa", with: ""),
            bundleId: info.bundleId ?? "未知 Bundle ID",
            version: info.version ?? "1.0",
            buildVersion: info.buildVersion ?? "1",
            minOSVersion: info.minOS ?? "14.0",
            fileSize: size,
            importedAt: Date().timeIntervalSince1970,
            isSigned: asSigned,
            signedCertName: nil
        )
        if asSigned {
            signedIpas.insert(item, at: 0)
        } else {
            unsignedIpas.insert(item, at: 0)
        }
        saveMetadata()
        return item
    }

    // MARK: - Import P12
    func importP12(from sourceURL: URL, password: String, customName: String) throws -> P12CertItem {
        ensureDirectories()
        let accessing = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessing { sourceURL.stopAccessingSecurityScopedResource() } }

        let data = try Data(contentsOf: sourceURL)
        let options = [kSecImportExportPassphrase as String: password] as CFDictionary
        var rawItems: CFArray?
        let status = SecPKCS12Import(data as CFData, options, &rawItems)

        var commonName = sourceURL.deletingPathExtension().lastPathComponent
        if status == errSecSuccess,
           let arr = rawItems as? [[String: Any]],
           let first = arr.first {
            if let identityRef = first[kSecImportItemIdentity as String] {
                let identity = identityRef as! SecIdentity
                var certRef: SecCertificate?
                if SecIdentityCopyCertificate(identity, &certRef) == errSecSuccess,
                   let cert = certRef,
                   let summary = SecCertificateCopySubjectSummary(cert) as String? {
                    commonName = summary
                }
            } else if let chain = first[kSecImportItemCertChain as String] as? [SecCertificate],
                      let cert = chain.first,
                      let summary = SecCertificateCopySubjectSummary(cert) as String? {
                commonName = summary
            }
        } else if status == errSecAuthFailed {
            throw NSError(domain: "JoolsIPA", code: Int(status), userInfo: [
                NSLocalizedDescriptionKey: "P12 密码错误"
            ])
        } else {
            // Fallback for P12 files that SecPKCS12Import rejects (e.g. OpenSSL 3 AES-256-CBC or Keychain entitlement -34018)
            switch PKCS12Fallback.verifyAndInspect(data: data, password: password) {
            case .success(let summary):
                if let s = summary, !s.isEmpty { commonName = s }
            case .wrongPassword:
                throw NSError(domain: "JoolsIPA", code: Int(errSecAuthFailed), userInfo: [
                    NSLocalizedDescriptionKey: "P12 密码错误"
                ])
            case .invalidFile:
                throw NSError(domain: "JoolsIPA", code: Int(status), userInfo: [
                    NSLocalizedDescriptionKey: "无效的 P12 证书文件 (code: \(status))"
                ])
            }
        }

        let uniqueName = "\(Int(Date().timeIntervalSince1970))_\(sourceURL.lastPathComponent)"
        let destURL = certsDir.appendingPathComponent(uniqueName)
        try data.write(to: destURL, options: .atomic)

        let displayName = customName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? commonName : customName
        let item = P12CertItem(
            name: displayName,
            fileName: uniqueName,
            password: password,
            commonName: commonName,
            importedAt: Date().timeIntervalSince1970
        )
        certs.insert(item, at: 0)
        saveMetadata()
        return item
    }

    struct ZipCertImportResult {
        var importedCerts: [P12CertItem] = []
        var importedProfiles: [ProvisionProfileItem] = []
        var pendingP12TempURLs: [URL] = []
    }

    // MARK: - Import Certificate ZIP (.p12 + .mobileprovision)
    func importCertZip(from sourceURL: URL) throws -> ZipCertImportResult {
        ensureDirectories()
        let accessing = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessing { sourceURL.stopAccessingSecurityScopedResource() } }

        let workDir = fm.temporaryDirectory.appendingPathComponent("cert_zip_\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: workDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: workDir) }

        try MiniZipArchive.unzip(fileURL: sourceURL, to: workDir)

        var result = ZipCertImportResult()
        if let enumerator = fm.enumerator(at: workDir, includingPropertiesForKeys: [.isRegularFileKey]) {
            let allUrls = (enumerator.allObjects as? [URL]) ?? []
            for fileUrl in allUrls {
                let pathStr = fileUrl.path
                let name = fileUrl.lastPathComponent
                if pathStr.contains("__MACOSX") || name.hasPrefix("._") { continue }
                let vals = try? fileUrl.resourceValues(forKeys: [.isRegularFileKey])
                guard vals?.isRegularFile == true else { continue }

                let ext = fileUrl.pathExtension.lowercased()
                if ext == "mobileprovision" {
                    if let prof = try? importProfile(from: fileUrl) {
                        result.importedProfiles.append(prof)
                    }
                } else if ext == "p12" {
                    if let cert = try? importP12(from: fileUrl, password: "", customName: "") {
                        result.importedCerts.append(cert)
                    } else {
                        let tempP12 = fm.temporaryDirectory.appendingPathComponent("jools_p12_\(UUID().uuidString)_\(name)")
                        try? fm.removeItem(at: tempP12)
                        try fm.copyItem(at: fileUrl, to: tempP12)
                        result.pendingP12TempURLs.append(tempP12)
                    }
                }
            }
        }

        if result.importedCerts.isEmpty && result.importedProfiles.isEmpty && result.pendingP12TempURLs.isEmpty {
            throw NSError(domain: "JoolsIPA", code: -3, userInfo: [
                NSLocalizedDescriptionKey: "压缩包中未找到 .p12 证书或 .mobileprovision 描述文件"
            ])
        }
        return result
    }

    // MARK: - Import MobileProvision
    func importProfile(from sourceURL: URL) throws -> ProvisionProfileItem {
        ensureDirectories()
        let accessing = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessing { sourceURL.stopAccessingSecurityScopedResource() } }

        let data = try Data(contentsOf: sourceURL)
        guard let parsed = parseMobileProvision(data: data) else {
            throw NSError(domain: "JoolsIPA", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "无法解析描述文件 (.mobileprovision)"
            ])
        }

        let uniqueName = "\(Int(Date().timeIntervalSince1970))_\(sourceURL.lastPathComponent)"
        let destURL = certsDir.appendingPathComponent(uniqueName)
        try data.write(to: destURL, options: .atomic)

        let item = ProvisionProfileItem(
            name: parsed.name,
            fileName: uniqueName,
            appId: parsed.appId,
            teamName: parsed.teamName,
            teamId: parsed.teamId,
            expiresAt: parsed.expiresAt,
            deviceCount: parsed.deviceCount,
            profileType: parsed.profileType,
            importedAt: Date().timeIntervalSince1970
        )
        profiles.insert(item, at: 0)
        saveMetadata()
        return item
    }

    // MARK: - Delete
    func deleteIpa(_ item: IpaPackageItem) {
        try? fm.removeItem(at: fileURL(for: item))
        if item.isSigned {
            signedIpas.removeAll { $0.id == item.id }
        } else {
            unsignedIpas.removeAll { $0.id == item.id }
        }
        saveMetadata()
    }

    func deleteCert(_ cert: P12CertItem) {
        try? fm.removeItem(at: certURL(for: cert))
        certs.removeAll { $0.id == cert.id }
        saveMetadata()
    }

    func deleteProfile(_ profile: ProvisionProfileItem) {
        try? fm.removeItem(at: profileURL(for: profile))
        profiles.removeAll { $0.id == profile.id }
        saveMetadata()
    }

    // MARK: - Sign IPA
    func signIpa(
        sourceItem: IpaPackageItem,
        cert: P12CertItem,
        profile: ProvisionProfileItem?,
        newAppName: String,
        newBundleId: String,
        newVersion: String
    ) async throws -> IpaPackageItem {
        isSigning = true
        signingProgressText = "正在验证 P12 证书..."
        defer {
            isSigning = false
            signingProgressText = ""
        }

        let sourceURL = fileURL(for: sourceItem)
        let certFileURL = certURL(for: cert)
        let profileFileURL = profile.map { profileURL(for: $0) }
        let p12Data = try Data(contentsOf: certFileURL)

        let options = [kSecImportExportPassphrase as String: cert.password] as CFDictionary
        var rawItems: CFArray?
        let status = SecPKCS12Import(p12Data as CFData, options, &rawItems)
        var privateKey: SecKey? = nil
        if status == errSecSuccess,
           let arr = rawItems as? [[String: Any]],
           let first = arr.first,
           let identityRef = first[kSecImportItemIdentity as String] {
            let identity = identityRef as! SecIdentity
            SecIdentityCopyPrivateKey(identity, &privateKey)
        } else if status == errSecAuthFailed {
            throw NSError(domain: "JoolsIPA", code: Int(status), userInfo: [
                NSLocalizedDescriptionKey: "证书加载失败，请检查 P12 密码"
            ])
        } else {
            if case .wrongPassword = PKCS12Fallback.verifyAndInspect(data: p12Data, password: cert.password) {
                throw NSError(domain: "JoolsIPA", code: Int(errSecAuthFailed), userInfo: [
                    NSLocalizedDescriptionKey: "证书加载失败，请检查 P12 密码"
                ])
            }
        }

        let workDir = fm.temporaryDirectory.appendingPathComponent("ipa_sign_\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: workDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: workDir) }

        signingProgressText = "正在解压 IPA..."
        try MiniZipArchive.unzip(fileURL: sourceURL, to: workDir)

        let payloadDir = workDir.appendingPathComponent("Payload", isDirectory: true)
        let contents = (try? fm.contentsOfDirectory(at: payloadDir, includingPropertiesForKeys: nil)) ?? []
        guard let appBundle = contents.first(where: { $0.pathExtension.lowercased() == "app" }) else {
            throw NSError(domain: "JoolsIPA", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "IPA 中未找到 Payload/*.app 目录"
            ])
        }

        signingProgressText = "正在写入应用信息与描述文件..."
        let infoPlistURL = appBundle.appendingPathComponent("Info.plist")
        var finalName = sourceItem.appName
        var finalBundleId = sourceItem.bundleId
        var finalVersion = sourceItem.version

        if let plistData = try? Data(contentsOf: infoPlistURL),
           var dict = (try? PropertyListSerialization.propertyList(from: plistData, options: [], format: nil)) as? [String: Any] {
            let trimmedName = newAppName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedName.isEmpty {
                dict["CFBundleDisplayName"] = trimmedName
                dict["CFBundleName"] = trimmedName
                finalName = trimmedName
            }
            let trimmedId = newBundleId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedId.isEmpty {
                dict["CFBundleIdentifier"] = trimmedId
                finalBundleId = trimmedId
            }
            let trimmedVer = newVersion.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedVer.isEmpty {
                dict["CFBundleShortVersionString"] = trimmedVer
                finalVersion = trimmedVer
            }
            let updatedData = try PropertyListSerialization.data(fromPropertyList: dict, format: .binary, options: 0)
            try updatedData.write(to: infoPlistURL, options: .atomic)
        }

        if let profURL = profileFileURL {
            let embeddedURL = appBundle.appendingPathComponent("embedded.mobileprovision")
            if fm.fileExists(atPath: embeddedURL.path) {
                try fm.removeItem(at: embeddedURL)
            }
            try fm.copyItem(at: profURL, to: embeddedURL)
        }

        signingProgressText = "正在生成签名摘要 (_CodeSignature)..."
        let codeSigDir = appBundle.appendingPathComponent("_CodeSignature", isDirectory: true)
        try fm.createDirectory(at: codeSigDir, withIntermediateDirectories: true)

        var filesMap: [String: Any] = [:]
        var files2Map: [String: Any] = [:]
        if let enumerator = fm.enumerator(at: appBundle, includingPropertiesForKeys: [.isRegularFileKey]) {
            let basePath = appBundle.path + "/"
            let allUrls = (enumerator.allObjects as? [URL]) ?? []
            for fileUrl in allUrls {
                let rel = fileUrl.path.replacingOccurrences(of: basePath, with: "")
                if rel.hasPrefix("_CodeSignature/") || rel == "Info.plist" { continue }
                let vals = try? fileUrl.resourceValues(forKeys: [.isRegularFileKey])
                guard vals?.isRegularFile == true, let fData = try? Data(contentsOf: fileUrl) else { continue }
                let sha1 = Data(Insecure.SHA1.hash(data: fData))
                let sha256 = Data(SHA256.hash(data: fData))
                filesMap[rel] = sha1
                files2Map[rel] = ["hash": sha1, "hash2": sha256]
            }
        }
        let codeResources: [String: Any] = [
            "files": filesMap,
            "files2": files2Map,
            "rules": ["^.*": true],
            "rules2": ["^.*": true]
        ]
        let codeResData = try PropertyListSerialization.data(fromPropertyList: codeResources, format: .xml, options: 0)
        try codeResData.write(to: codeSigDir.appendingPathComponent("CodeResources"), options: .atomic)

        if let key = privateKey {
            let alg: SecKeyAlgorithm = SecKeyIsAlgorithmSupported(key, .sign, .rsaSignatureMessagePKCS1v15SHA256)
                ? .rsaSignatureMessagePKCS1v15SHA256
                : .ecdsaSignatureMessageX962SHA256
            if SecKeyIsAlgorithmSupported(key, .sign, alg),
               let sig = SecKeyCreateSignature(key, alg, codeResData as CFData, nil) as Data? {
                let sigMeta: [String: Any] = [
                    "Signer": cert.commonName,
                    "BundleIdentifier": finalBundleId,
                    "Version": finalVersion,
                    "SignedAt": Date().timeIntervalSince1970,
                    "CodeResourcesSignature": sig
                ]
                if let sigData = try? PropertyListSerialization.data(fromPropertyList: sigMeta, format: .binary, options: 0) {
                    try sigData.write(to: codeSigDir.appendingPathComponent("JoolsSignature.plist"), options: .atomic)
                }
            }
        }

        signingProgressText = "正在打包已签名 IPA..."
        let safeName = finalName.replacingOccurrences(of: "/", with: "_")
        let outFileName = "\(safeName)_v\(finalVersion)_signed_\(Int(Date().timeIntervalSince1970)).ipa"
        let outURL = signedDir.appendingPathComponent(outFileName)
        try MiniZipArchive.zip(directoryURL: workDir, to: outURL)

        let attrs = try fm.attributesOfItem(atPath: outURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0

        let signedItem = IpaPackageItem(
            fileName: outFileName,
            appName: finalName,
            bundleId: finalBundleId,
            version: finalVersion,
            buildVersion: sourceItem.buildVersion,
            minOSVersion: sourceItem.minOSVersion,
            fileSize: size,
            importedAt: Date().timeIntervalSince1970,
            isSigned: true,
            signedCertName: cert.name
        )
        signedIpas.insert(signedItem, at: 0)
        saveMetadata()
        return signedItem
    }

    // MARK: - Helpers
    private func parseIpaInfoPlist(ipaURL: URL) -> (appName: String?, bundleId: String?, version: String?, buildVersion: String?, minOS: String?) {
        guard let plistData = MiniZipArchive.extractFirstInfoPlist(from: ipaURL),
              let dict = try? PropertyListSerialization.propertyList(from: plistData, options: [], format: nil) as? [String: Any] else {
            return (nil, nil, nil, nil, nil)
        }
        let appName = (dict["CFBundleDisplayName"] as? String) ?? (dict["CFBundleName"] as? String)
        let bundleId = dict["CFBundleIdentifier"] as? String
        let version = dict["CFBundleShortVersionString"] as? String
        let build = dict["CFBundleVersion"] as? String
        let minOS = dict["MinimumOSVersion"] as? String
        return (appName, bundleId, version, build, minOS)
    }

    private func parseMobileProvision(data: Data) -> (name: String, appId: String, teamName: String, teamId: String, expiresAt: TimeInterval?, deviceCount: Int, profileType: String)? {
        guard let startRange = data.range(of: Data("<?xml".utf8)),
              let endRange = data.range(of: Data("</plist>".utf8), in: startRange.lowerBound..<data.endIndex) else {
            return nil
        }
        let xmlData = data.subdata(in: startRange.lowerBound..<endRange.upperBound)
        guard let dict = try? PropertyListSerialization.propertyList(from: xmlData, options: [], format: nil) as? [String: Any] else {
            return nil
        }
        let name = (dict["Name"] as? String) ?? "描述文件"
        let teamName = (dict["TeamName"] as? String) ?? ""
        let teamId = (dict["TeamIdentifier"] as? [String])?.first ?? ""
        let entitlements = dict["Entitlements"] as? [String: Any]
        let appId = (entitlements?["application-identifier"] as? String) ?? "*"
        let expDate = dict["ExpirationDate"] as? Date
        let devices = dict["ProvisionedDevices"] as? [String] ?? []
        let isEnterprise = (dict["ProvisionsAllDevices"] as? Bool) == true
        let getTaskAllow = (entitlements?["get-task-allow"] as? Bool) == true
        let pType: String = isEnterprise ? "企业证书 (In-House)" : (getTaskAllow ? "开发证书 (Development)" : "分发证书 (AdHoc/AppStore)")
        return (name, appId, teamName, teamId, expDate?.timeIntervalSince1970, devices.count, pType)
    }

    static func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}

// MARK: - Pure Swift ZIP Reader/Writer (Stored + Raw Deflate via Compression)
enum MiniZipArchive {
    static func extractFirstInfoPlist(from ipaURL: URL) -> Data? {
        guard let data = try? Data(contentsOf: ipaURL) else { return nil }
        let entries = parseCentralDirectory(data: data)
        for entry in entries {
            let lower = entry.name.lowercased()
            if lower.hasPrefix("payload/") && lower.hasSuffix(".app/info.plist") {
                return extractEntryData(entry, from: data)
            }
        }
        return nil
    }

    static func unzip(fileURL: URL, to destDir: URL) throws {
        let data = try Data(contentsOf: fileURL)
        let entries = parseCentralDirectory(data: data)
        let fm = FileManager.default
        for entry in entries {
            if entry.name.contains("..") { continue }
            let target = destDir.appendingPathComponent(entry.name)
            if entry.name.hasSuffix("/") {
                try fm.createDirectory(at: target, withIntermediateDirectories: true)
            } else {
                try fm.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
                if let fileData = extractEntryData(entry, from: data) {
                    try fileData.write(to: target, options: .atomic)
                }
            }
        }
    }

    static func zip(directoryURL: URL, to outURL: URL) throws {
        let fm = FileManager.default
        var centralRecords: [Data] = []
        var archiveData = Data()
        let basePath = directoryURL.path.hasSuffix("/") ? directoryURL.path : directoryURL.path + "/"

        guard let enumerator = fm.enumerator(at: directoryURL, includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey]) else {
            throw NSError(domain: "MiniZip", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法遍历打包目录"])
        }

        for case let url as URL in enumerator {
            let vals = try? url.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey])
            let rel = url.path.replacingOccurrences(of: basePath, with: "")
            if rel.isEmpty { continue }

            if vals?.isDirectory == true {
                let dirName = rel.hasSuffix("/") ? rel : rel + "/"
                let nameData = Data(dirName.utf8)
                let localOffset = UInt32(archiveData.count)
                archiveData.append(localHeader(nameData: nameData, method: 0, crc32: 0, compSize: 0, uncompSize: 0))
                archiveData.append(nameData)
                centralRecords.append(centralHeader(nameData: nameData, method: 0, crc32: 0, compSize: 0, uncompSize: 0, localOffset: localOffset, isDir: true))
            } else if vals?.isRegularFile == true {
                let raw = try Data(contentsOf: url)
                let crc = computeCRC32(raw)
                let compressed = deflateRaw(raw)
                let useDeflate = compressed != nil && compressed!.count < raw.count
                let payload = useDeflate ? compressed! : raw
                let method: UInt16 = useDeflate ? 8 : 0
                let nameData = Data(rel.utf8)
                let localOffset = UInt32(archiveData.count)

                archiveData.append(localHeader(nameData: nameData, method: method, crc32: crc, compSize: UInt32(payload.count), uncompSize: UInt32(raw.count)))
                archiveData.append(nameData)
                archiveData.append(payload)

                centralRecords.append(centralHeader(nameData: nameData, method: method, crc32: crc, compSize: UInt32(payload.count), uncompSize: UInt32(raw.count), localOffset: localOffset, isDir: false))
            }
        }

        let cdOffset = UInt32(archiveData.count)
        for rec in centralRecords { archiveData.append(rec) }
        let cdSize = UInt32(archiveData.count) - cdOffset

        var eocd = Data()
        eocd.appendUInt32(0x06054b50)
        eocd.appendUInt16(0)
        eocd.appendUInt16(0)
        eocd.appendUInt16(UInt16(centralRecords.count))
        eocd.appendUInt16(UInt16(centralRecords.count))
        eocd.appendUInt32(cdSize)
        eocd.appendUInt32(cdOffset)
        eocd.appendUInt16(0)
        archiveData.append(eocd)

        try archiveData.write(to: outURL, options: .atomic)
    }

    private struct ZipEntry {
        let name: String
        let method: UInt16
        let compSize: Int
        let uncompSize: Int
        let localHeaderOffset: Int
    }

    private static func parseCentralDirectory(data: Data) -> [ZipEntry] {
        // Find End of Central Directory (0x06054b50) from tail
        let minOffset = max(0, data.count - 65557)
        var eocdOffset: Int?
        var i = data.count - 22
        while i >= minOffset {
            if data.readUInt32(at: i) == 0x06054b50 {
                eocdOffset = i
                break
            }
            i -= 1
        }
        guard let eocd = eocdOffset else { return [] }
        let totalEntries = Int(data.readUInt16(at: eocd + 10))
        let cdOffset = Int(data.readUInt32(at: eocd + 16))
        var cursor = cdOffset
        var entries: [ZipEntry] = []

        for _ in 0..<totalEntries {
            guard cursor + 46 <= data.count, data.readUInt32(at: cursor) == 0x02014b50 else { break }
            let method = data.readUInt16(at: cursor + 10)
            let compSize = Int(data.readUInt32(at: cursor + 20))
            let uncompSize = Int(data.readUInt32(at: cursor + 24))
            let nameLen = Int(data.readUInt16(at: cursor + 28))
            let extraLen = Int(data.readUInt16(at: cursor + 30))
            let commentLen = Int(data.readUInt16(at: cursor + 32))
            let localOffset = Int(data.readUInt32(at: cursor + 42))
            let nameStart = cursor + 46
            if nameStart + nameLen <= data.count {
                let name = String(data: data.subdata(in: nameStart..<(nameStart + nameLen)), encoding: .utf8) ?? ""
                entries.append(ZipEntry(name: name, method: method, compSize: compSize, uncompSize: uncompSize, localHeaderOffset: localOffset))
            }
            cursor += 46 + nameLen + extraLen + commentLen
        }
        return entries
    }

    private static func extractEntryData(_ entry: ZipEntry, from data: Data) -> Data? {
        let lh = entry.localHeaderOffset
        guard lh + 30 <= data.count, data.readUInt32(at: lh) == 0x04034b50 else { return nil }
        let nameLen = Int(data.readUInt16(at: lh + 26))
        let extraLen = Int(data.readUInt16(at: lh + 28))
        let dataStart = lh + 30 + nameLen + extraLen
        guard dataStart + entry.compSize <= data.count else { return nil }
        let slice = data.subdata(in: dataStart..<(dataStart + entry.compSize))
        if entry.method == 0 { return slice }
        if entry.method == 8 { return inflateRaw(slice, expectedSize: max(entry.uncompSize, 1)) }
        return nil
    }

    private static func inflateRaw(_ compressed: Data, expectedSize: Int) -> Data? {
        var dstSize = max(expectedSize, compressed.count * 4)
        for _ in 0..<4 {
            var dst = [UInt8](repeating: 0, count: dstSize)
            let decoded = compressed.withUnsafeBytes { srcPtr -> Int in
                guard let base = srcPtr.bindMemory(to: UInt8.self).baseAddress else { return 0 }
                return compression_decode_buffer(&dst, dstSize, base, compressed.count, nil, COMPRESSION_ZLIB)
            }
            if decoded > 0 && decoded < dstSize {
                return Data(dst[0..<decoded])
            }
            if decoded == expectedSize && decoded > 0 {
                return Data(dst[0..<decoded])
            }
            dstSize *= 2
        }
        return nil
    }

    private static func deflateRaw(_ raw: Data) -> Data? {
        guard !raw.isEmpty else { return Data() }
        let dstCap = raw.count + 512
        var dst = [UInt8](repeating: 0, count: dstCap)
        let encoded = raw.withUnsafeBytes { srcPtr -> Int in
            guard let base = srcPtr.bindMemory(to: UInt8.self).baseAddress else { return 0 }
            return compression_encode_buffer(&dst, dstCap, base, raw.count, nil, COMPRESSION_ZLIB)
        }
        guard encoded > 0 else { return nil }
        return Data(dst[0..<encoded])
    }

    private static func localHeader(nameData: Data, method: UInt16, crc32: UInt32, compSize: UInt32, uncompSize: UInt32) -> Data {
        var d = Data()
        d.appendUInt32(0x04034b50)
        d.appendUInt16(20)
        d.appendUInt16(0x0800)
        d.appendUInt16(method)
        d.appendUInt16(0)
        d.appendUInt16(0)
        d.appendUInt32(crc32)
        d.appendUInt32(compSize)
        d.appendUInt32(uncompSize)
        d.appendUInt16(UInt16(nameData.count))
        d.appendUInt16(0)
        return d
    }

    private static func centralHeader(nameData: Data, method: UInt16, crc32: UInt32, compSize: UInt32, uncompSize: UInt32, localOffset: UInt32, isDir: Bool) -> Data {
        var d = Data()
        d.appendUInt32(0x02014b50)
        d.appendUInt16(20)
        d.appendUInt16(20)
        d.appendUInt16(0x0800)
        d.appendUInt16(method)
        d.appendUInt16(0)
        d.appendUInt16(0)
        d.appendUInt32(crc32)
        d.appendUInt32(compSize)
        d.appendUInt32(uncompSize)
        d.appendUInt16(UInt16(nameData.count))
        d.appendUInt16(0)
        d.appendUInt16(0)
        d.appendUInt16(0)
        d.appendUInt16(0)
        d.appendUInt32(isDir ? 0x10 : 0)
        d.appendUInt32(localOffset)
        d.append(nameData)
        return d
    }

    private static func computeCRC32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                let mask = (crc & 1) == 1 ? UInt32(0xEDB88320) : 0
                crc = (crc >> 1) ^ mask
            }
        }
        return ~crc
    }
}

private extension Data {
    func readUInt16(at offset: Int) -> UInt16 {
        UInt16(self[offset]) | (UInt16(self[offset + 1]) << 8)
    }
    func readUInt32(at offset: Int) -> UInt32 {
        UInt32(self[offset])
            | (UInt32(self[offset + 1]) << 8)
            | (UInt32(self[offset + 2]) << 16)
            | (UInt32(self[offset + 3]) << 24)
    }
    mutating func appendUInt16(_ value: UInt16) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
    }
    mutating func appendUInt32(_ value: UInt32) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
        append(UInt8((value >> 16) & 0xFF))
        append(UInt8((value >> 24) & 0xFF))
    }
}


private enum PKCS12Fallback {
    enum Result {
        case success(summary: String?)
        case wrongPassword
        case invalidFile
    }

    private struct TLV {
        let tag: UInt8
        let value: Data
        let raw: Data
    }

    static func verifyAndInspect(data: Data, password: String) -> Result {
        var off = 0
        guard let pfx = readTLV(data, &off), pfx.tag == 0x30 else { return .invalidFile }
        var pfxOff = 0
        guard let ver = readTLV(pfx.value, &pfxOff), ver.tag == 0x02,
              let authSafe = readTLV(pfx.value, &pfxOff), authSafe.tag == 0x30 else {
            return .invalidFile
        }
        // Optional MacData
        if let macData = readTLV(pfx.value, &pfxOff), macData.tag == 0x30 {
            if let authSafeContent = extractContentInfoOctetOrRaw(authSafe.value) {
                var macOff = 0
                if let digestInfo = readTLV(macData.value, &macOff), digestInfo.tag == 0x30,
                   let saltTLV = readTLV(macData.value, &macOff), saltTLV.tag == 0x04 {
                    var iter = 1
                    if let iterTLV = readTLV(macData.value, &macOff), iterTLV.tag == 0x02 {
                        iter = readInt(iterTLV.value)
                    }
                    var diOff = 0
                    if let algSeq = readTLV(digestInfo.value, &diOff), algSeq.tag == 0x30,
                       let digestTLV = readTLV(digestInfo.value, &diOff), digestTLV.tag == 0x04 {
                        var algOff = 0
                        if let oidTLV = readTLV(algSeq.value, &algOff), oidTLV.tag == 0x06 {
                            let useSha256 = (oidTLV.value == Data([0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01]))
                            let useSha1 = (oidTLV.value == Data([0x2B, 0x0E, 0x03, 0x02, 0x1A]))
                            if useSha256 || useSha1 {
                                let macKey = pkcs12Kdf(
                                    password: password,
                                    salt: saltTLV.value,
                                    iterations: max(1, iter),
                                    id: 3,
                                    keyLen: useSha256 ? 32 : 20,
                                    useSha256: useSha256
                                )
                                let computed = hmac(key: macKey, data: authSafeContent, useSha256: useSha256)
                                if computed != digestTLV.value {
                                    return .wrongPassword
                                }
                            }
                        }
                    }
                }
            }
        }
        return .success(summary: nil)
    }

    private static func extractContentInfoOctetOrRaw(_ contentInfo: Data) -> Data? {
        var off = 0
        guard let _ = readTLV(contentInfo, &off),
              let ctx = readTLV(contentInfo, &off) else { return nil }
        var ctxOff = 0
        if let inner = readTLV(ctx.value, &ctxOff), inner.tag == 0x04 {
            return inner.value
        }
        return ctx.value
    }

    private static func readInt(_ d: Data) -> Int {
        var v = 0
        for b in d { v = (v << 8) | Int(b) }
        return v
    }

    private static func readTLV(_ data: Data, _ offset: inout Int) -> TLV? {
        guard offset < data.count else { return nil }
        let start = offset
        let tag = data[offset]
        offset += 1
        guard offset < data.count else { return nil }
        let firstLen = data[offset]
        offset += 1
        if firstLen == 0x80 {
            // Indefinite length BER: scan until 0x00 0x00 at top level (bounded fallback)
            let valStart = offset
            var depth = 1
            var scan = offset
            while scan + 1 < data.count {
                if data[scan] == 0x00 && data[scan + 1] == 0x00 {
                    depth -= 1
                    if depth == 0 {
                        let val = data.subdata(in: valStart..<scan)
                        offset = scan + 2
                        return TLV(tag: tag, value: val, raw: data.subdata(in: start..<offset))
                    }
                    scan += 2
                    continue
                }
                scan += 1
            }
            return nil
        }
        let len: Int
        if firstLen & 0x80 == 0 {
            len = Int(firstLen)
        } else {
            let n = Int(firstLen & 0x7F)
            guard n > 0, n <= 4, offset + n <= data.count else { return nil }
            var acc = 0
            for _ in 0..<n {
                acc = (acc << 8) | Int(data[offset])
                offset += 1
            }
            len = acc
        }
        guard len >= 0, offset + len <= data.count else { return nil }
        let val = data.subdata(in: offset..<(offset + len))
        offset += len
        return TLV(tag: tag, value: val, raw: data.subdata(in: start..<offset))
    }

    private static func hmac(key: Data, data: Data, useSha256: Bool) -> Data {
        let alg = CCHmacAlgorithm(useSha256 ? kCCHmacAlgSHA256 : kCCHmacAlgSHA1)
        let outLen = Int(useSha256 ? CC_SHA256_DIGEST_LENGTH : CC_SHA1_DIGEST_LENGTH)
        var out = [UInt8](repeating: 0, count: outLen)
        key.withUnsafeBytes { kp in
            data.withUnsafeBytes { dp in
                CCHmac(alg, kp.baseAddress, key.count, dp.baseAddress, data.count, &out)
            }
        }
        return Data(out)
    }

    private static func hash(_ data: Data, useSha256: Bool) -> Data {
        if useSha256 {
            var out = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
            data.withUnsafeBytes { _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &out) }
            return Data(out)
        } else {
            var out = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
            data.withUnsafeBytes { _ = CC_SHA1($0.baseAddress, CC_LONG(data.count), &out) }
            return Data(out)
        }
    }

    private static func pkcs12Kdf(password: String, salt: Data, iterations: Int, id: UInt8, keyLen: Int, useSha256: Bool) -> Data {
        let v = 64
        let u = useSha256 ? 32 : 20
        var passBytes = Data()
        for scalar in password.utf16 {
            passBytes.append(UInt8((scalar >> 8) & 0xFF))
            passBytes.append(UInt8(scalar & 0xFF))
        }
        passBytes.append(0)
        passBytes.append(0)

        let D = Data(repeating: id, count: v)
        func repeatToBlock(_ src: Data) -> Data {
            if src.isEmpty { return Data() }
            let total = ((src.count + v - 1) / v) * v
            var out = Data(capacity: total)
            while out.count < total {
                out.append(src)
            }
            return out.prefix(total)
        }
        let S = repeatToBlock(salt)
        let P = repeatToBlock(passBytes)
        var I = S + P

        var derived = Data()
        while derived.count < keyLen {
            var A = D + I
            for _ in 0..<iterations {
                A = hash(A, useSha256: useSha256)
            }
            derived.append(A)
            if derived.count >= keyLen { break }

            let B = repeatToBlock(A)
            var newI = Data()
            var pos = 0
            while pos + v <= I.count {
                var carry: UInt16 = 1
                var block = [UInt8](repeating: 0, count: v)
                for j in stride(from: v - 1, through: 0, by: -1) {
                    let sum = UInt16(I[pos + j]) + UInt16(B[j]) + carry
                    block[j] = UInt8(sum & 0xFF)
                    carry = sum >> 8
                }
                newI.append(contentsOf: block)
                pos += v
            }
            I = newI
        }
        return derived.prefix(keyLen)
    }
}
