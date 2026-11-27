import SwiftUI
import UIKit

struct UpdateSheet: View {
    let current: String
    let latest: String
    let name: String
    let notes: String
    let downloadUrl: String
    var onOpenInBrowser: ((String) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(name)
                                .font(.system(size: 18, weight: .bold))
                            Text("当前 v\(current) → 最新 \(latest)")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }
                    Divider()
                    Text(notes.isEmpty ? "暂无更新说明" : notes)
                        .font(.system(size: 13.5))
                        .foregroundColor(.primary)
                }
                .padding(18)
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    let target = GitHubMirrorHelper.buildCandidateURLs(downloadUrl).first ?? downloadUrl
                    dismiss()
                    if let onOpenInBrowser {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            onOpenInBrowser(target)
                        }
                    } else if let u = URL(string: target) {
                        UIApplication.shared.open(u)
                    }
                } label: {
                    Text("内置浏览器下载更新")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .padding(16)
                .background(.ultraThinMaterial)
            }
            .navigationTitle("发现新版本")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("稍后") { dismiss() }
                }
            }
        }
    }
}
