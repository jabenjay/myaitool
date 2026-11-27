import SwiftUI

struct ProxySettingsView: View {
    @ObservedObject var prefs: AppPreferences
    @ObservedObject var proxyManager: ProxyManager

    @State private var showAddSheet = false
    @State private var nameInput = ""
    @State private var hostInput = ""
    @State private var portInput = "7890"
    @State private var proto: ProxyProtocolType = .http

    var body: some View {
        List {
            Section {
                Toggle(isOn: $proxyManager.enabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("代理总开关").font(.system(size: 15, weight: .semibold))
                        Text(proxyManager.statusMessage)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                Toggle(isOn: $prefs.githubMirrorEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("镜像加速").font(.system(size: 15, weight: .semibold))
                        Text("用于给Github资源进行加速")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
            }

            Section("代理节点") {
                if proxyManager.nodes.isEmpty {
                    Text("暂无代理节点，点击右上角添加")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                } else {
                    ForEach(proxyManager.nodes) { node in
                        HStack {
                            Button {
                                proxyManager.selectedId = node.id
                            } label: {
                                HStack {
                                    Image(systemName: proxyManager.selectedId == node.id ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(proxyManager.selectedId == node.id ? .accentColor : .secondary)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(node.name).font(.system(size: 14, weight: .semibold))
                                        Text("\(node.protocolType.rawValue) · \(node.host):\(node.port)")
                                            .font(.system(size: 11.5))
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            if let ms = proxyManager.latencies[node.id] {
                                Text(ms >= 0 ? "\(ms)ms" : "超时")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundColor(ms >= 0 ? .green : .red)
                            }
                            Button("测速") { proxyManager.testNode(node) }
                                .font(.system(size: 12))
                                .buttonStyle(.bordered)
                        }
                    }
                    .onDelete { idxs in
                        idxs.forEach { proxyManager.deleteNode(id: proxyManager.nodes[$0].id) }
                    }
                }
            }
        }
        .navigationTitle("网络代理")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                Form {
                    TextField("节点名称", text: $nameInput)
                    TextField("主机地址 (如 127.0.0.1)", text: $hostInput)
                        .autocapitalization(.none)
                    TextField("端口 (如 7890)", text: $portInput)
                        .keyboardType(.numberPad)
                    Picker("协议", selection: $proto) {
                        ForEach(ProxyProtocolType.allCases) { p in
                            Text(p.rawValue).tag(p)
                        }
                    }
                }
                .navigationTitle("添加代理节点")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { showAddSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            let port = Int(portInput) ?? 7890
                            let item = ProxyNodeItem(
                                name: nameInput.isEmpty ? hostInput : nameInput,
                                host: hostInput.trimmingCharacters(in: .whitespaces),
                                port: port,
                                protocolType: proto
                            )
                            proxyManager.addNode(item)
                            nameInput = ""; hostInput = ""; portInput = "7890"
                            showAddSheet = false
                        }
                        .disabled(hostInput.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }
}
