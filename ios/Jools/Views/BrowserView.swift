import SwiftUI
import WebKit
import AVFoundation
import MediaPlayer

struct BrowserView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @StateObject private var adBlock = AdBlockManager()

    @State private var urlInput: String = ""
    @State private var pageTitle: String = ""
    @State private var isLoading: Bool = false
    @State private var progress: Double = 0.0
    @State private var canGoBack: Bool = false
    @State private var canGoForward: Bool = false

    @State private var showBookmarks: Bool = false
    @State private var showMenuSheet: Bool = false
    @State private var showDownloadsSheet: Bool = false
    @State private var showHistorySheet: Bool = false
    @State private var hasTypedInAddress: Bool = false
    @State private var isHistoryMultiSelect: Bool = false
    @State private var selectedHistoryIds: Set<String> = []
    @State private var showAdBlockDialog: Bool = false
    @State private var editingBookmark: BookmarkItem?
    @State private var showAddBookmarkDialog: Bool = false

    @State private var adBlockTab: Int = 0
    @State private var newSubName: String = ""
    @State private var newSubUrl: String = ""
    @State private var newCustomDomain: String = ""
    @State private var isRefreshingSubs: Bool = false

    @State private var detectedVideoUrl: String?
    @State private var detectedVideoPos: Double = 0
    @State private var detectedVideoTitle: String = ""
    @State private var detectedVideoPoster: String = ""
    @State private var activeFloatingVideoUrl: String?
    @State private var activeFloatingVideoPageUrl: String = ""
    @State private var userDismissedFloating: Bool = false

    @State private var webViewRef: WKWebView?
    @State private var toastText: String?

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // Address bar
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: urlInput.hasPrefix("https://") ? "lock.fill" : "globe")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                        TextField("输入网址或搜索", text: $urlInput)
                            .font(.system(size: 14))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .submitLabel(.go)
                            .onChange(of: urlInput) { val in
                                hasTypedInAddress = true
                                if !val.trimmingCharacters(in: .whitespaces).isEmpty {
                                    showBookmarks = false
                                }
                            }
                            .onSubmit {
                                hasTypedInAddress = false
                                navigateTo(urlInput)
                            }
                        if !urlInput.isEmpty {
                            Button {
                                urlInput = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Capsule())

                    Button {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
                            showBookmarks.toggle()
                        }
                    } label: {
                        Image(systemName: "bookmark")
                            .font(.system(size: 17))
                            .frame(width: 32, height: 32)
                    }

                    Button {
                        webViewRef?.reload()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 17))
                            .frame(width: 32, height: 32)
                    }

                    let isBookmarked = appState.bookmarks.contains {
                        $0.url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                            .caseInsensitiveCompare(urlInput.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) == .orderedSame
                    }
                    Button {
                        toggleCurrentBookmark()
                    } label: {
                        Image(systemName: isBookmarked ? "star.fill" : "star")
                            .font(.system(size: 17))
                            .foregroundColor(isBookmarked ? .accentColor : .primary)
                            .frame(width: 32, height: 32)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(.systemBackground))

                if isLoading && progress > 0 && progress < 1.0 {
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                        .frame(height: 2)
                }

                // Main WebView + Floating Play Icon + Bookmark Dropdown
                ZStack(alignment: .bottomTrailing) {
                    JoolsWebView(
                        initialUrl: appState.homeUrl,
                        adBlockEnabled: adBlock.enabled,
                        compiledRuleList: adBlock.compiledRuleList,
                        onWebViewCreated: { wv in self.webViewRef = wv },
                        onUrlChanged: { url in
                            self.urlInput = url
                            self.detectedVideoUrl = nil
                            self.detectedVideoPos = 0
                            self.detectedVideoTitle = ""
                            self.detectedVideoPoster = ""
                            self.userDismissedFloating = false
                        },
                        onTitleChanged: { self.pageTitle = $0 },
                        onProgressChanged: { p in
                            self.progress = p
                            self.isLoading = p < 1.0
                            if p >= 1.0 && !self.urlInput.isEmpty {
                                appState.recordHistory(title: self.pageTitle, url: self.urlInput)
                            }
                        },
                        onNavStateChanged: { back, fwd in
                            self.canGoBack = back
                            self.canGoForward = fwd
                        },
                        onVideoFound: { src, pos, title, poster in
                            self.detectedVideoUrl = src
                            self.detectedVideoPos = pos
                            if !title.isEmpty { self.detectedVideoTitle = title }
                            if !poster.isEmpty { self.detectedVideoPoster = poster }
                        },
                        onVideoPlayRequested: { src, pos, title, poster in
                            self.detectedVideoUrl = src
                            self.detectedVideoPos = pos
                            if !title.isEmpty { self.detectedVideoTitle = title }
                            if !poster.isEmpty { self.detectedVideoPoster = poster }
                            if !self.userDismissedFloating && self.activeFloatingVideoUrl == nil {
                                self.lockWebPagePause(true)
                                self.activeFloatingVideoPageUrl = self.urlInput
                                self.activeFloatingVideoUrl = src
                            }
                        }
                    )

                    if detectedVideoUrl != nil && !showBookmarks && activeFloatingVideoUrl == nil {
                        Button {
                            if let vUrl = detectedVideoUrl {
                                lockWebPagePause(true)
                                activeFloatingVideoPageUrl = urlInput
                                activeFloatingVideoUrl = vUrl
                            }
                        } label: {
                            Image(systemName: "play.circle.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                                .background(Color.accentColor)
                                .clipShape(Circle())
                                .shadow(radius: 6)
                        }
                        .padding(.trailing, 14)
                        .padding(.bottom, 14)
                    }

                    let matchedHistory = hasTypedInAddress ? appState.searchHistory(urlInput) : []
                    if !matchedHistory.isEmpty {
                        VStack(spacing: 0) {
                            List(matchedHistory) { item in
                                Button {
                                    hasTypedInAddress = false
                                    navigateTo(item.url)
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(systemName: "clock.arrow.circlepath")
                                            .foregroundColor(.secondary)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(item.title)
                                                .font(.system(size: 13, weight: .medium))
                                                .lineLimit(1)
                                            Text(item.url)
                                                .font(.system(size: 11))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                        }
                                    }
                                }
                            }
                            .listStyle(.plain)
                            .frame(maxHeight: 240)
                        }
                        .background(Color(.systemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .shadow(radius: 10)
                        .padding(12)
                        .frame(maxHeight: .infinity, alignment: .top)
                    } else if showBookmarks {
                        bookmarkOverlay
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Bottom toolbar
                HStack {
                    Spacer()
                    Button { webViewRef?.goBack() } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundColor(canGoBack ? .primary : .secondary.opacity(0.35))
                    }
                    .disabled(!canGoBack)
                    Spacer()
                    Button { webViewRef?.goForward() } label: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 19, weight: .medium))
                            .foregroundColor(canGoForward ? .primary : .secondary.opacity(0.35))
                    }
                    .disabled(!canGoForward)
                    Spacer()
                    Button { navigateTo(appState.homeUrl) } label: {
                        Image(systemName: "house")
                            .font(.system(size: 18, weight: .medium))
                    }
                    Spacer()
                    Button { showMenuSheet = true } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: 19, weight: .medium))
                            if appState.downloads.contains(where: { !$0.isCompleted && !$0.statusText.hasPrefix("失败") }) {
                                Circle()
                                    .fill(Color.accentColor)
                                    .frame(width: 7, height: 7)
                                    .offset(x: 4, y: -3)
                            }
                        }
                    }
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                    }
                    Spacer()
                }
                .frame(height: 48)
                .background(Color(.systemBackground))
            }

            if let videoUrl = activeFloatingVideoUrl {
                FloatingVideoPlayerOverlay(
                    videoUrl: videoUrl,
                    pageUrl: activeFloatingVideoPageUrl.isEmpty ? urlInput : activeFloatingVideoPageUrl,
                    pageTitle: detectedVideoTitle.isEmpty ? pageTitle : detectedVideoTitle,
                    posterUrl: detectedVideoPoster,
                    initialPositionSec: detectedVideoPos,
                    onDownloadVideo: { dlUrl in
                        appState.startDownload(urlString: dlUrl)
                        showToast("开始下载")
                    },
                    onClose: {
                        activeFloatingVideoUrl = nil
                        userDismissedFloating = true
                        lockWebPagePause(false)
                        webViewRef?.evaluateJavaScript("window.__joolsAutoHijack = false;", completionHandler: nil)
                    }
                )
            }

            if let toast = toastText {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.75))
                        .clipShape(Capsule())
                        .padding(.bottom, 64)
                }
                .transition(.opacity)
                .allowsHitTesting(false)
            }
        }
        .navigationBarBackButtonHidden(true)
        .task {
            if adBlock.subscriptions.contains(where: { $0.enabled && $0.lastUpdatedMs == 0 }) {
                _ = await adBlock.refreshAllEnabled()
            }
        }
        .sheet(isPresented: $showMenuSheet) {
            browserMenuSheet
                .presentationDetents([.height(230)])
        }
        .sheet(isPresented: $showDownloadsSheet) {
            downloadsSheet
        }
        .sheet(isPresented: $showHistorySheet) {
            historySheet
        }
        .sheet(isPresented: $showAdBlockDialog) {
            adBlockManagerSheet
        }
        .alert("添加书签", isPresented: $showAddBookmarkDialog) {
            BookmarkAlertContent(initialName: "", initialUrl: urlInput) { name, url in
                let finalUrl = normalizeUrl(url)
                appState.bookmarks.append(BookmarkItem(id: UUID().uuidString, name: name.isEmpty ? finalUrl : name, url: finalUrl))
            }
        }
        .sheet(item: $editingBookmark) { item in
            BookmarkEditSheet(item: item) { updated in
                if let idx = appState.bookmarks.firstIndex(where: { $0.id == updated.id }) {
                    appState.bookmarks[idx] = updated
                }
            } onDelete: {
                appState.bookmarks.removeAll { $0.id == item.id }
            }
        }
    }

    private var browserMenuSheet: some View {
        VStack(spacing: 14) {
            // Row 1 (4 columns)
            HStack(spacing: 8) {
                Button {
                    showMenuSheet = false
                    showDownloadsSheet = true
                } label: {
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(.secondarySystemBackground))
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: "arrow.down.circle")
                                    .font(.system(size: 20))
                            )
                        Text("下载管理")
                            .font(.system(size: 12))
                            .foregroundColor(.primary)
                    }
                    .frame(maxWidth: .infinity)
                }

                VStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(adBlock.enabled ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground))
                        .frame(width: 48, height: 48)
                        .overlay(
                            Image(systemName: "hand.raised.slash")
                                .font(.system(size: 20))
                                .foregroundColor(adBlock.enabled ? .accentColor : .secondary)
                        )
                    Text(adBlock.enabled ? "屏蔽广告·开" : "屏蔽广告·关")
                        .font(.system(size: 12))
                        .foregroundColor(adBlock.enabled ? .accentColor : .primary)
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .onTapGesture {
                    adBlock.enabled.toggle()
                    showToast(adBlock.enabled ? "已开启广告屏蔽" : "已关闭广告屏蔽")
                    clearWebCacheAndReload(removeCss: !adBlock.enabled)
                }
                .onLongPressGesture {
                    showMenuSheet = false
                    showAdBlockDialog = true
                }

                Button {
                    showMenuSheet = false
                    isHistoryMultiSelect = false
                    selectedHistoryIds = []
                    showHistorySheet = true
                } label: {
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(.secondarySystemBackground))
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: "clock.arrow.circlepath")
                                    .font(.system(size: 20))
                            )
                        Text("历史记录")
                            .font(.system(size: 12))
                            .foregroundColor(.primary)
                    }
                    .frame(maxWidth: .infinity)
                }

                Spacer().frame(maxWidth: .infinity)
            }

            // Row 2 (4 reserved columns)
            HStack(spacing: 8) {
                Spacer().frame(maxWidth: .infinity)
                Spacer().frame(maxWidth: .infinity)
                Spacer().frame(maxWidth: .infinity)
                Spacer().frame(maxWidth: .infinity)
            }
            .frame(height: 68)
        }
        .padding(.horizontal, 16)
        .padding(.top, 18)
    }

    private var adBlockManagerSheet: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Picker("", selection: $adBlockTab) {
                    Text("规则订阅 (\(adBlock.subscriptions.count))").tag(0)
                    Text("自定义域名 (\(adBlock.customDomains.count))").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                if adBlockTab == 0 {
                    VStack(spacing: 8) {
                        TextField("订阅名称（可选）", text: $newSubName)
                            .textFieldStyle(.roundedBorder)
                        HStack {
                            TextField("订阅链接 https://...", text: $newSubUrl)
                                .textFieldStyle(.roundedBorder)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            Button("添加") {
                                adBlock.addSubscription(name: newSubName, url: newSubUrl)
                                newSubName = ""
                                newSubUrl = ""
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                    .padding(.horizontal)

                    List {
                        ForEach(adBlock.subscriptions) { sub in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(sub.name)
                                        .font(.system(size: 14, weight: .semibold))
                                    Text("\(sub.ruleCount) 条规则")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Button {
                                    Task {
                                        isRefreshingSubs = true
                                        let ok = await adBlock.refreshSubscription(id: sub.id)
                                        isRefreshingSubs = false
                                        showToast(ok ? "更新成功" : "更新失败")
                                    }
                                } label: {
                                    Image(systemName: "arrow.clockwise")
                                }
                                .buttonStyle(.borderless)

                                Toggle("", isOn: Binding(
                                    get: { sub.enabled },
                                    set: { adBlock.toggleSubscription(id: sub.id, enabled: $0) }
                                ))
                                .labelsHidden()
                            }
                            .swipeActions(edge: .trailing) {
                                if !sub.isBuiltIn {
                                    Button("删除", role: .destructive) {
                                        adBlock.removeSubscription(id: sub.id)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    HStack {
                        TextField("输入屏蔽域名，如 ads.example.com", text: $newCustomDomain)
                            .textFieldStyle(.roundedBorder)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("添加") {
                            adBlock.addCustomDomain(newCustomDomain)
                            newCustomDomain = ""
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(.horizontal)

                    List {
                        ForEach(adBlock.customDomains, id: \.self) { domain in
                            Text(domain)
                        }
                        .onDelete { idxs in
                            for idx in idxs {
                                adBlock.removeCustomDomain(adBlock.customDomains[idx])
                            }
                        }
                    }
                }
            }
            .navigationTitle("广告屏蔽订阅与规则")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isRefreshingSubs ? "更新中..." : "全部更新") {
                        Task {
                            isRefreshingSubs = true
                            let count = await adBlock.refreshAllEnabled()
                            isRefreshingSubs = false
                            showToast("已更新 \(count) 个订阅")
                        }
                    }
                    .disabled(isRefreshingSubs)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        showAdBlockDialog = false
                        if adBlock.enabled {
                            clearWebCacheAndReload(removeCss: false)
                        }
                    }
                }
            }
        }
    }

    private var downloadsSheet: some View {
        NavigationStack {
            List {
                if appState.downloads.isEmpty {
                    Text("暂无下载任务")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(appState.downloads) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.fileName)
                                .font(.system(size: 14, weight: .semibold))
                                .lineLimit(1)
                            Text(item.statusText)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("下载管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("清空") { appState.downloads.removeAll() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("关闭") { showDownloadsSheet = false }
                }
            }
        }
    }

    private var historySheet: some View {
        NavigationStack {
            List {
                if appState.historyItems.isEmpty {
                    Text("暂无历史记录").foregroundColor(.secondary)
                } else {
                    ForEach(appState.historyItems) { item in
                        let isSelected = selectedHistoryIds.contains(item.id)
                        HStack(spacing: 10) {
                            if isHistoryMultiSelect {
                                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(isSelected ? .accentColor : .secondary)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.title)
                                    .font(.system(size: 14, weight: .medium))
                                    .lineLimit(1)
                                Text(item.url)
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            if !isHistoryMultiSelect {
                                Button {
                                    appState.deleteHistory(id: item.id)
                                } label: {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if isHistoryMultiSelect {
                                if isSelected { selectedHistoryIds.remove(item.id) }
                                else { selectedHistoryIds.insert(item.id) }
                            } else {
                                showHistorySheet = false
                                navigateTo(item.url)
                            }
                        }
                        .onLongPressGesture {
                            isHistoryMultiSelect = true
                            selectedHistoryIds.insert(item.id)
                        }
                    }
                }
            }
            .navigationTitle(isHistoryMultiSelect ? "已选 \(selectedHistoryIds.count) 条" : "历史记录 (14天)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isHistoryMultiSelect ? "取消多选" : "关闭") {
                        if isHistoryMultiSelect {
                            isHistoryMultiSelect = false
                            selectedHistoryIds = []
                        } else {
                            showHistorySheet = false
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isHistoryMultiSelect {
                        Button("删除所选", role: .destructive) {
                            appState.deleteHistory(ids: selectedHistoryIds)
                            isHistoryMultiSelect = false
                            selectedHistoryIds = []
                        }
                        .disabled(selectedHistoryIds.isEmpty)
                    } else if !appState.historyItems.isEmpty {
                        Button("全部删除", role: .destructive) {
                            appState.clearHistory()
                        }
                    }
                }
            }
        }
    }

    private var bookmarkOverlay: some View {
        VStack(spacing: 0) {
            List {
                ForEach(appState.bookmarks) { bm in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(bm.name)
                                .font(.system(size: 14, weight: .medium))
                            Text(bm.url)
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        if appState.homeUrl == bm.url {
                            Image(systemName: "house.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.accentColor)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        showBookmarks = false
                        navigateTo(bm.url)
                    }
                    .contextMenu {
                        Button("设为主页") {
                            appState.homeUrl = bm.url
                            showToast("已设为浏览器主页")
                        }
                        Button("编辑") { editingBookmark = bm }
                        Button("删除", role: .destructive) {
                            appState.bookmarks.removeAll { $0.id == bm.id }
                        }
                    }
                }
                .onMove { from, to in
                    appState.bookmarks.move(fromOffsets: from, toOffset: to)
                }
            }
            .listStyle(.plain)
            .frame(maxHeight: 340)

            HStack {
                Button {
                    showAddBookmarkDialog = true
                } label: {
                    Label("添加书签", systemImage: "plus")
                        .font(.system(size: 14, weight: .medium))
                }
                Spacer()
                Button("收起") { showBookmarks = false }
                    .font(.system(size: 14))
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
        }
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(radius: 12)
        .padding(12)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private func navigateTo(_ raw: String) {
        let target = normalizeUrl(raw)
        urlInput = target
        showBookmarks = false
        activeFloatingVideoUrl = nil
        lockWebPagePause(false)
        if let url = URL(string: target) {
            webViewRef?.load(URLRequest(url: url))
        }
    }

    private func normalizeUrl(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return appState.homeUrl }
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") { return trimmed }
        if trimmed.contains(".") && !trimmed.contains(" ") { return "https://\(trimmed)" }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        return "https://www.bing.com/search?q=\(encoded)"
    }

    private func toggleCurrentBookmark() {
        let norm = urlInput.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if let idx = appState.bookmarks.firstIndex(where: {
            $0.url.trimmingCharacters(in: CharacterSet(charactersIn: "/")).caseInsensitiveCompare(norm) == .orderedSame
        }) {
            appState.bookmarks.remove(at: idx)
            showToast("已取消收藏")
        } else if !urlInput.isEmpty {
            let title = pageTitle.isEmpty ? urlInput : pageTitle
            appState.bookmarks.append(BookmarkItem(id: UUID().uuidString, name: title, url: urlInput))
            showToast("已收藏到书签")
        }
    }

    private func lockWebPagePause(_ locked: Bool) {
        let js = locked
            ? "window.__joolsLockPause=true;document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(e){}});"
            : "window.__joolsLockPause=false;"
        webViewRef?.evaluateJavaScript(js, completionHandler: nil)
    }

    private func clearWebCacheAndReload(removeCss: Bool) {
        let types = Set([WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])
        WKWebsiteDataStore.default().removeData(ofTypes: types, modifiedSince: .distantPast) {
            if removeCss {
                webViewRef?.evaluateJavaScript("document.getElementById('jools-adblock-style')?.remove();", completionHandler: nil)
            }
            webViewRef?.reload()
        }
    }

    private func showToast(_ text: String) {
        toastText = text
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toastText == text { toastText = nil }
        }
    }
}

private struct JoolsWebView: UIViewRepresentable {
    let initialUrl: String
    let adBlockEnabled: Bool
    let compiledRuleList: WKContentRuleList?
    let onWebViewCreated: (WKWebView) -> Void
    let onUrlChanged: (String) -> Void
    let onTitleChanged: (String) -> Void
    let onProgressChanged: (Double) -> Void
    let onNavStateChanged: (Bool, Bool) -> Void
    let onVideoFound: (String, Double, String, String) -> Void
    let onVideoPlayRequested: (String, Double, String, String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.add(context.coordinator, name: "joolsVideoBridge")

        let detectorScript = WKUserScript(source: Self.videoDetectorJS, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        config.userContentController.addUserScript(detectorScript)

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = context.coordinator
        wv.allowsBackForwardNavigationGestures = true
        wv.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

        context.coordinator.observe(wv)
        onWebViewCreated(wv)
        if let url = URL(string: initialUrl) {
            wv.load(URLRequest(url: url))
        }
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        let ucc = uiView.configuration.userContentController
        ucc.removeAllContentRuleLists()
        if adBlockEnabled, let list = compiledRuleList {
            ucc.add(list)
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        let parent: JoolsWebView
        private var progressObs: NSKeyValueObservation?
        private var titleObs: NSKeyValueObservation?
        private var urlObs: NSKeyValueObservation?

        init(_ parent: JoolsWebView) {
            self.parent = parent
        }

        func observe(_ wv: WKWebView) {
            progressObs = wv.observe(\.estimatedProgress, options: .new) { [weak self] view, _ in
                DispatchQueue.main.async {
                    self?.parent.onProgressChanged(view.estimatedProgress)
                }
            }
            titleObs = wv.observe(\.title, options: .new) { [weak self] view, _ in
                DispatchQueue.main.async {
                    self?.parent.onTitleChanged(view.title ?? "")
                }
            }
            urlObs = wv.observe(\.url, options: .new) { [weak self] view, _ in
                DispatchQueue.main.async {
                    if let u = view.url?.absoluteString {
                        self?.parent.onUrlChanged(u)
                    }
                    self?.parent.onNavStateChanged(view.canGoBack, view.canGoForward)
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.onNavStateChanged(webView.canGoBack, webView.canGoForward)
            if parent.adBlockEnabled {
                webView.evaluateJavaScript(Self.cosmeticJS, completionHandler: nil)
            }
            webView.evaluateJavaScript(JoolsWebView.videoDetectorJS, completionHandler: nil)
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let scheme = navigationAction.request.url?.scheme?.lowercased() else {
                decisionHandler(.cancel)
                return
            }
            decisionHandler((scheme == "http" || scheme == "https") ? .allow : .cancel)
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let dict = message.body as? [String: Any],
                  let type = dict["type"] as? String,
                  let src = dict["src"] as? String else { return }
            let pos = dict["pos"] as? Double ?? 0
            let title = dict["title"] as? String ?? ""
            let poster = dict["poster"] as? String ?? ""
            DispatchQueue.main.async {
                if type == "play" {
                    self.parent.onVideoPlayRequested(src, pos, title, poster)
                } else {
                    self.parent.onVideoFound(src, pos, title, poster)
                }
            }
        }

        private static let cosmeticJS = """
            (function(){
              var id = 'jools-adblock-style';
              if (!document.getElementById(id)) {
                var s = document.createElement('style');
                s.id = id;
                s.textContent = 'ins.adsbygoogle, [id*="google_ads_iframe"], [class*="ad-banner"], [class*="floating-ad"], [class*="popup-ad"], iframe[src*="doubleclick"], iframe[src*="exoclick"] { display: none !important; }';
                (document.head || document.documentElement).appendChild(s);
              }
            })();
        """
    }

    static let videoDetectorJS = """
        (function() {
          function getSrc(v) {
            var s = v.currentSrc || v.src || '';
            if (!s) {
              var el = v.querySelector('source');
              if (el) s = el.src || '';
            }
            return s;
          }
          function absUrl(u) {
            if (!u) return '';
            try { return new URL(u, location.href).href; } catch(e) { return u; }
          }
          function getTitle(v) {
            var og = document.querySelector('meta[property="og:title"]');
            if (og && og.content) return og.content.trim();
            return (v && v.title) || document.title || '';
          }
          function getPoster(v) {
            if (v && v.poster) return absUrl(v.poster);
            var og = document.querySelector('meta[property="og:image"]');
            if (og && og.content) return absUrl(og.content);
            return '';
          }
          function postMsg(type, v, src) {
            window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.joolsVideoBridge &&
              window.webkit.messageHandlers.joolsVideoBridge.postMessage({
                type: type, src: src, pos: v.currentTime || 0, title: getTitle(v), poster: getPoster(v)
              });
          }
          function scan() {
            var vids = document.querySelectorAll('video');
            for (var i = 0; i < vids.length; i++) {
              var src = getSrc(vids[i]);
              if (src && src.indexOf('http') === 0) { postMsg('found', vids[i], src); return; }
            }
          }
          if (!window.__joolsVideoHooked) {
            window.__joolsVideoHooked = true;
            window.__joolsAutoHijack = true;
            window.__joolsLockPause = false;
            var origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
              if (this.tagName === 'VIDEO') {
                if (window.__joolsLockPause) { this.pause(); return Promise.resolve(); }
                var src = getSrc(this);
                if (src && src.indexOf('http') === 0 && window.__joolsAutoHijack !== false) {
                  this.pause();
                  postMsg('play', this, src);
                  return Promise.resolve();
                }
              }
              return origPlay.apply(this, arguments);
            };
            document.addEventListener('play', function(e) {
              var v = e.target;
              if (!v || v.tagName !== 'VIDEO') return;
              if (window.__joolsLockPause) { v.pause(); return; }
              var src = getSrc(v);
              if (src && src.indexOf('http') === 0 && window.__joolsAutoHijack !== false) {
                v.pause();
                postMsg('play', v, src);
              }
            }, true);
          }
          scan();
        })();
    """
}

private struct FloatingVideoPlayerOverlay: View {
    let videoUrl: String
    let pageUrl: String
    let pageTitle: String
    let posterUrl: String
    let initialPositionSec: Double
    let onDownloadVideo: (String) -> Void
    let onClose: () -> Void

    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = true
    @State private var isFullscreen: Bool = false
    @State private var isMinimized: Bool = false
    @State private var minimizedOnLeft: Bool = false
    @State private var minimizedY: CGFloat = 260
    @State private var controlsVisible: Bool = true
    @State private var isSeeking: Bool = false
    @State private var interactionTick: Int = 0
    @State private var currentTimeSec: Double = 0
    @State private var durationSec: Double = 0
    @State private var playbackRate: Float = 1.0

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = CGSize(width: 0, height: 80)
    @State private var lastOffset: CGSize = CGSize(width: 0, height: 80)

    private let speeds: [Float] = [0.5, 0.75, 1.0, 1.28, 1.5, 2.0]

    var body: some View {
        GeometryReader { geo in
            let screenW = geo.size.width
            let screenH = geo.size.height
            let edgeMargin: CGFloat = 8.0
            let baseW = screenW * 0.86
            let maxRestW = max(baseW, screenW - edgeMargin * 2)
            let maxRestScale = max(1.0, maxRestW / baseW)

            if isMinimized && !isFullscreen {
                let snapX = minimizedOnLeft ? 11.0 : screenW - 11.0
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(white: 0.25).opacity(0.9))
                    .frame(width: 22, height: 68)
                    .overlay(
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color.white.opacity(0.9))
                            .frame(width: 3.5, height: 28)
                    )
                    .position(x: snapX, y: min(max(minimizedY, 80), screenH - 100))
                    .gesture(
                        DragGesture()
                            .onChanged { val in
                                minimizedY = min(max(val.location.y, 80), screenH - 100)
                            }
                    )
                    .onTapGesture {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                            isMinimized = false
                            offset = CGSize(width: 0, height: min(max(minimizedY - (baseW * 9 / 16) / 2, 40), screenH - 260))
                            lastOffset = offset
                        }
                    }
            } else {
                let curW = isFullscreen ? screenW : baseW * scale
                let curH = isFullscreen ? screenH : curW * 9.0 / 16.0

                ZStack {
                    if let avPlayer = player {
                        AVPlayerContainerView(player: avPlayer)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }

                    if controlsVisible {
                        playerControlsOverlay
                    }
                }
                .frame(width: curW, height: curH)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: isFullscreen ? 0 : 14))
                .shadow(radius: isFullscreen ? 0 : 10)
                .position(
                    x: isFullscreen ? screenW / 2 : (screenW / 2 + offset.width),
                    y: isFullscreen ? screenH / 2 : (curH / 2 + offset.height)
                )
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) { controlsVisible.toggle() }
                }
                .gesture(
                    SimultaneousGesture(
                        DragGesture()
                            .onChanged { val in
                                guard !isFullscreen else { return }
                                offset = CGSize(
                                    width: lastOffset.width + val.translation.width,
                                    height: lastOffset.height + val.translation.height
                                )
                            }
                            .onEnded { _ in
                                guard !isFullscreen else { return }
                                let centerX = screenW / 2 + offset.width
                                let leftEdge = centerX - curW / 2
                                let rightEdge = centerX + curW / 2
                                let twoThirds = curW * (2.0 / 3.0)
                                if -leftEdge > twoThirds || (rightEdge - screenW) > twoThirds {
                                    minimizedOnLeft = -leftEdge > twoThirds
                                    minimizedY = curH / 2 + offset.height
                                    withAnimation(.spring()) { isMinimized = true }
                                } else {
                                    let maxOffX = max(0, (screenW - curW) / 2 - edgeMargin)
                                    let clampedX = min(max(offset.width, -maxOffX), maxOffX)
                                    let clampedY = min(max(offset.height, 28), max(28, screenH - curH - 56))
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.72)) {
                                        offset = CGSize(width: clampedX, height: clampedY)
                                        lastOffset = offset
                                    }
                                }
                            },
                        MagnificationGesture()
                            .onChanged { val in
                                guard !isFullscreen else { return }
                                scale = min(max(lastScale * val, 0.25), maxRestScale * 2.2)
                            }
                            .onEnded { _ in
                                guard !isFullscreen else { return }
                                let clamped = min(max(scale, 0.25), maxRestScale)
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                                    scale = clamped
                                    lastScale = clamped
                                }
                            }
                    )
                )
            }
        }
        .onAppear { startPlayback() }
        .onDisappear { stopPlayback() }
        .task(id: "\(controlsVisible)_\(isPlaying)_\(isSeeking)_\(interactionTick)") {
            if controlsVisible && isPlaying && !isSeeking {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                if !Task.isCancelled && !isSeeking {
                    withAnimation(.easeInOut(duration: 0.2)) { controlsVisible = false }
                }
            }
        }
    }

    private var playerControlsOverlay: some View {
        ZStack {
            VStack {
                HStack(spacing: 6) {
                    Button {
                        stopPlayback()
                        onClose()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                    }
                    Text(pageTitle.isEmpty ? "视频播放" : pageTitle)
                        .font(.system(size: isFullscreen ? 14 : 11, weight: .medium))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Spacer()
                    Button {
                        let idx = (speeds.firstIndex(of: playbackRate) ?? 2) + 1
                        playbackRate = speeds[idx % speeds.count]
                        player?.rate = isPlaying ? playbackRate : 0
                    } label: {
                        Text(playbackRate == 1.0 ? "1x" : "\(String(format: "%g", playbackRate))x")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.2))
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 6)
                .background(LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom))

                Spacer()

                HStack(spacing: 8) {
                    Text("\(formatSec(currentTimeSec))/\(formatSec(durationSec))")
                        .font(.system(size: isFullscreen ? 12 : 10))
                        .foregroundColor(.white)

                    // Slim Expressive progress bar
                    GeometryReader { barGeo in
                        let ratio = durationSec > 0 ? min(max(currentTimeSec / durationSec, 0), 1) : 0
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.3))
                                .frame(height: 2.5)
                            Capsule()
                                .fill(Color.white)
                                .frame(width: barGeo.size.width * ratio, height: 2.5)
                            Circle()
                                .fill(Color.white)
                                .frame(width: 9, height: 9)
                                .offset(x: max(0, barGeo.size.width * ratio - 4.5))
                        }
                        .frame(height: 24)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { val in
                                    isSeeking = true
                                    interactionTick += 1
                                    let r = min(max(val.location.x / max(1, barGeo.size.width), 0), 1)
                                    let target = r * durationSec
                                    currentTimeSec = target
                                    player?.seek(to: CMTime(seconds: target, preferredTimescale: 600))
                                }
                                .onEnded { _ in
                                    isSeeking = false
                                    interactionTick += 1
                                }
                        )
                    }
                    .frame(height: 24)

                    Button {
                        withAnimation(.spring()) { isFullscreen.toggle() }
                    } label: {
                        Image(systemName: isFullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 6)
                .background(LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom))
            }

            Button {
                guard let p = player else { return }
                if isPlaying {
                    p.pause()
                    isPlaying = false
                } else {
                    p.play()
                    p.rate = playbackRate
                    isPlaying = true
                }
                updateNowPlaying()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: isFullscreen ? 26 : 20))
                    .foregroundColor(.white)
                    .frame(width: isFullscreen ? 54 : 40, height: isFullscreen ? 54 : 40)
                    .background(Color.black.opacity(0.5))
                    .clipShape(Circle())
            }

            HStack {
                Spacer()
                Button { onDownloadVideo(videoUrl) } label: {
                    Image(systemName: "arrow.down.to.line")
                        .font(.system(size: isFullscreen ? 18 : 14))
                        .foregroundColor(.white)
                        .frame(width: isFullscreen ? 40 : 32, height: isFullscreen ? 40 : 32)
                        .background(Color.black.opacity(0.5))
                        .clipShape(Circle())
                }
                .padding(.trailing, isFullscreen ? 16 : 8)
            }
        }
    }

    private func startPlayback() {
        guard let url = URL(string: videoUrl) else { return }
        let headers = ["Referer": pageUrl]
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let item = AVPlayerItem(asset: asset)
        let av = AVPlayer(playerItem: item)
        if initialPositionSec > 0 {
            av.seek(to: CMTime(seconds: initialPositionSec, preferredTimescale: 600))
        }
        av.play()
        self.player = av
        self.isPlaying = true

        av.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { time in
            self.currentTimeSec = time.seconds
            if let dur = av.currentItem?.duration.seconds, dur.isFinite, dur > 0 {
                self.durationSec = dur
            }
            self.updateNowPlaying()
        }
        setupRemoteCommands(av)
        loadArtworkAndSetNowPlaying()
    }

    private func stopPlayback() {
        player?.pause()
        player = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func setupRemoteCommands(_ av: AVPlayer) {
        let cc = MPRemoteCommandCenter.shared()
        cc.playCommand.addTarget { _ in av.play(); self.isPlaying = true; return .success }
        cc.pauseCommand.addTarget { _ in av.pause(); self.isPlaying = false; return .success }
        cc.changePlaybackPositionCommand.addTarget { event in
            if let posEvent = event as? MPChangePlaybackPositionCommandEvent {
                av.seek(to: CMTime(seconds: posEvent.positionTime, preferredTimescale: 600))
            }
            return .success
        }
    }

    private func updateNowPlaying() {
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPMediaItemPropertyTitle] = pageTitle.isEmpty ? "网页视频" : pageTitle
        info[MPMediaItemPropertyArtist] = "Jools 浏览器"
        info[MPMediaItemPropertyPlaybackDuration] = durationSec
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTimeSec
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? playbackRate : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func loadArtworkAndSetNowPlaying() {
        updateNowPlaying()
        guard let url = URL(string: posterUrl) else { return }
        Task {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = UIImage(data: data) {
                let artwork = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
                var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                info[MPMediaItemPropertyArtwork] = artwork
                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
        }
    }

    private func formatSec(_ s: Double) -> String {
        guard s.isFinite && s > 0 else { return "00:00" }
        let total = Int(s)
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}

private struct AVPlayerContainerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerUIView {
        let v = PlayerUIView()
        v.playerLayer.player = player
        v.playerLayer.videoGravity = .resizeAspect
        return v
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
        }
    }

    final class PlayerUIView: UIView {
        override static var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}

private struct BookmarkAlertContent: View {
    @State var name: String
    @State var url: String
    let onSave: (String, String) -> Void

    init(initialName: String, initialUrl: String, onSave: @escaping (String, String) -> Void) {
        _name = State(initialValue: initialName)
        _url = State(initialValue: initialUrl)
        self.onSave = onSave
    }

    var body: some View {
        TextField("名称", text: $name)
        TextField("网址", text: $url)
        Button("保存") { onSave(name, url) }
        Button("取消", role: .cancel) {}
    }
}

private struct BookmarkEditSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var item: BookmarkItem
    let onSave: (BookmarkItem) -> Void
    let onDelete: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("书签名称", text: $item.name)
                TextField("网址", text: $item.url)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Section {
                    Button("删除书签", role: .destructive) {
                        onDelete()
                        dismiss()
                    }
                }
            }
            .navigationTitle("编辑书签")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { onSave(item); dismiss() }
                }
            }
        }
    }
}
