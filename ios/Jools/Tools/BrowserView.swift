import SwiftUI
import UIKit
import WebKit

private let blankTabURL = "about:blank"

struct BrowserTabModel: Identifiable, Equatable {
    let id: String
    var title: String
    var url: String
}

struct HistoryModel: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var title: String
    var url: String
    var visitedAt: TimeInterval
}

final class BrowserWebCoordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    let tabId: String
    let onStateChange: (String, String?, String?, Bool, Bool, Bool) -> Void
    let onProgressChange: (String, Double) -> Void
    let onVideoDetected: (String, String) -> Void
    let onDownloadRequested: (String, String?, String) -> Void
    var progressObservation: NSKeyValueObservation?

    init(
        tabId: String,
        onStateChange: @escaping (String, String?, String?, Bool, Bool, Bool) -> Void,
        onProgressChange: @escaping (String, Double) -> Void,
        onVideoDetected: @escaping (String, String) -> Void,
        onDownloadRequested: @escaping (String, String?, String) -> Void
    ) {
        self.tabId = tabId
        self.onStateChange = onStateChange
        self.onProgressChange = onProgressChange
        self.onVideoDetected = onVideoDetected
        self.onDownloadRequested = onDownloadRequested
    }

    deinit {
        progressObservation?.invalidate()
    }

    func observeProgress(of webView: WKWebView) {
        progressObservation?.invalidate()
        progressObservation = webView.observe(\.estimatedProgress, options: [.new]) { [weak self] wv, _ in
            guard let self else { return }
            let val = wv.estimatedProgress
            DispatchQueue.main.async {
                self.onProgressChange(self.tabId, val)
            }
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, true, webView.canGoBack, webView.canGoForward)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, false, webView.canGoBack, webView.canGoForward)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, false, webView.canGoBack, webView.canGoForward)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, false, webView.canGoBack, webView.canGoForward)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        if let url = navigationAction.request.url {
            let ext = url.pathExtension.lowercased()
            let downloadExts = Set(["ipa", "zip", "rar", "7z", "p12", "mobileprovision", "apk", "dmg", "mp4", "m4a", "mp3", "pdf"])
            if (ext == "ipa") || (downloadExts.contains(ext) && navigationAction.navigationType == .linkActivated) {
                DispatchQueue.main.async {
                    self.onDownloadRequested(url.absoluteString, url.lastPathComponent, "")
                }
                decisionHandler(.cancel)
                return
            }
        }
        decisionHandler(.allow)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        let resp = navigationResponse.response
        let mime = resp.mimeType ?? ""
        let disp = (resp as? HTTPURLResponse)?.allHeaderFields["Content-Disposition"] as? String ?? ""
        let isAttachment = disp.lowercased().contains("attachment")
        let ext = resp.url?.pathExtension.lowercased() ?? ""
        if !navigationResponse.canShowMIMEType || isAttachment || ext == "ipa" {
            if let urlStr = resp.url?.absoluteString {
                let suggested = resp.suggestedFilename
                DispatchQueue.main.async {
                    self.onDownloadRequested(urlStr, suggested, mime)
                }
            }
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if navigationAction.targetFrame == nil {
            webView.load(navigationAction.request)
        }
        return nil
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "joolsVideo",
           let dict = message.body as? [String: Any],
           let src = dict["src"] as? String,
           src.hasPrefix("http") {
            let title = (dict["title"] as? String) ?? ""
            DispatchQueue.main.async {
                self.onVideoDetected(src, title)
            }
        }
    }
}

struct MultiTabWebViewHost: UIViewRepresentable {
    let tabs: [BrowserTabModel]
    let activeTabId: String
    let webViews: [String: WKWebView]
    let makeWebView: (String, String) -> WKWebView

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .systemBackground
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let cur = tabs.first(where: { $0.id == activeTabId }) else { return }
        let target = makeWebView(cur.id, cur.url)
        if target.superview !== uiView {
            target.frame = uiView.bounds
            target.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            uiView.addSubview(target)
        }
        for sub in uiView.subviews {
            sub.isHidden = (sub !== target)
        }
    }
}


private struct PendingBrowserDownload: Identifiable {
    let id = UUID().uuidString
    let urlStr: String
    let fileName: String
    let mimeType: String
}

struct BrowserView: View {
    @ObservedObject var prefs: AppPreferences
    @ObservedObject private var bookmarkManager = BookmarkManager.shared
    @ObservedObject private var proxyManager = ProxyManager.shared
    @ObservedObject private var downloadManager = BrowserDownloadManager.shared
    let initialUrl: String
    @Environment(\.dismiss) private var dismiss

    @State private var tabs: [BrowserTabModel] = []
    @State private var activeTabId: String = ""
    @State private var webViewMap: [String: WKWebView] = [:]
    @State private var coordinators: [String: BrowserWebCoordinator] = [:]

    @State private var urlInput: String = ""
    @State private var isEditingAddress = false
    @FocusState private var addressFocused: Bool

    @State private var canGoBack = false
    @State private var canGoForward = false
    @State private var isLoading = false
    @State private var estimatedProgress: Double = 0

    @State private var showTabsSheet = false
    @State private var showMenuSheet = false
    @State private var showLibrarySheet = false
    @State private var showDownloadsSheet = false
    @State private var pendingDownloadPrompt: PendingBrowserDownload?
    @State private var showNewDownloadAlert = false
    @State private var newDownloadUrlInput = ""
    @State private var shareDownloadURL: URL?
    @State private var librarySegment: Int = 0
    @State private var librarySearch: String = ""

    @State private var editingBookmark: BookmarkItem?
    @State private var showAddBookmark = false
    @State private var bookmarkNameInput = ""
    @State private var bookmarkUrlInput = ""

    @State private var history: [HistoryModel] = []
    @State private var adBlockEnabled: Bool = UserDefaults.standard.object(forKey: "adblock_enabled") == nil
        ? true
        : UserDefaults.standard.bool(forKey: "adblock_enabled")

    @State private var activeVideoUrl: String?
    @State private var activeVideoTitle: String = ""
    @State private var detectedVideoUrl: String?
    @State private var detectedVideoTitle: String = ""
    @State private var toastMsg: String?

    private let lastUrlKey = "last_visited_url"
    private let historyKey = "browser_history_json"

    var body: some View {
        let activeTab = tabs.first { $0.id == activeTabId }
        let currentUrl = activeTab?.url ?? blankTabURL
        let isBlank = currentUrl == blankTabURL

        ZStack {
            VStack(spacing: 0) {
                // Safari 顶部极简导航条 + 进度条
                topMinimalBar(activeTab: activeTab, isBlank: isBlank)

                // 主内容区：WebView 或 Safari 起始页
                ZStack(alignment: .bottom) {
                    MultiTabWebViewHost(
                        tabs: tabs,
                        activeTabId: activeTabId,
                        webViews: webViewMap,
                        makeWebView: { tid, start in getOrCreateWebView(tabId: tid, startUrl: start) }
                    )

                    if isBlank {
                        safariStartPage
                    }

                    // 地址输入实时联想浮层
                    if isEditingAddress && !urlInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        suggestionsOverlay
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Safari 底部悬浮胶囊地址栏 + 5 键工具栏
                safariBottomChrome(currentUrl: currentUrl, isBlank: isBlank)
            }

            if let vUrl = activeVideoUrl {
                BuiltInVideoPlayerView(
                    videoUrl: vUrl,
                    pageTitle: activeVideoTitle,
                    onClose: { activeVideoUrl = nil },
                    onDownload: { dl in
                        downloadManager.startDownload(urlStr: dl)
                        toastMsg = "已开始下载，可在下载管理查看进度"
                    }
                )
            }
        }
        .navigationBarHidden(true)
        .onAppear { setupInitialTab() }
        .onDisappear {
            saveLastVisited()
            webViewMap.values.forEach { $0.stopLoading() }
        }
        .sheet(isPresented: $showTabsSheet) { tabsGridSheet }
        .sheet(isPresented: $showMenuSheet) { menuSheet }
        .sheet(isPresented: $showLibrarySheet) { librarySheet }
        .sheet(isPresented: $showDownloadsSheet) { downloadsSheet }
        .alert(item: $pendingDownloadPrompt) { prompt in
            Alert(
                title: Text("下载文件"),
                message: Text("检测到安装包/文件：\(prompt.fileName)\n点击立即下载，.ipa 完成后将自动加入「IPA 管理 → 未签名」列表。"),
                primaryButton: .default(Text("立即下载")) {
                    downloadManager.startDownload(urlStr: prompt.urlStr, suggestedName: prompt.fileName, mimeType: prompt.mimeType)
                    showDownloadsSheet = true
                    toastMsg = "已开始下载，完成后将自动加入「IPA 管理 → 未签名」"
                },
                secondaryButton: .cancel(Text("取消"))
            )
        }
        .alert("新建下载", isPresented: $showNewDownloadAlert) {
            TextField("输入文件或 IPA 下载链接", text: $newDownloadUrlInput)
                .autocapitalization(.none)
                .disableAutocorrection(true)
            Button("开始下载") {
                let raw = newDownloadUrlInput.trimmingCharacters(in: .whitespacesAndNewlines)
                if !raw.isEmpty {
                    downloadManager.startDownload(urlStr: raw)
                    showDownloadsSheet = true
                }
            }
            Button("取消", role: .cancel) {}
        }
        .sheet(isPresented: $showAddBookmark) {
            bookmarkEditorSheet(isNew: true)
        }
        .sheet(item: $editingBookmark) { item in
            bookmarkEditorSheet(isNew: false, existing: item)
        }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }

    // MARK: - Top Minimal Chrome
    @ViewBuilder
    private func topMinimalBar(activeTab: BrowserTabModel?, isBlank: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button {
                    saveLastVisited()
                    dismiss()
                } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 13, weight: .semibold))
                        Text("工具箱")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .foregroundColor(.accentColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(Color.accentColor.opacity(0.10))
                    .clipShape(Capsule())
                }

                Spacer()

                Text(isBlank ? "起始页" : (activeTab?.title.isEmpty == false ? activeTab!.title : displayHost(from: activeTab?.url ?? "")))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                Spacer()

                HStack(spacing: 10) {
                    if let dUrl = detectedVideoUrl {
                        Button {
                            activeVideoTitle = detectedVideoTitle
                            activeVideoUrl = dUrl
                        } label: {
                            Image(systemName: "play.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.accentColor)
                        }
                    }

                    Button {
                        showDownloadsSheet = true
                    } label: {
                        let activeCount = downloadManager.items.filter { $0.status == .downloading }.count
                        Image(systemName: activeCount > 0 ? "arrow.down.circle.fill" : "arrow.down.circle")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(activeCount > 0 ? .accentColor : .primary)
                            .frame(width: 28, height: 28)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Circle())
                    }

                    Button {
                        openNewTab()
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.primary)
                            .frame(width: 28, height: 28)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Circle())
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(.ultraThinMaterial)

            if isLoading && estimatedProgress < 1.0 {
                GeometryReader { geo in
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: max(12, geo.size.width * estimatedProgress), height: 2.2)
                        .animation(.easeOut(duration: 0.2), value: estimatedProgress)
                }
                .frame(height: 2.2)
            } else {
                Divider().opacity(0.45)
            }
        }
    }

    // MARK: - Safari Start Page
    private var safariStartPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("起始页")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                    Text("Light Browser · Safari 风格浏览体验")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                .padding(.top, 12)

                // 个人收藏（4 列网格，默认含 7 个预设书签）
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("个人收藏")
                            .font(.system(size: 18, weight: .bold))
                        Spacer()
                        Button {
                            bookmarkNameInput = ""
                            bookmarkUrlInput = ""
                            showAddBookmark = true
                        } label: {
                            Label("添加", systemImage: "plus")
                                .font(.system(size: 13, weight: .medium))
                        }
                    }

                    let favCols = Array(repeating: GridItem(.flexible(), spacing: 14), count: 4)
                    LazyVGrid(columns: favCols, spacing: 16) {
                        ForEach(bookmarkManager.bookmarks) { item in
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                navigateTo(item.url)
                            } label: {
                                VStack(spacing: 7) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                                            .fill(favoriteGradient(for: item.name))
                                            .frame(width: 58, height: 58)
                                            .shadow(color: Color.black.opacity(0.08), radius: 5, x: 0, y: 3)
                                        Text(String(item.name.prefix(1)))
                                            .font(.system(size: 24, weight: .bold, design: .rounded))
                                            .foregroundColor(.white)
                                    }
                                    Text(item.name)
                                        .font(.system(size: 11.5, weight: .medium))
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.8)
                                }
                            }
                            .buttonStyle(JoolsScaleButtonStyle())
                            .contextMenu {
                                Button {
                                    openNewTab(url: item.url)
                                } label: {
                                    Label("在新标签页打开", systemImage: "plus.square.on.square")
                                }
                                Button {
                                    bookmarkManager.setHomeUrl(item.url)
                                    toastMsg = "已将「\(item.name)」设为默认主页"
                                } label: {
                                    Label("设为默认主页", systemImage: "house")
                                }
                                Button {
                                    bookmarkNameInput = item.name
                                    bookmarkUrlInput = item.url
                                    editingBookmark = item
                                } label: {
                                    Label("编辑书签", systemImage: "pencil")
                                }
                                Button(role: .destructive) {
                                    bookmarkManager.deleteBookmark(id: item.id)
                                } label: {
                                    Label("删除", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .padding(16)
                .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)

                // 最近访问
                if !history.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("最近访问")
                                .font(.system(size: 18, weight: .bold))
                            Spacer()
                            Button("查看全部") {
                                librarySegment = 1
                                showLibrarySheet = true
                            }
                            .font(.system(size: 13))
                        }

                        VStack(spacing: 0) {
                            ForEach(Array(history.prefix(5).enumerated()), id: \.element.id) { idx, h in
                                Button {
                                    navigateTo(h.url)
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: "clock.arrow.circlepath")
                                            .font(.system(size: 15))
                                            .foregroundColor(.accentColor)
                                            .frame(width: 28, height: 28)
                                            .background(Color.accentColor.opacity(0.1))
                                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(h.title)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.primary)
                                                .lineLimit(1)
                                            Text(displayHost(from: h.url))
                                                .font(.system(size: 11.5))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                    }
                                    .padding(.vertical, 10)
                                }
                                .buttonStyle(.plain)
                                if idx < min(4, history.count - 1) {
                                    Divider()
                                }
                            }
                        }
                    }
                    .padding(16)
                    .joolsCard(uiStyle: prefs.uiStyle, cornerRadius: 22)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Color(uiColor: .systemGroupedBackground))
    }

    // MARK: - Safari Bottom Chrome (Floating Address Capsule + 5-Button Bar)
    @ViewBuilder
    private func safariBottomChrome(currentUrl: String, isBlank: Bool) -> some View {
        VStack(spacing: 10) {
            // 地址栏胶囊
            HStack(spacing: 8) {
                if isEditingAddress {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                    TextField("搜索或输入网站名称", text: $urlInput)
                        .focused($addressFocused)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                        .submitLabel(.go)
                        .font(.system(size: 15))
                        .onSubmit {
                            navigateTo(urlInput)
                            isEditingAddress = false
                            addressFocused = false
                        }

                    if !urlInput.isEmpty {
                        Button {
                            urlInput = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.secondary)
                        }
                    }

                    Button("取消") {
                        urlInput = isBlank ? "" : currentUrl
                        isEditingAddress = false
                        addressFocused = false
                    }
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.accentColor)
                } else {
                    Button {
                        urlInput = isBlank ? "" : currentUrl
                        isEditingAddress = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                            addressFocused = true
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: currentUrl.hasPrefix("https://") ? "lock.fill" : "magnifyingglass")
                                .font(.system(size: 12))
                                .foregroundColor(currentUrl.hasPrefix("https://") ? .secondary : .secondary)
                            Text(isBlank ? "搜索或输入网站名称" : displayHost(from: currentUrl))
                                .font(.system(size: 14.5, weight: .medium))
                                .foregroundColor(isBlank ? .secondary : .primary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)

                    if !isBlank {
                        Button {
                            if let wv = webViewMap[activeTabId] {
                                if isLoading { wv.stopLoading() } else { wv.reload() }
                            }
                        } label: {
                            Image(systemName: isLoading ? "xmark" : "arrow.clockwise")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                        }
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemBackground).opacity(0.9))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 2)
            .padding(.horizontal, 14)

            // 5 键 Safari 工具栏
            HStack {
                Button { webViewMap[activeTabId]?.goBack() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 32)
                }
                .disabled(!canGoBack)
                .opacity(canGoBack ? 1 : 0.35)

                Spacer()

                Button { webViewMap[activeTabId]?.goForward() } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 32)
                }
                .disabled(!canGoForward)
                .opacity(canGoForward ? 1 : 0.35)

                Spacer()

                Button {
                    librarySegment = 0
                    showLibrarySheet = true
                } label: {
                    Image(systemName: "book")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 32)
                }

                Spacer()

                Button {
                    showTabsSheet = true
                } label: {
                    ZStack {
                        Image(systemName: "square.on.square")
                            .font(.system(size: 18, weight: .medium))
                        Text("\(tabs.count)")
                            .font(.system(size: 9, weight: .bold))
                            .offset(x: -2, y: 2)
                    }
                    .frame(width: 44, height: 32)
                }

                Spacer()

                Button {
                    showMenuSheet = true
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 19, weight: .medium))
                        .frame(width: 44, height: 32)
                }
            }
            .foregroundColor(.accentColor)
            .padding(.horizontal, 18)
        }
        .padding(.top, 10)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Divider().opacity(0.45)
        }
    }

    // MARK: - Address Suggestions Overlay
    private var suggestionsOverlay: some View {
        let q = urlInput.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let matchedBookmarks = bookmarkManager.bookmarks.filter {
            $0.name.lowercased().contains(q) || $0.url.lowercased().contains(q)
        }.prefix(4)
        let matchedHistory = history.filter {
            $0.title.lowercased().contains(q) || $0.url.lowercased().contains(q)
        }.prefix(4)

        return Group {
            if !matchedBookmarks.isEmpty || !matchedHistory.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(matchedBookmarks)) { b in
                            Button {
                                navigateTo(b.url)
                                isEditingAddress = false
                                addressFocused = false
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(.orange)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(b.name).font(.system(size: 14, weight: .medium)).foregroundColor(.primary).lineLimit(1)
                                        Text(b.url).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                            }
                            Divider()
                        }
                        ForEach(Array(matchedHistory)) { h in
                            Button {
                                navigateTo(h.url)
                                isEditingAddress = false
                                addressFocused = false
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: "clock")
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(h.title).font(.system(size: 14, weight: .medium)).foregroundColor(.primary).lineLimit(1)
                                        Text(h.url).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                            }
                            Divider()
                        }
                    }
                }
                .frame(maxHeight: 240)
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .shadow(color: Color.black.opacity(0.14), radius: 12, x: 0, y: 4)
                .padding(.horizontal, 14)
                .padding(.bottom, 8)
            }
        }
    }

    // MARK: - Safari Grid Tabs Sheet
    private var tabsGridSheet: some View {
        NavigationStack {
            ScrollView {
                let gridCols = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
                LazyVGrid(columns: gridCols, spacing: 12) {
                    ForEach(tabs) { t in
                        let isActive = t.id == activeTabId
                        let isTabBlank = t.url == blankTabURL
                        ZStack(alignment: .topTrailing) {
                            Button {
                                activeTabId = t.id
                                urlInput = isTabBlank ? "" : t.url
                                canGoBack = webViewMap[t.id]?.canGoBack ?? false
                                canGoForward = webViewMap[t.id]?.canGoForward ?? false
                                showTabsSheet = false
                            } label: {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(spacing: 6) {
                                        Image(systemName: isTabBlank ? "safari" : "globe")
                                            .font(.system(size: 13))
                                            .foregroundColor(.accentColor)
                                        Text(t.title.isEmpty ? "新标签页" : t.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.primary)
                                            .lineLimit(1)
                                        Spacer()
                                    }
                                    .padding(.trailing, 22)

                                    Spacer()

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(isTabBlank ? "起始页" : displayHost(from: t.url))
                                            .font(.system(size: 11.5, weight: .medium))
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                        if !isTabBlank {
                                            Text(t.url)
                                                .font(.system(size: 10))
                                                .foregroundColor(.secondary.opacity(0.75))
                                                .lineLimit(2)
                                        }
                                    }
                                }
                                .padding(12)
                                .frame(height: 136)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                        .stroke(isActive ? Color.accentColor : Color.primary.opacity(0.08), lineWidth: isActive ? 2.2 : 0.8)
                                )
                            }
                            .buttonStyle(.plain)

                            Button {
                                closeTab(t.id)
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.secondary)
                                    .padding(8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("\(tabs.count) 个标签页")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        openNewTab()
                    } label: {
                        Label("新建标签页", systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { showTabsSheet = false }
                }
            }
        }
    }

    // MARK: - Safari Bookmarks & History Sheet
    private var librarySheet: some View {
        let q = librarySearch.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let filteredBookmarks = bookmarkManager.bookmarks.filter {
            q.isEmpty || $0.name.lowercased().contains(q) || $0.url.lowercased().contains(q)
        }
        let filteredHistory = history.filter {
            q.isEmpty || $0.title.lowercased().contains(q) || $0.url.lowercased().contains(q)
        }

        return NavigationStack {
            VStack(spacing: 0) {
                Picker("分类", selection: $librarySegment) {
                    Text("书签 (\(bookmarkManager.bookmarks.count))").tag(0)
                    Text("历史记录").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                if librarySegment == 0 {
                    List {
                        ForEach(filteredBookmarks) { b in
                            Button {
                                showLibrarySheet = false
                                navigateTo(b.url)
                            } label: {
                                HStack(spacing: 12) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            .fill(favoriteGradient(for: b.name))
                                            .frame(width: 34, height: 34)
                                        Text(String(b.name.prefix(1)))
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(b.name).font(.system(size: 15, weight: .medium)).foregroundColor(.primary)
                                        Text(b.url).font(.system(size: 11.5)).foregroundColor(.secondary).lineLimit(1)
                                    }
                                    Spacer()
                                    if bookmarkManager.homeUrl.lowercased() == b.url.lowercased() {
                                        Text("主页")
                                            .font(.system(size: 10.5, weight: .semibold))
                                            .foregroundColor(.accentColor)
                                            .padding(.horizontal, 7)
                                            .padding(.vertical, 3)
                                            .background(Color.accentColor.opacity(0.12))
                                            .clipShape(Capsule())
                                    }
                                }
                            }
                            .contextMenu {
                                Button {
                                    bookmarkNameInput = b.name
                                    bookmarkUrlInput = b.url
                                    editingBookmark = b
                                } label: {
                                    Label("编辑", systemImage: "pencil")
                                }
                                Button {
                                    bookmarkManager.setHomeUrl(b.url)
                                    toastMsg = "已设为默认主页"
                                } label: {
                                    Label("设为默认主页", systemImage: "house")
                                }
                                Button(role: .destructive) {
                                    bookmarkManager.deleteBookmark(id: b.id)
                                } label: {
                                    Label("删除", systemImage: "trash")
                                }
                            }
                        }
                        .onDelete { offsets in
                            let ids = offsets.map { filteredBookmarks[$0].id }
                            for id in ids { bookmarkManager.deleteBookmark(id: id) }
                        }
                        .onMove { source, destination in
                            if q.isEmpty {
                                bookmarkManager.moveBookmarks(from: source, to: destination)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                } else {
                    List {
                        if filteredHistory.isEmpty {
                            Text("暂无最近 14 天浏览记录")
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(filteredHistory) { h in
                                Button {
                                    showLibrarySheet = false
                                    navigateTo(h.url)
                                } label: {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(h.title).font(.system(size: 14.5, weight: .medium)).foregroundColor(.primary).lineLimit(1)
                                        HStack {
                                            Text(displayHost(from: h.url))
                                                .font(.system(size: 11.5))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                            Spacer()
                                            Text(formatHistoryDate(h.visitedAt))
                                                .font(.system(size: 11))
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                }
                            }
                            .onDelete { offsets in
                                let ids = Set(offsets.map { filteredHistory[$0].id })
                                history.removeAll { ids.contains($0.id) }
                                if let d = try? JSONEncoder().encode(history) {
                                    UserDefaults.standard.set(d, forKey: historyKey)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .searchable(text: $librarySearch, prompt: librarySegment == 0 ? "搜索书签" : "搜索历史记录")
            .navigationTitle(librarySegment == 0 ? "书签" : "历史记录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if librarySegment == 0 {
                        Button {
                            bookmarkNameInput = ""
                            bookmarkUrlInput = ""
                            showAddBookmark = true
                        } label: {
                            Image(systemName: "plus")
                        }
                    } else if !history.isEmpty {
                        Button("清空", role: .destructive) {
                            history.removeAll()
                            UserDefaults.standard.removeObject(forKey: historyKey)
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    HStack(spacing: 12) {
                        if librarySegment == 0 && q.isEmpty {
                            EditButton()
                        }
                        Button("完成") { showLibrarySheet = false }
                    }
                }
            }
        }
    }

    // MARK: - Menu Sheet
    private var menuSheet: some View {
        let activeTab = tabs.first { $0.id == activeTabId }
        let curUrl = activeTab?.url ?? blankTabURL
        let isBlank = curUrl == blankTabURL
        let bookmarked = !isBlank && bookmarkManager.isBookmarked(curUrl)

        return NavigationStack {
            List {
                Section("当前页面") {
                    Button {
                        showMenuSheet = false
                        navigateTo(bookmarkManager.homeUrl)
                    } label: {
                        Label("前往默认主页 (\(displayHost(from: bookmarkManager.homeUrl)))", systemImage: "house")
                    }

                    if !isBlank {
                        Button {
                            let added = bookmarkManager.toggleBookmark(
                                name: activeTab?.title ?? "",
                                url: curUrl
                            )
                            toastMsg = added ? "已添加到个人收藏" : "已从书签移除"
                            showMenuSheet = false
                        } label: {
                            Label(
                                bookmarked ? "从个人收藏移除" : "添加到个人收藏",
                                systemImage: bookmarked ? "star.slash" : "star"
                            )
                        }

                        Button {
                            bookmarkManager.setHomeUrl(curUrl)
                            toastMsg = "已设为默认主页"
                            showMenuSheet = false
                        } label: {
                            Label("设为默认主页", systemImage: "house.circle")
                        }

                        Button {
                            UIPasteboard.general.string = curUrl
                            toastMsg = "已复制当前网址"
                            showMenuSheet = false
                        } label: {
                            Label("拷贝网址", systemImage: "doc.on.doc")
                        }

                        Button {
                            if let u = URL(string: curUrl) { UIApplication.shared.open(u) }
                            showMenuSheet = false
                        } label: {
                            Label("在 Safari 中打开", systemImage: "safari")
                        }
                    }
                }

                Section("浏览增强") {
                    Button {
                        showMenuSheet = false
                        showDownloadsSheet = true
                    } label: {
                        HStack {
                            Label("下载管理", systemImage: "arrow.down.circle")
                            Spacer()
                            if !downloadManager.items.isEmpty {
                                Text("\(downloadManager.items.count)")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }

                    Toggle(isOn: $adBlockEnabled) {
                        Label("广告与弹窗拦截", systemImage: "hand.raised")
                    }
                    .onChange(of: adBlockEnabled) { v in
                        UserDefaults.standard.set(v, forKey: "adblock_enabled")
                    }

                    Toggle(isOn: $proxyManager.enabled) {
                        VStack(alignment: .leading, spacing: 2) {
                            Label("应用内代理", systemImage: "network")
                            Text(proxyManager.statusMessage)
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                        }
                    }
                }

                Section {
                    Button(role: .destructive) {
                        showMenuSheet = false
                        saveLastVisited()
                        dismiss()
                    } label: {
                        Label("退出浏览器返回工具箱", systemImage: "xmark.circle")
                    }
                }
            }
            .navigationTitle("浏览器选项")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { showMenuSheet = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Bookmark Editor Sheet
    private func bookmarkEditorSheet(isNew: Bool, existing: BookmarkItem? = nil) -> some View {
        NavigationStack {
            Form {
                Section("书签信息") {
                    TextField("名称（如：文尼导航）", text: $bookmarkNameInput)
                    TextField("网址（https://...）", text: $bookmarkUrlInput)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                }
            }
            .navigationTitle(isNew ? "添加书签" : "编辑书签")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        showAddBookmark = false
                        editingBookmark = nil
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        if let existing {
                            bookmarkManager.updateBookmark(
                                id: existing.id,
                                name: bookmarkNameInput,
                                url: bookmarkUrlInput
                            )
                            editingBookmark = nil
                        } else {
                            bookmarkManager.addBookmark(
                                name: bookmarkNameInput,
                                url: bookmarkUrlInput
                            )
                            showAddBookmark = false
                        }
                    }
                    .disabled(bookmarkUrlInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Tab & WebView Logic
    private func setupInitialTab() {
        loadHistory()
        if tabs.isEmpty {
            let saved = UserDefaults.standard.string(forKey: lastUrlKey) ?? ""
            let firstUrl: String = {
                if !initialUrl.trimmingCharacters(in: .whitespaces).isEmpty { return initialUrl }
                if saved.hasPrefix("http://") || saved.hasPrefix("https://") { return saved }
                return bookmarkManager.homeUrl
            }()
            let first = BrowserTabModel(id: UUID().uuidString, title: "加载中...", url: firstUrl)
            tabs = [first]
            activeTabId = first.id
            urlInput = firstUrl == blankTabURL ? "" : firstUrl
            if let u = URL(string: firstUrl), u.pathExtension.lowercased() == "ipa" {
                let fname = u.lastPathComponent.isEmpty ? "Jools.ipa" : u.lastPathComponent
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                    if pendingDownloadPrompt == nil {
                        pendingDownloadPrompt = PendingBrowserDownload(urlStr: firstUrl, fileName: fname, mimeType: "")
                    }
                }
            }
        }
    }

    private func getOrCreateWebView(tabId: String, startUrl: String) -> WKWebView {
        if let existing = webViewMap[tabId] { return existing }
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let js = """
        (function(){
          if(window.__joolsHooked) return; window.__joolsHooked=true;
          var origPlay = HTMLMediaElement.prototype.play;
          HTMLMediaElement.prototype.play = function(){
            try{
              var s = this.currentSrc || this.src;
              if(s && s.indexOf('http')===0){
                window.webkit.messageHandlers.joolsVideo.postMessage({src:s, title:document.title||''});
              }
            }catch(e){}
            return origPlay.apply(this, arguments);
          };
        })();
        """
        let script = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        config.userContentController.addUserScript(script)

        if adBlockEnabled {
            let adJs = """
            (function(){
              window.open = function(){ return null; };
              var s = document.createElement('style');
              s.innerHTML = '[id*="ad_"],[class*="ad-banner"],[class*="popup-ad"],iframe[src*="ads"]{display:none !important;}';
              document.documentElement.appendChild(s);
            })();
            """
            config.userContentController.addUserScript(
                WKUserScript(source: adJs, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
            )
        }

        let coord = BrowserWebCoordinator(
            tabId: tabId,
            onStateChange: { tid, u, t, loading, back, fwd in
                DispatchQueue.main.async {
                    if let idx = tabs.firstIndex(where: { $0.id == tid }) {
                        if let u, u != blankTabURL { tabs[idx].url = u }
                        if let t, !t.isEmpty { tabs[idx].title = t }
                    }
                    if tid == activeTabId {
                        isLoading = loading
                        canGoBack = back
                        canGoForward = fwd
                        if let u, u != blankTabURL {
                            if !isEditingAddress { urlInput = u }
                            UserDefaults.standard.set(u, forKey: lastUrlKey)
                            if !loading { recordHistory(title: t ?? u, url: u) }
                        }
                    }
                }
            },
            onProgressChange: { tid, progress in
                if tid == activeTabId {
                    estimatedProgress = progress
                }
            },
            onVideoDetected: { src, title in
                detectedVideoUrl = src
                detectedVideoTitle = title
                if activeVideoUrl == nil {
                    activeVideoTitle = title
                    activeVideoUrl = src
                }
            },
            onDownloadRequested: { urlStr, suggested, mime in
                let fallbackName = (URL(string: urlStr)?.lastPathComponent).flatMap { $0.isEmpty ? nil : $0 } ?? "download_file"
                let displayName = (suggested?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 } ?? fallbackName
                DispatchQueue.main.async {
                    pendingDownloadPrompt = PendingBrowserDownload(urlStr: urlStr, fileName: displayName, mimeType: mime)
                }
            }
        )
        coordinators[tabId] = coord
        config.userContentController.add(coord, name: "joolsVideo")

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = coord
        wv.uiDelegate = coord
        wv.allowsBackForwardNavigationGestures = true
        coord.observeProgress(of: wv)
        if startUrl != blankTabURL, let u = URL(string: startUrl) {
            wv.load(URLRequest(url: u))
        }
        DispatchQueue.main.async {
            webViewMap[tabId] = wv
        }
        return wv
    }

    private func navigateTo(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let target: String
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            target = trimmed
        } else if trimmed.contains(".") && !trimmed.contains(" ") {
            target = "https://\(trimmed)"
        } else {
            let q = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
            target = "https://www.bing.com/search?q=\(q)"
        }
        urlInput = target
        detectedVideoUrl = nil
        if let idx = tabs.firstIndex(where: { $0.id == activeTabId }) {
            tabs[idx].url = target
        }
        if let u = URL(string: target) {
            webViewMap[activeTabId]?.load(URLRequest(url: u))
        }
    }

    private func saveLastVisited() {
        if let u = webViewMap[activeTabId]?.url?.absoluteString, u.hasPrefix("http") {
            UserDefaults.standard.set(u, forKey: lastUrlKey)
        }
    }

    private func openNewTab(url: String = blankTabURL) {
        let t = BrowserTabModel(id: UUID().uuidString, title: url == blankTabURL ? "起始页" : "加载中...", url: url)
        tabs.append(t)
        activeTabId = t.id
        urlInput = url == blankTabURL ? "" : url
        canGoBack = false
        canGoForward = false
        isLoading = false
        detectedVideoUrl = nil
        showTabsSheet = false
    }

    private func closeTab(_ id: String) {
        webViewMap[id]?.stopLoading()
        webViewMap[id]?.removeFromSuperview()
        webViewMap.removeValue(forKey: id)
        coordinators.removeValue(forKey: id)
        if tabs.count <= 1 {
            let fresh = BrowserTabModel(id: UUID().uuidString, title: "起始页", url: blankTabURL)
            tabs = [fresh]
            activeTabId = fresh.id
            urlInput = ""
        } else {
            tabs.removeAll { $0.id == id }
            if activeTabId == id, let first = tabs.first {
                activeTabId = first.id
                urlInput = first.url == blankTabURL ? "" : first.url
            }
        }
    }

    private func loadHistory() {
        let cutoff = Date().timeIntervalSince1970 - 14 * 86400
        if let d = UserDefaults.standard.data(forKey: historyKey),
           let h = try? JSONDecoder().decode([HistoryModel].self, from: d) {
            history = h.filter { $0.visitedAt >= cutoff }
        }
    }

    private func recordHistory(title: String, url: String) {
        let cutoff = Date().timeIntervalSince1970 - 14 * 86400
        history.removeAll { $0.url == url || $0.visitedAt < cutoff }
        history.insert(HistoryModel(title: title, url: url, visitedAt: Date().timeIntervalSince1970), at: 0)
        if history.count > 500 { history = Array(history.prefix(500)) }
        if let d = try? JSONEncoder().encode(history) {
            UserDefaults.standard.set(d, forKey: historyKey)
        }
    }

    private func displayHost(from urlStr: String) -> String {
        guard let u = URL(string: urlStr), let host = u.host, !host.isEmpty else {
            return urlStr
        }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    private func favoriteGradient(for name: String) -> LinearGradient {
        let palette: [[Color]] = [
            [Color(red: 0.16, green: 0.56, blue: 0.98), Color(red: 0.05, green: 0.36, blue: 0.88)],
            [Color(red: 0.48, green: 0.38, blue: 0.96), Color(red: 0.30, green: 0.22, blue: 0.78)],
            [Color(red: 0.18, green: 0.74, blue: 0.48), Color(red: 0.08, green: 0.54, blue: 0.34)],
            [Color(red: 0.96, green: 0.52, blue: 0.20), Color(red: 0.84, green: 0.36, blue: 0.10)],
            [Color(red: 0.92, green: 0.30, blue: 0.52), Color(red: 0.74, green: 0.16, blue: 0.36)],
            [Color(red: 0.14, green: 0.68, blue: 0.84), Color(red: 0.06, green: 0.48, blue: 0.68)]
        ]
        let idx = abs(name.hashValue) % palette.count
        return LinearGradient(colors: palette[idx], startPoint: .topLeading, endPoint: .bottomTrailing)
    }


    private func presentSystemShareSheet(for url: URL) {
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
                  let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController ?? scene.windows.first?.rootViewController else {
                return
            }
            var topVC = root
            while let presented = topVC.presentedViewController {
                topVC = presented
            }
            let avc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            if let pop = avc.popoverPresentationController {
                pop.sourceView = topVC.view
                pop.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            topVC.present(avc, animated: true)
        }
    }

    private func formatHistoryDate(_ ts: TimeInterval) -> String {
        let f = DateFormatter()
        f.timeZone = TimeZone.current
        f.dateFormat = "MM-dd HH:mm"
        return f.string(from: Date(timeIntervalSince1970: ts))
    }

    // MARK: - Downloads Sheet
    private var downloadsSheet: some View {
        NavigationStack {
            List {
                if downloadManager.items.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "arrow.down.doc")
                            .font(.system(size: 32))
                            .foregroundColor(.secondary)
                        Text("暂无下载内容")
                            .font(.system(size: 14, weight: .semibold))
                        Text("网页中点击下载或输入直链下载；.ipa 文件完成后会自动同步到「IPA管理 → 未签名」")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)
                } else {
                    ForEach(downloadManager.items) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 10) {
                                Image(systemName: item.fileName.lowercased().hasSuffix(".ipa") ? "shippingbox.fill" : "doc.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(item.fileName.lowercased().hasSuffix(".ipa") ? .accentColor : .secondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.fileName)
                                        .font(.system(size: 14.5, weight: .semibold))
                                        .lineLimit(1)
                                    HStack(spacing: 6) {
                                        if item.status == .downloading {
                                            Text(item.totalBytes > 0
                                                 ? "\(IpaManager.formatBytes(item.downloadedBytes)) / \(IpaManager.formatBytes(item.totalBytes))"
                                                 : IpaManager.formatBytes(item.downloadedBytes))
                                        } else if item.status == .completed {
                                            Text(IpaManager.formatBytes(item.downloadedBytes))
                                        } else {
                                            Text(item.errorMessage ?? "下载失败")
                                                .foregroundColor(.red)
                                        }
                                        if item.importedToUnsignedIpa {
                                            Text("· 已加入未签名 IPA")
                                                .foregroundColor(Color(red: 0.12, green: 0.65, blue: 0.36))
                                        }
                                    }
                                    .font(.system(size: 11.5))
                                    .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if item.status == .completed, let localURL = downloadManager.localFileURL(for: item) {
                                    presentSystemShareSheet(for: localURL)
                                }
                            }

                            if item.status == .downloading {
                                ProgressView(value: item.progress)
                            } else if item.status == .completed {
                                HStack(spacing: 10) {
                                    if item.fileName.lowercased().hasSuffix(".ipa") && !item.importedToUnsignedIpa {
                                        Button("加入未签名 IPA") {
                                            if downloadManager.importToIpaManager(item) {
                                                toastMsg = "已导入到「IPA管理 → 未签名」"
                                            } else {
                                                toastMsg = "导入失败"
                                            }
                                        }
                                        .font(.system(size: 12, weight: .semibold))
                                        .buttonStyle(.bordered)
                                    }
                                    if let localURL = downloadManager.localFileURL(for: item) {
                                        Button {
                                            presentSystemShareSheet(for: localURL)
                                        } label: {
                                            Label("打开 / 分享", systemImage: "square.and.arrow.up")
                                                .font(.system(size: 12, weight: .medium))
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }
                            } else if item.status == .failed {
                                Button("重试") {
                                    downloadManager.deleteItem(item)
                                    downloadManager.startDownload(urlStr: item.sourceUrl, suggestedName: item.fileName)
                                }
                                .font(.system(size: 12, weight: .semibold))
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onDelete { offsets in
                        for idx in offsets {
                            downloadManager.deleteItem(downloadManager.items[idx])
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("下载管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    HStack(spacing: 12) {
                        Button {
                            newDownloadUrlInput = ""
                            showNewDownloadAlert = true
                        } label: {
                            Image(systemName: "plus")
                        }
                        if downloadManager.items.contains(where: { $0.status != .downloading }) {
                            Button("清空") {
                                downloadManager.clearCompleted()
                            }
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { showDownloadsSheet = false }
                }
            }
        }
    }
}

private struct BrowserShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
