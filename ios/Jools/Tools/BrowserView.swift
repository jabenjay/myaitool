import SwiftUI
import WebKit

private let blankTabURL = "about:blank"

struct BrowserTabModel: Identifiable, Equatable {
    let id: String
    var title: String
    var url: String
}

struct BookmarkModel: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var title: String
    var url: String
}

struct HistoryModel: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var title: String
    var url: String
    var visitedAt: TimeInterval
}

final class BrowserWebCoordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
    let tabId: String
    let onStateChange: (String, String?, String?, Bool, Bool, Bool) -> Void
    let onVideoDetected: (String, String) -> Void

    init(
        tabId: String,
        onStateChange: @escaping (String, String?, String?, Bool, Bool, Bool) -> Void,
        onVideoDetected: @escaping (String, String) -> Void
    ) {
        self.tabId = tabId
        self.onStateChange = onStateChange
        self.onVideoDetected = onVideoDetected
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, true, webView.canGoBack, webView.canGoForward)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        onStateChange(tabId, webView.url?.absoluteString, webView.title, false, webView.canGoBack, webView.canGoForward)
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

struct BrowserView: View {
    @ObservedObject var prefs: AppPreferences
    let initialUrl: String
    @Environment(\.dismiss) private var dismiss

    @State private var tabs: [BrowserTabModel] = []
    @State private var activeTabId: String = ""
    @State private var webViewMap: [String: WKWebView] = [:]
    @State private var coordinators: [String: BrowserWebCoordinator] = [:]

    @State private var urlInput: String = ""
    @State private var blankInput: String = ""
    @State private var canGoBack = false
    @State private var canGoForward = false
    @State private var isLoading = false

    @State private var showTabsSheet = false
    @State private var showMenuSheet = false
    @State private var showBookmarksSheet = false
    @State private var showHistorySheet = false
    @State private var bookmarks: [BookmarkModel] = []
    @State private var history: [HistoryModel] = []
    @State private var adBlockEnabled: Bool = UserDefaults.standard.bool(forKey: "adblock_enabled")

    @State private var activeVideoUrl: String?
    @State private var activeVideoTitle: String = ""
    @State private var toastMsg: String?

    private let lastUrlKey = "last_visited_url"
    private let bookmarksKey = "browser_bookmarks_json"
    private let historyKey = "browser_history_json"

    var body: some View {
        let activeTab = tabs.first { $0.id == activeTabId }
        let isBlank = activeTab?.url == blankTabURL

        ZStack {
            VStack(spacing: 0) {
                // Top Address Bar
                HStack(spacing: 8) {
                    TextField("搜索或输入网址", text: $urlInput)
                        .textFieldStyle(.roundedBorder)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .submitLabel(.go)
                        .onSubmit { navigateTo(urlInput) }

                    Button {
                        if let wv = webViewMap[activeTabId] {
                            if isLoading { wv.stopLoading() } else { wv.reload() }
                        }
                    } label: {
                        Image(systemName: isLoading ? "xmark" : "arrow.clockwise")
                    }
                    Button { showBookmarksSheet = true } label: {
                        Image(systemName: "star")
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial)

                // WebView / Blank Page
                ZStack {
                    MultiTabWebViewHost(
                        tabs: tabs,
                        activeTabId: activeTabId,
                        webViews: webViewMap,
                        makeWebView: { tid, start in getOrCreateWebView(tabId: tid, startUrl: start) }
                    )

                    if isBlank {
                        VStack(spacing: 22) {
                            Text("Light Browser")
                                .font(.system(size: 28, weight: .semibold))
                                .tracking(0.6)

                            HStack(spacing: 8) {
                                Image(systemName: "magnifyingglass")
                                    .foregroundColor(.secondary)
                                TextField("搜索或输入网址", text: $blankInput)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                                    .submitLabel(.go)
                                    .onSubmit {
                                        navigateTo(blankInput)
                                        blankInput = ""
                                    }
                                if !blankInput.isEmpty {
                                    Button {
                                        navigateTo(blankInput)
                                        blankInput = ""
                                    } label: {
                                        Image(systemName: "arrow.right.circle.fill")
                                            .font(.system(size: 20))
                                    }
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Capsule())
                            .padding(.horizontal, 24)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color(uiColor: .systemBackground))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Bottom Toolbar
                HStack {
                    Button { webViewMap[activeTabId]?.goBack() } label: {
                        Image(systemName: "chevron.left")
                    }
                    .disabled(!canGoBack)
                    Spacer()
                    Button { webViewMap[activeTabId]?.goForward() } label: {
                        Image(systemName: "chevron.right")
                    }
                    .disabled(!canGoForward)
                    Spacer()
                    Button { navigateTo(prefs.browserHomeUrl) } label: {
                        Image(systemName: "house")
                    }
                    Spacer()
                    Button { showTabsSheet = true } label: {
                        Text("\(tabs.count)")
                            .font(.system(size: 11, weight: .bold))
                            .frame(width: 22, height: 22)
                            .overlay(RoundedRectangle(cornerRadius: 5).stroke(Color.primary, lineWidth: 1.6))
                    }
                    Spacer()
                    Button { showMenuSheet = true } label: {
                        Image(systemName: "line.3.horizontal")
                    }
                    Spacer()
                    Button {
                        saveLastVisited()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                }
                .foregroundColor(.primary)
                .padding(.horizontal, 22)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial)
            }

            if let vUrl = activeVideoUrl {
                BuiltInVideoPlayerView(
                    videoUrl: vUrl,
                    pageTitle: activeVideoTitle,
                    onClose: { activeVideoUrl = nil },
                    onDownload: { dl in
                        if let u = URL(string: dl) { UIApplication.shared.open(u) }
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
        .sheet(isPresented: $showTabsSheet) { tabsSheet }
        .sheet(isPresented: $showMenuSheet) { menuSheet }
        .sheet(isPresented: $showBookmarksSheet) { bookmarksSheet }
        .sheet(isPresented: $showHistorySheet) { historySheet }
        .alert(toastMsg ?? "", isPresented: Binding(get: { toastMsg != nil }, set: { if !$0 { toastMsg = nil } })) {
            Button("确定", role: .cancel) {}
        }
    }

    private func setupInitialTab() {
        loadBookmarksAndHistory()
        if tabs.isEmpty {
            let saved = UserDefaults.standard.string(forKey: lastUrlKey) ?? ""
            let firstUrl: String = {
                if !initialUrl.trimmingCharacters(in: .whitespaces).isEmpty { return initialUrl }
                if saved.hasPrefix("http://") || saved.hasPrefix("https://") { return saved }
                return prefs.browserHomeUrl
            }()
            let first = BrowserTabModel(id: UUID().uuidString, title: "新标签页", url: firstUrl)
            tabs = [first]
            activeTabId = first.id
            urlInput = firstUrl == blankTabURL ? "" : firstUrl
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
                            urlInput = u
                            UserDefaults.standard.set(u, forKey: lastUrlKey)
                            if !loading { recordHistory(title: t ?? u, url: u) }
                        }
                    }
                }
            },
            onVideoDetected: { src, title in
                if activeVideoUrl == nil {
                    activeVideoTitle = title
                    activeVideoUrl = src
                }
            }
        )
        coordinators[tabId] = coord
        config.userContentController.add(coord, name: "joolsVideo")

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = coord
        wv.allowsBackForwardNavigationGestures = true
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

    private func openNewTab() {
        let t = BrowserTabModel(id: UUID().uuidString, title: "新标签页", url: blankTabURL)
        tabs.append(t)
        activeTabId = t.id
        urlInput = ""
        canGoBack = false
        canGoForward = false
        isLoading = false
        showTabsSheet = false
    }

    private func closeTab(_ id: String) {
        webViewMap[id]?.stopLoading()
        webViewMap[id]?.removeFromSuperview()
        webViewMap.removeValue(forKey: id)
        coordinators.removeValue(forKey: id)
        if tabs.count <= 1 {
            let fresh = BrowserTabModel(id: UUID().uuidString, title: "新标签页", url: blankTabURL)
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

    private func loadBookmarksAndHistory() {
        if let d = UserDefaults.standard.data(forKey: bookmarksKey),
           let b = try? JSONDecoder().decode([BookmarkModel].self, from: d) {
            bookmarks = b
        }
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
        if let d = try? JSONEncoder().encode(history) {
            UserDefaults.standard.set(d, forKey: historyKey)
        }
    }

    private var tabsSheet: some View {
        NavigationStack {
            List {
                ForEach(tabs) { t in
                    HStack {
                        Button {
                            activeTabId = t.id
                            urlInput = t.url == blankTabURL ? "" : t.url
                            canGoBack = webViewMap[t.id]?.canGoBack ?? false
                            canGoForward = webViewMap[t.id]?.canGoForward ?? false
                            showTabsSheet = false
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(t.title).font(.system(size: 14, weight: t.id == activeTabId ? .bold : .medium))
                                    .foregroundColor(t.id == activeTabId ? .accentColor : .primary)
                                Text(t.url == blankTabURL ? "空白页" : t.url).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                            }
                        }
                        Spacer()
                        Button { closeTab(t.id) } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("标签页 (\(tabs.count))")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { openNewTab() } label: {
                        Label("新建标签页", systemImage: "plus")
                    }
                }
            }
        }
    }

    private var menuSheet: some View {
        NavigationStack {
            List {
                Toggle("屏蔽广告", isOn: $adBlockEnabled)
                    .onChange(of: adBlockEnabled) { v in
                        UserDefaults.standard.set(v, forKey: "adblock_enabled")
                    }
                Button("书签管理") { showMenuSheet = false; showBookmarksSheet = true }
                Button("历史记录 (14天)") { showMenuSheet = false; showHistorySheet = true }
                Button("添加当前页到书签") {
                    if let cur = tabs.first(where: { $0.id == activeTabId }), cur.url != blankTabURL {
                        bookmarks.append(BookmarkModel(title: cur.title, url: cur.url))
                        if let d = try? JSONEncoder().encode(bookmarks) {
                            UserDefaults.standard.set(d, forKey: bookmarksKey)
                        }
                        toastMsg = "已添加书签"
                    }
                    showMenuSheet = false
                }
                Button("设为默认主页") {
                    if let cur = tabs.first(where: { $0.id == activeTabId }), cur.url != blankTabURL {
                        prefs.browserHomeUrl = cur.url
                        toastMsg = "已设为主页"
                    }
                    showMenuSheet = false
                }
            }
            .navigationTitle("浏览器菜单")
        }
    }

    private var bookmarksSheet: some View {
        NavigationStack {
            List {
                ForEach(bookmarks) { b in
                    Button {
                        showBookmarksSheet = false
                        navigateTo(b.url)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(b.title).font(.system(size: 14, weight: .medium))
                            Text(b.url).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                        }
                    }
                }
                .onDelete { idxs in
                    bookmarks.remove(atOffsets: idxs)
                    if let d = try? JSONEncoder().encode(bookmarks) {
                        UserDefaults.standard.set(d, forKey: bookmarksKey)
                    }
                }
            }
            .navigationTitle("书签")
        }
    }

    private var historySheet: some View {
        NavigationStack {
            List {
                ForEach(history) { h in
                    Button {
                        showHistorySheet = false
                        navigateTo(h.url)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(h.title).font(.system(size: 14, weight: .medium))
                            Text(h.url).font(.system(size: 11)).foregroundColor(.secondary).lineLimit(1)
                        }
                    }
                }
            }
            .navigationTitle("历史记录")
            .toolbar {
                ToolbarItem(placement: .destructiveAction) {
                    Button("清空") {
                        history.removeAll()
                        UserDefaults.standard.removeObject(forKey: historyKey)
                    }
                }
            }
        }
    }
}
