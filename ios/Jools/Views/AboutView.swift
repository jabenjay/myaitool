import SwiftUI

struct AboutView: View {
    @ObservedObject var prefs: AppPreferences
    let onOpenRepo: (String) -> Void
    let onOpenLogs: () -> Void

    @State private var selectedPayTab = 0
    @State private var toastMsg: String?

    private let alipayUrl = "https://qr.alipay.com/fkx19214trgp2tndpljhv7f"
    private let wechatUrl = "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"

    var body: some View {
        let isAlipay = selectedPayTab == 0
        let brandColor = isAlipay ? Color(red: 0.086, green: 0.467, blue: 1.0) : Color(red: 0.027, green: 0.757, blue: 0.376)
        let payUrl = isAlipay ? alipayUrl : wechatUrl

        ScrollView {
            VStack(spacing: 14) {
                // App Info
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Jools").font(.system(size: 22, weight: .bold))
                        Spacer()
                        Text("v\(AppUpdateChecker.currentVersionName)")
                            .font(.system(size: 12, weight: .semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.accentColor.opacity(0.14))
                            .foregroundColor(.accentColor)
                            .clipShape(Capsule())
                    }
                    Text("轻量、纯粹的个人手机工具箱")
                        .font(.system(size: 13.5))
                        .foregroundColor(.secondary)

                    Button {
                        onOpenRepo(AppUpdateChecker.repoURL)
                    } label: {
                        HStack {
                            Image(systemName: "chevron.left.forwardslash.chevron.right")
                                .foregroundColor(.accentColor)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("开源地址").font(.system(size: 11)).foregroundColor(.secondary)
                                Text(AppUpdateChecker.repoURL).font(.system(size: 12.5, weight: .medium)).lineLimit(1)
                            }
                            Spacer()
                            Button {
                                UIPasteboard.general.string = AppUpdateChecker.repoURL
                                toastMsg = "已复制开源地址"
                            } label: {
                                Image(systemName: "doc.on.doc")
                            }
                        }
                        .padding(12)
                        .background(Color.primary.opacity(0.05))
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
                .padding(18)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)

                // 运行日志入口
                Button(action: onOpenLogs) {
                    HStack(spacing: 12) {
                        Image(systemName: "ladybug.fill").foregroundColor(.accentColor)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("运行日志").font(.system(size: 15, weight: .semibold)).foregroundColor(.primary)
                            Text("查看运行状态与崩溃记录").font(.system(size: 12)).foregroundColor(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 16)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                }
                .buttonStyle(.plain)

                // 捐赠支持
                VStack(spacing: 14) {
                    HStack(spacing: 6) {
                        Image(systemName: "heart.fill").foregroundColor(.red)
                        Text("捐赠支持").font(.system(size: 16, weight: .bold))
                    }

                    Picker("支付方式", selection: $selectedPayTab) {
                        Text("支付宝").tag(0)
                        Text("微信支付").tag(1)
                    }
                    .pickerStyle(.segmented)

                    if let qr = JoolsQRGenerator.makeQRImage(from: payUrl, size: 420) {
                        VStack(spacing: 6) {
                            Image(uiImage: qr)
                                .interpolation(.none)
                                .resizable()
                                .frame(width: 200, height: 200)
                            Text(isAlipay ? "支付宝" : "微信支付")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(brandColor)
                        }
                        .padding(16)
                        .background(Color.white)
                        .cornerRadius(18)
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(brandColor.opacity(0.7), lineWidth: 2))
                    }

                    HStack(spacing: 10) {
                        Button {
                            if let qr = JoolsQRGenerator.makeQRImage(from: payUrl, size: 720) {
                                UIImageWriteToSavedPhotosAlbum(qr, nil, nil, nil)
                                toastMsg = "已保存收款码"
                            }
                        } label: {
                            Label("保存收款码", systemImage: "square.and.arrow.down")
                                .font(.system(size: 13))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)

                        if isAlipay {
                            Button {
                                let encoded = alipayUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? alipayUrl
                                if let appUrl = URL(string: "alipays://platformapi/startapp?saId=10000007&qrcode=\(encoded)"),
                                   UIApplication.shared.canOpenURL(appUrl) {
                                    UIApplication.shared.open(appUrl)
                                } else {
                                    toastMsg = "未安装支付宝"
                                }
                            } label: {
                                Label("打开支付宝", systemImage: "arrow.up.right.square")
                                    .font(.system(size: 13))
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(brandColor)
                        }
                    }
                }
                .padding(18)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
            }
            .padding(16)
        }
        .navigationTitle("关于")
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }
}
