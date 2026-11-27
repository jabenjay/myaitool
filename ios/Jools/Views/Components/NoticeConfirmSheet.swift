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
        let isUsage = item.type == .usageNotice

        ZStack {
            Color.black.opacity(0.42)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // 精致顶部图标与标题栏
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: isUsage
                                        ? [Color(red: 0.14, green: 0.54, blue: 0.98), Color(red: 0.04, green: 0.36, blue: 0.88)]
                                        : [Color(red: 0.98, green: 0.56, blue: 0.16), Color(red: 0.88, green: 0.38, blue: 0.08)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 40, height: 40)
                        Image(systemName: isUsage ? "checkmark.shield.fill" : "megaphone.fill")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.primary)
                        Text(isUsage ? "使用前请完整阅读以下条款" : "最新通知")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    if isUsage && remainingSeconds > 0 {
                        Text("\(remainingSeconds)s")
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                            .foregroundColor(.accentColor)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(Color.accentColor.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                .padding(.bottom, 12)

                Divider().opacity(0.5)

                // 内容滚动区
                ScrollView(showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(item.content.components(separatedBy: .newlines).enumerated()), id: \.offset) { _, rawLine in
                            let line = rawLine.trimmingCharacters(in: .whitespaces)
                            if !line.isEmpty {
                                if line.hasPrefix("# ") {
                                    Text(line.dropFirst(2))
                                        .font(.system(size: 15.5, weight: .bold))
                                        .foregroundColor(.primary)
                                        .padding(.top, 2)
                                } else if line.hasPrefix("## ") {
                                    Text(line.dropFirst(3))
                                        .font(.system(size: 13.5, weight: .semibold))
                                        .foregroundColor(.accentColor)
                                        .padding(.top, 4)
                                } else if line.hasPrefix("- ") {
                                    HStack(alignment: .top, spacing: 6) {
                                        Text("•")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.accentColor)
                                        Text(line.dropFirst(2).replacingOccurrences(of: "**", with: ""))
                                            .font(.system(size: 13))
                                            .foregroundColor(.secondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                } else {
                                    Text(line.replacingOccurrences(of: "**", with: ""))
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                        .lineSpacing(3)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }

                        if isUsage, let don = item.donationConfig, don.enabled {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(don.title)
                                    .font(.system(size: 14, weight: .semibold))
                                if !don.description.isEmpty {
                                    Text(don.description)
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }
                                HStack(spacing: 10) {
                                    if let aliImg = JoolsQRGenerator.makeQRImage(from: don.alipayUrl) {
                                        VStack(spacing: 5) {
                                            Image(uiImage: aliImg)
                                                .interpolation(.none)
                                                .resizable()
                                                .frame(width: 88, height: 88)
                                            Text("支付宝")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(Color(red: 0.086, green: 0.467, blue: 1.0))
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(8)
                                        .background(Color.white)
                                        .cornerRadius(12)
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blue.opacity(0.35), lineWidth: 1))
                                    }
                                    if let wxImg = JoolsQRGenerator.makeQRImage(from: don.wechatUrl) {
                                        VStack(spacing: 5) {
                                            Image(uiImage: wxImg)
                                                .interpolation(.none)
                                                .resizable()
                                                .frame(width: 88, height: 88)
                                            Text("微信支付")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(Color(red: 0.027, green: 0.757, blue: 0.376))
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(8)
                                        .background(Color.white)
                                        .cornerRadius(12)
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.green.opacity(0.35), lineWidth: 1))
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
                                        Label(don.alipayLinkText, systemImage: "arrow.up.right.circle.fill")
                                            .font(.system(size: 12.5, weight: .medium))
                                            .foregroundColor(.accentColor)
                                    }
                                }
                            }
                            .padding(12)
                            .background(Color.primary.opacity(0.04))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .padding(.top, 4)
                        }

                        // Sentinel confirming scroll reached bottom
                        Color.clear
                            .frame(height: 1)
                            .onAppear { hasReachedBottom = true }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                }
                .frame(maxHeight: isUsage ? 320 : 260)

                // 底部确认区
                VStack(spacing: 10) {
                    Divider().opacity(0.5)

                    if isUsage {
                        Button {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.78)) {
                                isAgreed.toggle()
                            }
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: isAgreed ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(isAgreed ? .accentColor : .secondary)
                                    .font(.system(size: 18))
                                Text("我已完整阅读、充分理解并自愿同意上述全部条款")
                                    .font(.system(size: 12.5, weight: .medium))
                                    .foregroundColor(.primary)
                                    .multilineTextAlignment(.leading)
                                Spacer()
                            }
                            .padding(.horizontal, 18)
                        }
                        .buttonStyle(.plain)
                    }

                    let canConfirm = hasReachedBottom && remainingSeconds <= 0 && isAgreed
                    let btnTitle: String = {
                        if !hasReachedBottom {
                            return remainingSeconds > 0 ? "请滑到底部继续 (\(remainingSeconds)s)" : "请滑到底部继续"
                        }
                        if !isAgreed {
                            return remainingSeconds > 0 ? "请勾选同意条款 (\(remainingSeconds)s)" : "请勾选同意条款"
                        }
                        return remainingSeconds > 0 ? "我已知晓 (\(remainingSeconds)s)" : "我已知晓"
                    }()

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onAcknowledge()
                    } label: {
                        Text(btnTitle)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(canConfirm ? Color.accentColor : Color.secondary.opacity(0.35))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(!canConfirm)
                    .padding(.horizontal, 18)
                    .padding(.bottom, 16)
                }
            }
            .frame(maxWidth: 356)
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .stroke(Color.white.opacity(0.28), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.22), radius: 24, x: 0, y: 12)
            .padding(.horizontal, 20)
        }
        .task(id: item.contentHash) {
            while remainingSeconds > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if remainingSeconds > 0 { remainingSeconds -= 1 }
            }
        }
    }
}
