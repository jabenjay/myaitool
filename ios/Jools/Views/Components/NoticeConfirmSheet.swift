import SwiftUI
import UIKit
import CoreImage.CIFilterBuiltins

enum JoolsQRGenerator {
    static func makeQRImage(from text: String, size: CGFloat = 360) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scale = size / max(outputImage.extent.size.width, 1)
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

struct NoticeConfirmSheet: View {
    let item: PendingNoticeItem
    let onAcknowledge: () -> Void

    @State private var remainingSeconds: Int
    @State private var isAgreed: Bool
    @State private var hasReachedBottom: Bool = false

    init(item: PendingNoticeItem, onAcknowledge: @escaping () -> Void) {
        self.item = item
        self.onAcknowledge = onAcknowledge
        _remainingSeconds = State(initialValue: item.type == .usageNotice ? 15 : 0)
        _isAgreed = State(initialValue: item.type != .usageNotice)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(item.content.components(separatedBy: .newlines).enumerated()), id: \.offset) { _, rawLine in
                            let line = rawLine.trimmingCharacters(in: .whitespaces)
                            if !line.isEmpty {
                                if line.hasPrefix("# ") {
                                    Text(line.dropFirst(2))
                                        .font(.system(size: 17, weight: .bold))
                                        .foregroundColor(.accentColor)
                                } else if line.hasPrefix("## ") {
                                    Text(line.dropFirst(3))
                                        .font(.system(size: 14.5, weight: .semibold))
                                        .padding(.top, 4)
                                } else if line.hasPrefix("- ") {
                                    Text("• \(line.dropFirst(2).replacingOccurrences(of: "**", with: ""))")
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                } else {
                                    Text(line.replacingOccurrences(of: "**", with: ""))
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                }
                            }
                        }

                        if item.type == .usageNotice, let don = item.donationConfig, don.enabled {
                            Divider().padding(.vertical, 6)
                            Text(don.title)
                                .font(.system(size: 15, weight: .semibold))
                            if !don.description.isEmpty {
                                Text(don.description)
                                    .font(.system(size: 12.5))
                                    .foregroundColor(.secondary)
                            }
                            HStack(spacing: 12) {
                                if let aliImg = JoolsQRGenerator.makeQRImage(from: don.alipayUrl) {
                                    VStack(spacing: 6) {
                                        Image(uiImage: aliImg)
                                            .interpolation(.none)
                                            .resizable()
                                            .frame(width: 110, height: 110)
                                        Text("支付宝")
                                            .font(.system(size: 11.5, weight: .bold))
                                            .foregroundColor(Color(red: 0.086, green: 0.467, blue: 1.0))
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(10)
                                    .background(Color.white)
                                    .cornerRadius(12)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blue.opacity(0.5), lineWidth: 1.2))
                                }
                                if let wxImg = JoolsQRGenerator.makeQRImage(from: don.wechatUrl) {
                                    VStack(spacing: 6) {
                                        Image(uiImage: wxImg)
                                            .interpolation(.none)
                                            .resizable()
                                            .frame(width: 110, height: 110)
                                        Text("微信支付")
                                            .font(.system(size: 11.5, weight: .bold))
                                            .foregroundColor(Color(red: 0.027, green: 0.757, blue: 0.376))
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(10)
                                    .background(Color.white)
                                    .cornerRadius(12)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.green.opacity(0.5), lineWidth: 1.2))
                                }
                            }
                            if !don.alipayUrl.isEmpty {
                                Button {
                                    let encoded = don.alipayUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? don.alipayUrl
                                    if let appUrl = URL(string: "alipays://platformapi/startapp?saId=10000007&qrcode=\(encoded)"),
                                       UIApplication.shared.canOpenURL(appUrl) {
                                        UIApplication.shared.open(appUrl)
                                    } else if let webUrl = URL(string: don.alipayUrl) {
                                        UIApplication.shared.open(webUrl)
                                    }
                                } label: {
                                    Text(don.alipayLinkText)
                                        .font(.system(size: 13))
                                        .underline()
                                        .foregroundColor(.blue)
                                }
                                .padding(.vertical, 4)
                            }
                        }

                        if item.type == .usageNotice {
                            Divider().padding(.vertical, 4)
                            Button {
                                isAgreed.toggle()
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: isAgreed ? "checkmark.square.fill" : "square")
                                        .foregroundColor(isAgreed ? .accentColor : .secondary)
                                        .font(.system(size: 20))
                                    Text("我已完整阅读、充分理解并自愿同意上述全部条款")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.primary)
                                        .multilineTextAlignment(.leading)
                                }
                            }
                            .buttonStyle(.plain)
                        }

                        // Sentinel at very bottom to confirm user scrolled to end
                        Color.clear
                            .frame(height: 1)
                            .onAppear { hasReachedBottom = true }
                    }
                    .padding(18)
                }

                VStack(spacing: 0) {
                    Divider()
                    let canConfirm = hasReachedBottom && remainingSeconds <= 0 && isAgreed
                    let btnTitle: String = {
                        if !hasReachedBottom {
                            return remainingSeconds > 0 ? "请滑动阅读至末尾 (\(remainingSeconds)s)" : "请滑动阅读至末尾"
                        }
                        if !isAgreed {
                            return remainingSeconds > 0 ? "请勾选同意条款 (\(remainingSeconds)s)" : "请勾选同意条款"
                        }
                        return remainingSeconds > 0 ? "我已知晓 (\(remainingSeconds)s)" : "我已知晓"
                    }()
                    Button(action: onAcknowledge) {
                        Text(btnTitle)
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canConfirm)
                    .padding(16)
                }
            }
            .navigationTitle(item.title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .interactiveDismissDisabled(true)
        .task(id: item.contentHash) {
            while remainingSeconds > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if remainingSeconds > 0 { remainingSeconds -= 1 }
            }
        }
    }
}
