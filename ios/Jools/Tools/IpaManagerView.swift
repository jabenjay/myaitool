import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct IpaManagerView: View {
    @ObservedObject var prefs: AppPreferences
    @ObservedObject private var manager = IpaManager.shared

    @State private var selectedTab: Int = 0
    @State private var showIpaImporter = false
    @State private var showP12Importer = false
    @State private var showProfileImporter = false
    @State private var showCertZipImporter = false

    @State private var pendingP12URL: URL?
    @State private var showP12PasswordAlert = false
    @State private var p12Password = ""
    @State private var p12Alias = ""

    @State private var signingTarget: IpaPackageItem?
    @State private var selectedCertId: String = ""
    @State private var selectedProfileId: String = ""
    @State private var editAppName = ""
    @State private var editBundleId = ""
    @State private var editVersion = ""

    @State private var shareFileURL: URL?
    @State private var toastMsg: String?

    var body: some View {
        mainContent
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("IPA 管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarMenu }
            .modifier(
                IpaImportersModifier(
                    manager: manager,
                    showIpaImporter: $showIpaImporter,
                    showP12Importer: $showP12Importer,
                    showProfileImporter: $showProfileImporter,
                    showCertZipImporter: $showCertZipImporter,
                    pendingP12URL: $pendingP12URL,
                    p12Password: $p12Password,
                    p12Alias: $p12Alias,
                    showP12PasswordAlert: $showP12PasswordAlert,
                    selectedTab: $selectedTab,
                    toastMsg: $toastMsg
                )
            )
            .sheet(item: $signingTarget) { target in
                signingConfigSheet(for: target)
            }
            .sheet(isPresented: shareSheetBinding) {
                if let url = shareFileURL {
                    IpaShareSheet(items: [url])
                }
            }
            .alert(toastMsg ?? "", isPresented: toastAlertBinding) {
                Button("确定", role: .cancel) {}
            }
    }

    private var shareSheetBinding: Binding<Bool> {
        Binding(get: { shareFileURL != nil }, set: { if !$0 { shareFileURL = nil } })
    }

    private var toastAlertBinding: Binding<Bool> {
        Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })
    }

    private var mainContent: some View {
        VStack(spacing: 0) {
            Picker("IPA 分类", selection: $selectedTab) {
                Text("未签名 (\(manager.unsignedIpas.count))").tag(0)
                Text("已签名 (\(manager.signedIpas.count))").tag(1)
                Text("证书 (\(manager.certs.count))").tag(2)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            if manager.isSigning {
                HStack(spacing: 10) {
                    ProgressView()
                    Text(manager.signingProgressText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.accentColor)
                }
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(Color.accentColor.opacity(0.08))
            }

            TabView(selection: $selectedTab) {
                unsignedListView.tag(0)
                signedListView.tag(1)
                certsAndProfilesView.tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
    }

    @ToolbarContentBuilder
    private var toolbarMenu: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Menu {
                Button {
                    showIpaImporter = true
                } label: {
                    Label("导入未签名 IPA", systemImage: "doc.badge.plus")
                }
                Button {
                    showCertZipImporter = true
                } label: {
                    Label("导入证书压缩包 (.zip)", systemImage: "doc.zipper")
                }
                Button {
                    showP12Importer = true
                } label: {
                    Label("导入 P12 证书", systemImage: "key.fill")
                }
                Button {
                    showProfileImporter = true
                } label: {
                    Label("导入描述文件 (.mobileprovision)", systemImage: "doc.text.fill")
                }
            } label: {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 19))
            }
        }
    }

    // MARK: - Unsigned Tab
    private var unsignedListView: some View {
        ScrollView {
            VStack(spacing: 12) {
                if manager.unsignedIpas.isEmpty {
                    emptyStateCard(
                        icon: "tray.and.arrow.down.fill",
                        title: "暂无未签名 IPA",
                        subtitle: "点击右上角 + 导入 .ipa 文件，然后选择 P12 证书进行本地签名",
                        buttonTitle: "导入 IPA"
                    ) {
                        showIpaImporter = true
                    }
                } else {
                    ForEach(manager.unsignedIpas) { item in
                        ipaCard(item: item) {
                            unsignedActions(for: item)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    private func unsignedActions(for item: IpaPackageItem) -> some View {
        HStack(spacing: 8) {
            Button {
                prepareSigning(item)
            } label: {
                Label("签名", systemImage: "signature")
                    .font(.system(size: 13, weight: .semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)

            Button {
                shareFileURL = manager.fileURL(for: item)
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(width: 32, height: 32)
                    .background(Color.primary.opacity(0.06))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)

            Button(role: .destructive) {
                manager.deleteIpa(item)
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .frame(width: 32, height: 32)
                    .background(Color.red.opacity(0.08))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Signed Tab
    private var signedListView: some View {
        ScrollView {
            VStack(spacing: 12) {
                if manager.signedIpas.isEmpty {
                    emptyStateCard(
                        icon: "checkmark.seal.fill",
                        title: "暂无已签名 IPA",
                        subtitle: "签名完成的 IPA 会单独存放在这里，可随时安装或导出",
                        buttonTitle: "去未签名列表"
                    ) {
                        selectedTab = 0
                    }
                } else {
                    ForEach(manager.signedIpas) { item in
                        ipaCard(item: item) {
                            signedActions(for: item)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    private func signedActions(for item: IpaPackageItem) -> some View {
        HStack(spacing: 8) {
            Button {
                shareFileURL = manager.fileURL(for: item)
            } label: {
                Label("安装 / 导出", systemImage: "arrow.down.app.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color(red: 0.12, green: 0.65, blue: 0.36))
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)

            Button(role: .destructive) {
                manager.deleteIpa(item)
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .frame(width: 32, height: 32)
                    .background(Color.red.opacity(0.08))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Certs & Profiles Tab
    private var certsAndProfilesView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Text("P12 签名证书 (\(manager.certs.count))")
                        .font(.system(size: 15, weight: .bold))
                    Spacer()
                    Button("导入 ZIP") { showCertZipImporter = true }
                        .font(.system(size: 13, weight: .semibold))
                    Button("导入 P12") { showP12Importer = true }
                        .font(.system(size: 13, weight: .semibold))
                }

                if manager.certs.isEmpty {
                    Text("尚未导入 P12 证书")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(18)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                } else {
                    ForEach(manager.certs) { cert in
                        certRow(cert)
                    }
                }

                HStack {
                    Text("描述文件 (\(manager.profiles.count))")
                        .font(.system(size: 15, weight: .bold))
                    Spacer()
                    Button("导入描述文件") { showProfileImporter = true }
                        .font(.system(size: 13, weight: .semibold))
                }
                .padding(.top, 6)

                if manager.profiles.isEmpty {
                    Text("尚未导入 .mobileprovision 描述文件（可选）")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(18)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
                } else {
                    ForEach(manager.profiles) { prof in
                        profileRow(prof)
                    }
                }
            }
            .padding(16)
        }
    }

    private func certRow(_ cert: P12CertItem) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "key.fill")
                .font(.system(size: 18))
                .foregroundColor(.white)
                .frame(width: 40, height: 40)
                .background(Color.orange.gradient)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(cert.name).font(.system(size: 14.5, weight: .semibold))
                Text(cert.commonName)
                    .font(.system(size: 11.5))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button(role: .destructive) {
                manager.deleteCert(cert)
            } label: {
                Image(systemName: "trash").foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
    }

    private func profileRow(_ prof: ProvisionProfileItem) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.fill")
                .font(.system(size: 18))
                .foregroundColor(.white)
                .frame(width: 40, height: 40)
                .background(Color.blue.gradient)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(prof.name).font(.system(size: 14.5, weight: .semibold))
                Text("\(prof.profileType) · \(prof.appId)")
                    .font(.system(size: 11.5))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button(role: .destructive) {
                manager.deleteProfile(prof)
            } label: {
                Image(systemName: "trash").foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 18)
    }

    // MARK: - Signing Sheet
    private func prepareSigning(_ item: IpaPackageItem) {
        if manager.certs.isEmpty {
            toastMsg = "请先在「证书」页导入 P12 证书后再签名"
            selectedTab = 2
            return
        }
        selectedCertId = manager.certs.first?.id ?? ""
        selectedProfileId = manager.profiles.first?.id ?? ""
        editAppName = item.appName
        editBundleId = item.bundleId
        editVersion = item.version
        signingTarget = item
    }

    private func signingConfigSheet(for item: IpaPackageItem) -> some View {
        NavigationStack {
            Form {
                Section("签名证书与描述文件") {
                    Picker("P12 证书", selection: $selectedCertId) {
                        ForEach(manager.certs) { cert in
                            Text(cert.name).tag(cert.id)
                        }
                    }
                    Picker("描述文件", selection: $selectedProfileId) {
                        Text("不嵌入描述文件").tag("")
                        ForEach(manager.profiles) { prof in
                            Text(prof.name).tag(prof.id)
                        }
                    }
                }

                Section("应用信息（可自定义多开/改名）") {
                    TextField("应用名称", text: $editAppName)
                    TextField("Bundle ID", text: $editBundleId)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                    TextField("版本号", text: $editVersion)
                }

                Section {
                    Button {
                        guard let cert = manager.certs.first(where: { $0.id == selectedCertId }) else { return }
                        let prof = manager.profiles.first(where: { $0.id == selectedProfileId })
                        signingTarget = nil
                        Task {
                            do {
                                let signed = try await manager.signIpa(
                                    sourceItem: item,
                                    cert: cert,
                                    profile: prof,
                                    newAppName: editAppName,
                                    newBundleId: editBundleId,
                                    newVersion: editVersion
                                )
                                selectedTab = 1
                                toastMsg = "签名成功！「\(signed.appName)」已保存到已签名列表"
                            } catch {
                                toastMsg = "签名失败：\(error.localizedDescription)"
                            }
                        }
                    } label: {
                        Text("开始签名")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
            }
            .navigationTitle("IPA 签名配置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { signingTarget = nil }
                }
            }
        }
    }

    // MARK: - Cards
    private func ipaCard<Actions: View>(item: IpaPackageItem, @ViewBuilder actions: () -> Actions) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: item.isSigned
                                    ? [Color(red: 0.18, green: 0.74, blue: 0.45), Color(red: 0.08, green: 0.52, blue: 0.30)]
                                    : [Color(red: 0.22, green: 0.56, blue: 0.96), Color(red: 0.08, green: 0.36, blue: 0.84)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 48, height: 48)
                    Image(systemName: item.isSigned ? "checkmark.seal.fill" : "shippingbox.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(item.appName)
                            .font(.system(size: 15.5, weight: .bold))
                            .lineLimit(1)
                        Text("v\(item.version)")
                            .font(.system(size: 11, weight: .semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.accentColor.opacity(0.12))
                            .foregroundColor(.accentColor)
                            .clipShape(Capsule())
                    }
                    Text(item.bundleId)
                        .font(.system(size: 11.5, design: .monospaced))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(IpaManager.formatBytes(item.fileSize))
                        if let cert = item.signedCertName {
                            Text("· 证书: \(cert)")
                                .foregroundColor(Color(red: 0.12, green: 0.65, blue: 0.36))
                        }
                    }
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                }
                Spacer()
            }

            Divider().opacity(0.45)

            HStack {
                Text(formatDate(item.importedAt))
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                Spacer()
                actions()
            }
        }
        .padding(14)
        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
    }

    private func emptyStateCard(icon: String, title: String, subtitle: String, buttonTitle: String, action: @escaping () -> Void) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 38))
                .foregroundColor(.accentColor.opacity(0.85))
            Text(title).font(.system(size: 16, weight: .bold))
            Text(subtitle)
                .font(.system(size: 12.5))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button(buttonTitle, action: action)
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(28)
        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
    }

    private func formatDate(_ ts: TimeInterval) -> String {
        let f = DateFormatter()
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.string(from: Date(timeIntervalSince1970: ts))
    }
}

private struct IpaImportersModifier: ViewModifier {
    let manager: IpaManager
    @Binding var showIpaImporter: Bool
    @Binding var showP12Importer: Bool
    @Binding var showProfileImporter: Bool
    @Binding var showCertZipImporter: Bool
    @Binding var pendingP12URL: URL?
    @Binding var p12Password: String
    @Binding var p12Alias: String
    @Binding var showP12PasswordAlert: Bool
    @Binding var selectedTab: Int
    @Binding var toastMsg: String?

    private func handleZipImport(_ url: URL) {
        do {
            let res = try manager.importCertZip(from: url)
            selectedTab = 2
            if let firstP12 = res.pendingP12TempURLs.first {
                pendingP12URL = firstP12
                p12Password = ""
                let rawName = firstP12.deletingPathExtension().lastPathComponent
                let cleaned = rawName.replacingOccurrences(
                    of: "^jools_p12_[0-9A-Fa-f-]+_",
                    with: "",
                    options: .regularExpression
                )
                p12Alias = cleaned
                showP12PasswordAlert = true
            } else {
                toastMsg = "已自动导入 \(res.importedCerts.count) 个证书、\(res.importedProfiles.count) 个描述文件"
            }
        } catch {
            toastMsg = "导入失败：\(error.localizedDescription)"
        }
    }

    private enum ActiveImportKind: String, Identifiable {
        case ipa, certZip, p12, profile
        var id: String { rawValue }
    }

    private var activeImportBinding: Binding<ActiveImportKind?> {
        Binding(
            get: {
                if showIpaImporter { return .ipa }
                if showCertZipImporter { return .certZip }
                if showP12Importer { return .p12 }
                if showProfileImporter { return .profile }
                return nil
            },
            set: { next in
                if next == nil {
                    showIpaImporter = false
                    showCertZipImporter = false
                    showP12Importer = false
                    showProfileImporter = false
                }
            }
        )
    }

    private func handlePickedFile(_ url: URL, kind: ActiveImportKind) {
        switch kind {
        case .ipa:
            do {
                let item = try manager.importIpa(from: url, asSigned: false)
                selectedTab = 0
                toastMsg = "已导入「\(item.appName)」到未签名列表"
            } catch {
                toastMsg = "导入失败：\(error.localizedDescription)"
            }
        case .certZip:
            handleZipImport(url)
        case .p12:
            if url.pathExtension.lowercased() == "zip" {
                handleZipImport(url)
            } else {
                pendingP12URL = url
                p12Password = ""
                p12Alias = url.deletingPathExtension().lastPathComponent
                showP12PasswordAlert = true
            }
        case .profile:
            do {
                let prof = try manager.importProfile(from: url)
                selectedTab = 2
                toastMsg = "已导入描述文件「\(prof.name)」"
            } catch {
                toastMsg = "导入失败：\(error.localizedDescription)"
            }
        }
    }

    func body(content: Content) -> some View {
        content
            .sheet(item: activeImportBinding) { kind in
                IpaDocumentPickerView { url in
                    showIpaImporter = false
                    showCertZipImporter = false
                    showP12Importer = false
                    showProfileImporter = false
                    if let url {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            handlePickedFile(url, kind: kind)
                        }
                    }
                }
                .ignoresSafeArea()
            }
            .alert("导入 P12 证书", isPresented: $showP12PasswordAlert) {
                TextField("证书备注名称", text: $p12Alias)
                SecureField("P12 密码（无密码可留空）", text: $p12Password)
                Button("验证并导入") {
                    if let url = pendingP12URL {
                        do {
                            let cert = try manager.importP12(from: url, password: p12Password, customName: p12Alias)
                            selectedTab = 2
                            toastMsg = "证书「\(cert.name)」验证通过并已保存"
                        } catch {
                            toastMsg = error.localizedDescription
                        }
                    }
                }
                Button("取消", role: .cancel) {}
            }
    }
}

private struct IpaShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}


private struct IpaDocumentPickerView: UIViewControllerRepresentable {
    let onPick: (URL?) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: (URL?) -> Void
        init(onPick: @escaping (URL?) -> Void) { self.onPick = onPick }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            onPick(urls.first)
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            onPick(nil)
        }
    }
}
