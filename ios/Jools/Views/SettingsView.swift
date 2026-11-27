import SwiftUI

struct SettingsView: View {
    @ObservedObject var prefs: AppPreferences
    @ObservedObject private var bookmarkManager = BookmarkManager.shared
    let onOpenProxy: () -> Void
    let onOpenAbout: () -> Void

    @State private var selectedChannel: UpdateChannel = AppUpdateChecker.getSelectedChannel()
    @State private var isCheckingUpdate = false
    @State private var updateResult: UpdateCheckResult?
    @State private var statusAlert: String?
    @State private var showHomeUrlEditor = false
    @State private var editingHomeUrl = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("设置")
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .padding(.horizontal, 4)
                    .padding(.top, 6)

                VStack(spacing: 12) {
                    // 主题
                    HStack(spacing: 14) {
                        Image(systemName: "paintpalette.fill")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("主题").font(.system(size: 15, weight: .medium))
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
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)

                    // 界面风格
                    HStack(spacing: 14) {
                        Image(systemName: "sparkles")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        Text("界面风格").font(.system(size: 15, weight: .medium))
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
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)

                    // 布局（支持 1-4 列，对齐安卓）
                    HStack(spacing: 14) {
                        Image(systemName: "square.grid.2x2.fill")
                            .foregroundColor(.accentColor)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("布局").font(.system(size: 15, weight: .medium))
                            Text("主页每行 \(prefs.homeColumns) 个工具")
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Picker("", selection: $prefs.homeColumns) {
                            Text("1列").tag(1)
                            Text("2列").tag(2)
                            Text("3列").tag(3)
                            Text("4列").tag(4)
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 184)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)

                    // 浏览器默认主页
                    Button {
                        editingHomeUrl = bookmarkManager.homeUrl
                        showHomeUrlEditor = true
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: "safari.fill")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("浏览器默认主页").font(.system(size: 15, weight: .medium)).foregroundColor(.primary)
                                Text(bookmarkManager.homeUrl)
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 15)
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                    }
                    .buttonStyle(JoolsScaleButtonStyle())

                    // 网络代理
                    Button(action: onOpenProxy) {
                        HStack(spacing: 14) {
                            Image(systemName: "network")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("网络代理").font(.system(size: 15, weight: .medium)).foregroundColor(.primary)
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
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                    }
                    .buttonStyle(JoolsScaleButtonStyle())

                    // 检查更新
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
                                Text("检查更新").font(.system(size: 15, weight: .medium)).foregroundColor(.primary)
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
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                    }
                    .buttonStyle(JoolsScaleButtonStyle())

                    // 关于
                    Button(action: onOpenAbout) {
                        HStack(spacing: 14) {
                            Image(systemName: "info.circle.fill")
                                .foregroundColor(.accentColor)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("关于").font(.system(size: 15, weight: .medium)).foregroundColor(.primary)
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
                        .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 20)
                    }
                    .buttonStyle(JoolsScaleButtonStyle())
                }
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .sheet(item: $updateResult) { res in
            if case let .updateAvailable(cur, lat, name, notes, dl) = res {
                UpdateSheet(current: cur, latest: lat, name: name, notes: notes, downloadUrl: dl)
            }
        }
        .alert("设置默认主页", isPresented: $showHomeUrlEditor) {
            TextField("https://biglist03.xyz/", text: $editingHomeUrl)
                .autocapitalization(.none)
                .disableAutocorrection(true)
            Button("恢复默认") {
                bookmarkManager.setHomeUrl(BookmarkManager.defaultHomeUrl)
            }
            Button("保存") {
                if !editingHomeUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    bookmarkManager.setHomeUrl(editingHomeUrl)
                }
            }
            Button("取消", role: .cancel) {}
        }
        .alert(statusAlert ?? "", isPresented: Binding(get: { statusAlert != nil }, set: { if !$0 { statusAlert = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }
}
