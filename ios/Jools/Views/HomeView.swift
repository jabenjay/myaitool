import SwiftUI
import CoreImage.CIFilterBuiltins
import Photos

struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            ToolboxHomeView()
        }
        .sheet(isPresented: $appState.showUpdateSheet) {
            if let info = appState.activeUpdateInfo {
                UpdateBottomSheetView(info: info)
                    .presentationDetents([.medium, .large])
            }
        }
    }
}

struct ToolboxHomeView: View {
    @EnvironmentObject var appState: AppState
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(appState.toolOrder) { tool in
                        NavigationLink(value: tool) {
                            VStack(alignment: .leading, spacing: 10) {
                                Image(systemName: tool.iconName)
                                    .font(.system(size: 24, weight: .semibold))
                                    .foregroundColor(.accentColor)
                                    .frame(width: 44, height: 44)
                                    .background(Color.accentColor.opacity(0.12))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))

                                Text(tool.title)
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)

                                Text(tool.subtitle)
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                        }
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Jools")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink {
                    SettingsView()
                } label: {
                    Image(systemName: "gearshape")
                }
            }
        }
        .navigationDestination(for: ToolRoute.self) { tool in
            switch tool {
            case .qrScanner: QrScannerView()
            case .browser: BrowserView()
            case .base64: Base64ToolView()
            case .qrTool: QrToolView()
            case .formatFactory: FormatFactoryView()
            case .compressor: FileCompressorView()
            }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @State private var isCheckingUpdate = false
    @State private var updateStatusText = ""

    var body: some View {
        List {
            Section("外观与布局") {
                Picker("主题模式", selection: $appState.themePreference) {
                    ForEach(ThemePreference.allCases, id: \.self) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                NavigationLink("首页工具排序") {
                    LayoutSettingsView()
                }
            }

            Section("系统与诊断") {
                NavigationLink("运行日志") {
                    LogViewerView()
                }
                HStack {
                    Button("检查更新") {
                        isCheckingUpdate = true
                        updateStatusText = "正在检查更新..."
                        Task {
                            let info = await appState.checkUpdatesManually()
                            isCheckingUpdate = false
                            updateStatusText = info == nil ? "当前已是最新版本 (\(appState.currentAppVersion))" : "发现新版本 \(info!.version)"
                        }
                    }
                    .disabled(isCheckingUpdate)
                    Spacer()
                    if isCheckingUpdate {
                        ProgressView()
                    } else if !updateStatusText.isEmpty {
                        Text(updateStatusText)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
            }

            Section("关于") {
                NavigationLink("关于与捐赠") {
                    AboutView()
                }
            }
        }
        .navigationTitle("设置")
    }
}

struct LayoutSettingsView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        List {
            ForEach(appState.toolOrder) { tool in
                Label(tool.title, systemImage: tool.iconName)
            }
            .onMove { from, to in
                appState.toolOrder.move(fromOffsets: from, toOffset: to)
            }
        }
        .environment(\.editMode, .constant(.active))
        .navigationTitle("首页工具排序")
    }
}

struct LogViewerView: View {
    @State private var logs = AppLogManager.shared.allLogs()

    var body: some View {
        List {
            if logs.isEmpty {
                Text("暂无日志记录").foregroundColor(.secondary)
            } else {
                ForEach(logs, id: \.self) { line in
                    Text(line).font(.system(size: 12, design: .monospaced))
                }
            }
        }
        .navigationTitle("运行日志")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("清空") {
                    AppLogManager.shared.clear()
                    logs = []
                }
            }
        }
    }
}

struct AboutView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedPayTab: Int = 0
    @State private var toastMsg: String?

    private let alipayUrl = "https://qr.alipay.com/fkx19214trgp2tndpljhv7f"
    private let wechatUrl = "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 6) {
                    Text("Jools")
                        .font(.system(size: 26, weight: .bold))
                    Text("版本 \(appState.currentAppVersion)")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                    Link("GitHub 仓库：jabenjay/myaitool", destination: URL(string: "https://github.com/jabenjay/myaitool")!)
                        .font(.system(size: 13))
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))

                VStack(spacing: 14) {
                    Text("请作者喝杯咖啡")
                        .font(.system(size: 16, weight: .bold))

                    Picker("", selection: $selectedPayTab) {
                        Text("支付宝").tag(0)
                        Text("微信支付").tag(1)
                    }
                    .pickerStyle(.segmented)

                    let payUrl = selectedPayTab == 0 ? alipayUrl : wechatUrl
                    let brandColor = selectedPayTab == 0 ? Color.blue : Color.green
                    if let qrImg = generateQRImage(from: payUrl) {
                        VStack(spacing: 6) {
                            Image(uiImage: qrImg)
                                .interpolation(.none)
                                .resizable()
                                .frame(width: 200, height: 200)
                            Text(selectedPayTab == 0 ? "支付宝" : "微信支付")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(brandColor)
                        }
                        .padding(16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(brandColor.opacity(0.75), lineWidth: 2))
                    }

                    HStack(spacing: 10) {
                        Button {
                            if let img = generateQRImage(from: payUrl) {
                                UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                                toastMsg = "已保存收款码到相册"
                            }
                        } label: {
                            Label("保存收款码", systemImage: "square.and.arrow.down")
                                .font(.system(size: 12, weight: .medium))
                                .lineLimit(1)
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)

                        if selectedPayTab == 0 {
                            Button {
                                let encoded = alipayUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? alipayUrl
                                if let schemeUrl = URL(string: "alipays://platformapi/startapp?saId=10000007&qrcode=\(encoded)"),
                                   UIApplication.shared.canOpenURL(schemeUrl) {
                                    UIApplication.shared.open(schemeUrl)
                                } else {
                                    toastMsg = "未检测到支付宝，请扫码捐赠"
                                }
                            } label: {
                                Label("打开支付宝", systemImage: "arrow.up.right.square")
                                    .font(.system(size: 12, weight: .medium))
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }

                    if let msg = toastMsg {
                        Text(msg)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .padding(16)
        }
        .navigationTitle("关于与捐赠")
    }

    private func generateQRImage(from text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let ctx = CIContext()
        guard let cgImg = ctx.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImg)
    }
}

struct UpdateBottomSheetView: View {
    @Environment(\.dismiss) private var dismiss
    let info: UpdateInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("发现新版本 \(info.version)")
                .font(.system(size: 20, weight: .bold))
            ScrollView {
                Text(info.changelog.isEmpty ? "暂无更新说明" : info.changelog)
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 12) {
                Button("取消") { dismiss() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                Button("立即下载") {
                    if let url = URL(string: info.downloadUrl) {
                        UIApplication.shared.open(url)
                    }
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(20)
    }
}
