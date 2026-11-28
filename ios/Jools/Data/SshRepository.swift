import Foundation

enum SshAuthType: String, Codable, CaseIterable, Identifiable {
    case password = "PASSWORD"
    case privateKey = "PRIVATE_KEY"
    var id: String { rawValue }
    var label: String {
        switch self {
        case .password: return "密码认证"
        case .privateKey: return "私钥认证"
        }
    }
}


struct SshQuickCommand: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var title: String
    var command: String
}

struct SshKeyItem: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var privateKey: String
    var publicKey: String = ""
    var createdAt: TimeInterval = Date().timeIntervalSince1970

    enum CodingKeys: String, CodingKey {
        case id, name, privateKey, publicKey, createdAt
    }

    init(id: String = UUID().uuidString, name: String, privateKey: String, publicKey: String = "", createdAt: TimeInterval = Date().timeIntervalSince1970) {
        self.id = id
        self.name = name
        self.privateKey = privateKey
        self.publicKey = publicKey
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        name = try c.decode(String.self, forKey: .name)
        privateKey = try c.decode(String.self, forKey: .privateKey)
        publicKey = try c.decodeIfPresent(String.self, forKey: .publicKey) ?? ""
        createdAt = try c.decodeIfPresent(TimeInterval.self, forKey: .createdAt) ?? Date().timeIntervalSince1970
    }
}

struct SshServerProfile: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var name: String
    var host: String
    var port: Int = 22
    var username: String = "root"
    var authType: SshAuthType = .password
    var password: String = ""
    var keyId: String = ""
    var keyPassphrase: String = ""
    var lastConnectedAt: TimeInterval = 0
}

@MainActor
final class SshRepository: ObservableObject {
    static let shared = SshRepository()
    private let defaults = UserDefaults.standard
    private let serversKey = "ssh_servers_json"
    private let keysKey = "ssh_keys_json"
    private let quickCmdsKey = "ssh_quick_commands_json"

    @Published var servers: [SshServerProfile] = []
    @Published var keys: [SshKeyItem] = []
    @Published var quickCommands: [SshQuickCommand] = []

    private init() {
        loadAll()
        loadQuickCommands()
    }

    private func loadAll() {
        if let d = defaults.data(forKey: serversKey),
           let decoded = try? JSONDecoder().decode([SshServerProfile].self, from: d) {
            servers = decoded
        }
        if let d = defaults.data(forKey: keysKey),
           let decoded = try? JSONDecoder().decode([SshKeyItem].self, from: d) {
            keys = decoded.sorted { $0.createdAt < $1.createdAt }
        }
    }

    private func saveServers() {
        if let d = try? JSONEncoder().encode(servers) {
            defaults.set(d, forKey: serversKey)
        }
    }

    private func saveKeys() {
        if let d = try? JSONEncoder().encode(keys) {
            defaults.set(d, forKey: keysKey)
        }
    }

    func upsertServer(_ profile: SshServerProfile) {
        if let idx = servers.firstIndex(where: { $0.id == profile.id }) {
            servers[idx] = profile
        } else {
            servers.insert(profile, at: 0)
        }
        saveServers()
    }

    func deleteServer(id: String) {
        servers.removeAll { $0.id == id }
        saveServers()
    }

    func touchLastConnected(id: String) {
        let now = Date().timeIntervalSince1970
        servers = servers.map {
            var copy = $0
            if copy.id == id { copy.lastConnectedAt = now }
            return copy
        }
        saveServers()
    }

    func upsertKey(_ item: SshKeyItem) {
        if let idx = keys.firstIndex(where: { $0.id == item.id }) {
            keys[idx] = item
        } else {
            keys.append(item)
        }
        keys.sort { $0.createdAt < $1.createdAt }
        saveKeys()
    }

    func deleteKey(id: String) {
        keys.removeAll { $0.id == id }
        saveKeys()
    }


    private func loadQuickCommands() {
        if let data = UserDefaults.standard.data(forKey: quickCmdsKey),
           let decoded = try? JSONDecoder().decode([SshQuickCommand].self, from: data),
           !decoded.isEmpty {
            quickCommands = decoded
        } else {
            quickCommands = [
                SshQuickCommand(title: "ls -la", command: "ls -la"),
                SshQuickCommand(title: "df -h", command: "df -h"),
                SshQuickCommand(title: "free -h", command: "free -h"),
                SshQuickCommand(title: "uptime", command: "uptime"),
                SshQuickCommand(title: "ps aux", command: "ps aux | head -n 15")
            ]
        }
    }

    private func saveQuickCommands() {
        if let data = try? JSONEncoder().encode(quickCommands) {
            UserDefaults.standard.set(data, forKey: quickCmdsKey)
        }
    }

    func upsertQuickCommand(_ item: SshQuickCommand) {
        if let idx = quickCommands.firstIndex(where: { $0.id == item.id }) {
            quickCommands[idx] = item
        } else {
            quickCommands.append(item)
        }
        saveQuickCommands()
    }

    func deleteQuickCommand(id: String) {
        quickCommands.removeAll { $0.id == id }
        saveQuickCommands()
    }

    func resolvePrivateKey(for profile: SshServerProfile) -> String {
        keys.first(where: { $0.id == profile.keyId })?.privateKey ?? ""
    }
}
