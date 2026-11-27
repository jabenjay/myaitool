import Foundation

enum ProxyProtocolType: String, Codable, CaseIterable, Identifiable {
    case http = "HTTP"
    case socks = "SOCKS5"
    var id: String { rawValue }
}

struct ProxyNodeItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var host: String
    var port: Int
    var protocolType: ProxyProtocolType = .http
}

@MainActor
final class ProxyManager: ObservableObject {
    static let shared = ProxyManager()
    private let defaults = UserDefaults.standard
    private let nodesKey = "proxy_nodes_json"
    private let selectedIdKey = "proxy_selected_id"
    private let enabledKey = "proxy_enabled"

    @Published var enabled: Bool {
        didSet { defaults.set(enabled, forKey: enabledKey) }
    }
    @Published var nodes: [ProxyNodeItem] {
        didSet { saveNodes() }
    }
    @Published var selectedId: String? {
        didSet { defaults.set(selectedId, forKey: selectedIdKey) }
    }
    @Published var latencies: [String: Int] = [:]

    var selectedNode: ProxyNodeItem? {
        nodes.first { $0.id == selectedId }
    }

    var statusMessage: String {
        if !enabled { return "未开启代理" }
        if let n = selectedNode { return "已启用: \(n.name) (\(n.host):\(n.port))" }
        return "已启用（未选节点）"
    }

    private init() {
        self.enabled = defaults.bool(forKey: enabledKey)
        self.selectedId = defaults.string(forKey: selectedIdKey)
        if let data = defaults.data(forKey: nodesKey),
           let decoded = try? JSONDecoder().decode([ProxyNodeItem].self, from: data) {
            self.nodes = decoded
        } else {
            self.nodes = []
        }
    }

    private func saveNodes() {
        if let data = try? JSONEncoder().encode(nodes) {
            defaults.set(data, forKey: nodesKey)
        }
    }

    func addNode(_ node: ProxyNodeItem) {
        nodes.append(node)
        if selectedId == nil { selectedId = node.id }
    }

    func deleteNode(id: String) {
        nodes.removeAll { $0.id == id }
        if selectedId == id { selectedId = nodes.first?.id }
    }

    func testNode(_ node: ProxyNodeItem) {
        Task {
            let start = Date()
            let config = URLSessionConfiguration.ephemeral
            config.timeoutIntervalForRequest = 6
            if node.protocolType == .http {
                config.connectionProxyDictionary = [
                    kCFNetworkProxiesHTTPEnable as String: 1,
                    kCFNetworkProxiesHTTPProxy as String: node.host,
                    kCFNetworkProxiesHTTPPort as String: node.port,
                    "HTTPSEnable": 1,
                    "HTTPSProxy": node.host,
                    "HTTPSPort": node.port
                ]
            } else {
                config.connectionProxyDictionary = [
                    "SOCKSEnable": 1,
                    "SOCKSProxy": node.host,
                    "SOCKSPort": node.port
                ]
            }
            let session = URLSession(configuration: config)
            if let u = URL(string: "https://www.google.com/generate_204"),
               let (_, resp) = try? await session.data(from: u),
               let http = resp as? HTTPURLResponse, (200...399).contains(http.statusCode) {
                let ms = Int(Date().timeIntervalSince(start) * 1000)
                latencies[node.id] = ms
            } else {
                latencies[node.id] = -1
            }
        }
    }
}
