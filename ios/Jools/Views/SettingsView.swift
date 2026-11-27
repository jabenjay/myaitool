import SwiftUI

struct SettingsView: View {
    @ObservedObject var prefs: AppPreferences
    let onOpenProxy: () -> Void
    let onOpenAbout: () -> Void

    @State private var selectedChannel: UpdateChannel = AppUpdateChecker.getSelectedChannel()
    @State private var isCheckingUpdate = false
    @State private var updateResult: UpdateCheckResult?
    @State private var statusAlert: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("设置")
                    .font(.system(size: 30, weight: .bold))
                    .padding(.horizontal, 4)
                    .padding(.top, 8)

                VStack(spacing: 12) {
                    // 主题
                    HStack(spacing: 14) {
                        Image(systemName: "paintpalette.fill")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("主题").font(.system(size: 15))
                            Text("\(prefs.uiStyle.label) · \(prefs.themeMode.label)")
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Picker("", selection: $prefs.themeMode) {
                            ForEach(AppThemeMode.allCases) { m in
                                Text(m.label).tag(m)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)

                    // 界面风格
                    HStack(spacing: 14) {
                        Image(systemName: "sparkles")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        Text("界面风格").font(.system(size: 15))
                        Spacer()
                        Picker("", selection: $prefs.uiStyle) {
                            ForEach(AppUIStyle.allCases) { s in
                                Text(s.label).tag(s)
                            }
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 170)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)

                    // 布局
                    HStack(spacing: 14) {
                        Image(systemName: "square.grid.2x2.fill")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("布局").font(.system(size: 15))
                            Text("主页每行 \(prefs.homeColumns) 个工具")
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Picker("", selection: $prefs.homeColumns) {
                            Text("2列").tag(2)
                            Text("3列").tag(3)
                            Text("4列").tag(4)
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 150)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)

                    // 网络代理
                    Button(action: onOpenProxy) {
                        HStack(spacing: 14) {
                            Image(systemName: "network")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("网络代理").font(.system(size: 15)).foregroundColor(.primary)
                                Text(ProxyManager.shared.statusMessage)
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 15)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                    }
                    .buttonStyle(.plain)

                    // 检查更新（与其余卡片完全一致的排版与对齐）
                    Button {
                        guard !isCheckingUpdate else { return }
                        isCheckingUpdate = true
                        Task {
                            let res = await AppUpdateChecker.checkLatestRelease(channel: selectedChannel)
                            isCheckingUpdate = false
                            switch res {
                            case .updateAvailable:
                                updateResult = res
                            case .upToDate(let c):
                                statusAlert = "当前版本 v\(c) 已是最新。"
                            case .error(let m):
                                statusAlert = m
                            }
                        }
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: "arrow.down.circle.fill")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("检查更新").font(.system(size: 15)).foregroundColor(.primary)
                                Text(isCheckingUpdate ? "正在检查更新..." : "当前版本 v\(AppUpdateChecker.currentVersionName)")
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Menu {
                                ForEach(UpdateChannel.allCases) { ch in
                                    Button {
                                        selectedChannel = ch
                                        AppUpdateChecker.setSelectedChannel(ch)
                                    } label: {
                                        if ch == selectedChannel {
                                            Label(ch.label, systemImage: "checkmark")
                                        } else {
                                            Text(ch.label)
                                        }
                                    }
                                }
                            } label: {
                                Text("\(selectedChannel.label) ▾")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundColor(.accentColor)
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 5)
                                    .background(Color.accentColor.opacity(0.12))
                                    .cornerRadius(10)
                            }
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 15)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                    }
                    .buttonStyle(.plain)

                    // 关于
                    Button(action: onOpenAbout) {
                        HStack(spacing: 14) {
                            Image(systemName: "info.circle.fill")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("关于").font(.system(size: 15)).foregroundColor(.primary)
                                Text("开源地址、运行日志与捐赠支持")
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 15)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .sheet(item: $updateResult) { res in
            if case let .updateAvailable(cur, lat, name, notes, dl) = res {
                UpdateSheet(current: cur, latest: lat, name: name, notes: notes, downloadUrl: dl)
            }
        }
        .alert(statusAlert ?? "", isPresented: Binding(get: { statusAlert != nil }, set: { if !$0 { statusAlert = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }
}
