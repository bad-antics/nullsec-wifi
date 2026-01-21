import SwiftUI
import Network
import CoreLocation
import NetworkExtension

/**
 * NullSec WiFi - iOS App
 * 
 * Advanced Wireless Security Analyzer
 * 
 * @author @AnonAntics
 * @website https://github.com/bad-antics
 * @discord discord.gg/killers
 */

@main
struct NullSecWifiApp: App {
    @StateObject private var appState = AppState()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .preferredColorScheme(.dark)
        }
    }
}

// MARK: - App State
class AppState: ObservableObject {
    @Published var isPremium: Bool = false
    @Published var licenseKey: String = ""
    @Published var networks: [WifiNetwork] = []
    @Published var isScanning: Bool = false
    @Published var alerts: [SecurityAlert] = []
    
    private let licenseManager = LicenseManager()
    
    init() {
        isPremium = licenseManager.isPremium()
    }
    
    func activateLicense(_ key: String) -> (success: Bool, message: String) {
        let result = licenseManager.activate(key: key)
        isPremium = result.success
        return result
    }
}

// MARK: - Content View
struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            ScannerView()
                .tabItem {
                    Image(systemName: "wifi")
                    Text("Scanner")
                }
                .tag(0)
            
            ChannelView()
                .tabItem {
                    Image(systemName: "chart.bar.fill")
                    Text("Channels")
                }
                .tag(1)
            
            AnalysisView()
                .tabItem {
                    Image(systemName: "shield.fill")
                    Text("Analysis")
                }
                .tag(2)
            
            AlertsView()
                .tabItem {
                    Image(systemName: "exclamationmark.triangle.fill")
                    Text("Alerts")
                }
                .tag(3)
            
            SettingsView()
                .tabItem {
                    Image(systemName: "gear")
                    Text("Settings")
                }
                .tag(4)
        }
        .accentColor(NullSecColors.primary)
        .onAppear {
            setupTabBarAppearance()
        }
    }
    
    private func setupTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.backgroundColor = UIColor(NullSecColors.surfaceDark)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

// MARK: - Scanner View
struct ScannerView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var scanner = WifiScanner()
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Connected Network Card
                    if let connected = scanner.connectedNetwork {
                        ConnectedNetworkCard(network: connected)
                            .padding()
                    }
                    
                    // Scan Button
                    Button(action: { scanner.startScan() }) {
                        HStack {
                            Image(systemName: scanner.isScanning ? "stop.fill" : "antenna.radiowaves.left.and.right")
                            Text(scanner.isScanning ? "Scanning..." : "Scan Networks")
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(NullSecColors.primary)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)
                    .disabled(scanner.isScanning)
                    
                    // Network List
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(scanner.networks) { network in
                                NetworkCard(network: network)
                            }
                        }
                        .padding()
                    }
                    
                    // Stats Bar
                    HStack {
                        StatBadge(value: "\(scanner.networks.count)", label: "Networks")
                        Spacer()
                        StatBadge(value: "\(scanner.networks.filter { $0.band == "5 GHz" }.count)", label: "5 GHz")
                        Spacer()
                        StatBadge(value: "\(scanner.networks.filter { $0.security == .open }.count)", label: "Open")
                    }
                    .padding()
                    .background(NullSecColors.surfaceDark)
                }
            }
            .navigationTitle("📶 NullSec WiFi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { exportResults() }) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundColor(NullSecColors.primary)
                    }
                }
            }
        }
    }
    
    private func exportResults() {
        let json = scanner.exportToJSON()
        let activityVC = UIActivityViewController(
            activityItems: [json],
            applicationActivities: nil
        )
        
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
            window.rootViewController?.present(activityVC, animated: true)
        }
    }
}

// MARK: - Network Card
struct NetworkCard: View {
    let network: WifiNetwork
    @State private var showDetails = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                // Signal Indicator
                SignalIndicator(strength: network.signalStrength)
                
                VStack(alignment: .leading) {
                    HStack {
                        Text(network.ssid)
                            .font(.headline)
                            .foregroundColor(.white)
                        
                        if network.isHidden {
                            Image(systemName: "eye.slash.fill")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                    
                    Text(network.bssid)
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                
                Spacer()
                
                VStack(alignment: .trailing) {
                    SecurityBadge(security: network.security)
                    
                    if network.wpsEnabled {
                        Text("WPS")
                            .font(.caption2)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.3))
                            .foregroundColor(.orange)
                            .cornerRadius(4)
                    }
                }
            }
            
            if showDetails {
                Divider()
                    .background(Color.gray.opacity(0.3))
                
                HStack(spacing: 16) {
                    DetailItem(icon: "number", label: "Channel", value: "\(network.channel)")
                    DetailItem(icon: "waveform", label: "Band", value: network.band)
                    DetailItem(icon: "antenna.radiowaves.left.and.right", label: "Signal", value: "\(network.signalStrength) dBm")
                }
            }
        }
        .padding()
        .background(NullSecColors.cardBackground)
        .cornerRadius(12)
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                showDetails.toggle()
            }
        }
    }
}

// MARK: - Connected Network Card
struct ConnectedNetworkCard: View {
    let network: ConnectedNetwork
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                Text("Connected")
                    .font(.caption)
                    .foregroundColor(.green)
            }
            
            Text(network.ssid)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                VStack(alignment: .leading) {
                    Text("IP Address")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Text(network.ipAddress)
                        .font(.subheadline)
                        .foregroundColor(.white)
                }
                
                VStack(alignment: .leading) {
                    Text("Speed")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Text("\(network.linkSpeed) Mbps")
                        .font(.subheadline)
                        .foregroundColor(.white)
                }
                
                VStack(alignment: .leading) {
                    Text("Channel")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Text("\(network.channel)")
                        .font(.subheadline)
                        .foregroundColor(.white)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [NullSecColors.primary.opacity(0.3), NullSecColors.surfaceDark],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(NullSecColors.primary.opacity(0.5), lineWidth: 1)
        )
    }
}

// MARK: - Channel View
struct ChannelView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedBand = "2.4 GHz"
    
    let bands = ["2.4 GHz", "5 GHz"]
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                VStack {
                    Picker("Band", selection: $selectedBand) {
                        ForEach(bands, id: \.self) { band in
                            Text(band).tag(band)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding()
                    
                    // Channel Graph
                    ChannelGraph(networks: appState.networks, band: selectedBand)
                        .frame(height: 200)
                        .padding()
                    
                    // Recommendations
                    VStack(alignment: .leading, spacing: 12) {
                        Text("📡 Recommendations")
                            .font(.headline)
                            .foregroundColor(.white)
                        
                        RecommendationCard(
                            icon: "checkmark.circle.fill",
                            color: .green,
                            text: "Optimal channel: \(selectedBand == "2.4 GHz" ? "6" : "36")"
                        )
                        
                        RecommendationCard(
                            icon: "exclamationmark.triangle.fill",
                            color: .yellow,
                            text: "Channel 1 is congested with 5 networks"
                        )
                    }
                    .padding()
                    .background(NullSecColors.cardBackground)
                    .cornerRadius(12)
                    .padding()
                    
                    Spacer()
                }
            }
            .navigationTitle("Channel Analysis")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Channel Graph
struct ChannelGraph: View {
    let networks: [WifiNetwork]
    let band: String
    
    var channels: [Int] {
        if band == "2.4 GHz" {
            return Array(1...14)
        } else {
            return [36, 40, 44, 48, 52, 56, 60, 64, 149, 153, 157, 161, 165]
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(channels, id: \.self) { channel in
                    let count = networks.filter { $0.channel == channel && $0.band == band }.count
                    let height = count > 0 ? CGFloat(count) / 10.0 * geometry.size.height : 4
                    
                    VStack {
                        Rectangle()
                            .fill(barColor(for: count))
                            .frame(width: (geometry.size.width / CGFloat(channels.count)) - 8, height: max(height, 4))
                        
                        Text("\(channel)")
                            .font(.system(size: 8))
                            .foregroundColor(.gray)
                    }
                }
            }
        }
        .background(NullSecColors.surfaceDark)
        .cornerRadius(8)
    }
    
    private func barColor(for count: Int) -> Color {
        switch count {
        case 0: return NullSecColors.channelFree
        case 1...2: return NullSecColors.channelLow
        case 3...4: return NullSecColors.channelMedium
        default: return NullSecColors.channelCongested
        }
    }
}

// MARK: - Analysis View
struct AnalysisView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 16) {
                        // Security Overview
                        SecurityOverviewCard(networks: appState.networks)
                        
                        // Network Security List
                        ForEach(appState.networks.sorted { $0.securityRating.rawValue > $1.securityRating.rawValue }) { network in
                            NetworkSecurityCard(network: network)
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("Security Analysis")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Security Overview Card
struct SecurityOverviewCard: View {
    let networks: [WifiNetwork]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🛡️ Security Overview")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 16) {
                SecurityStat(count: networks.filter { $0.security == .wpa3 || $0.security == .wpa2 }.count, 
                           label: "Secure", color: .green)
                SecurityStat(count: networks.filter { $0.security == .wpa }.count,
                           label: "Weak", color: .yellow)
                SecurityStat(count: networks.filter { $0.security == .wep || $0.security == .open }.count,
                           label: "Critical", color: .red)
            }
            
            // WPS Warning
            let wpsCount = networks.filter { $0.wpsEnabled }.count
            if wpsCount > 0 {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("\(wpsCount) networks have WPS enabled")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
            }
        }
        .padding()
        .background(NullSecColors.cardBackground)
        .cornerRadius(12)
    }
}

// MARK: - Network Security Card
struct NetworkSecurityCard: View {
    let network: WifiNetwork
    
    var body: some View {
        HStack {
            Circle()
                .fill(securityColor)
                .frame(width: 12, height: 12)
            
            VStack(alignment: .leading) {
                Text(network.ssid)
                    .font(.subheadline)
                    .foregroundColor(.white)
                Text(network.security.rawValue)
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Text(network.securityRating.emoji)
        }
        .padding()
        .background(NullSecColors.surfaceDark)
        .cornerRadius(8)
    }
    
    var securityColor: Color {
        switch network.securityRating {
        case .excellent, .good: return .green
        case .fair: return .yellow
        case .weak: return .orange
        case .critical, .none: return .red
        case .unknown: return .gray
        }
    }
}

// MARK: - Alerts View (Premium)
struct AlertsView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                if appState.isPremium {
                    if appState.alerts.isEmpty {
                        VStack {
                            Image(systemName: "checkmark.shield.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.green)
                            Text("No Security Alerts")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("Your network appears safe")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                        }
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 12) {
                                ForEach(appState.alerts) { alert in
                                    AlertCard(alert: alert)
                                }
                            }
                            .padding()
                        }
                    }
                } else {
                    PremiumRequiredView(feature: "Security Alerts")
                }
            }
            .navigationTitle("Alerts")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Alert Card
struct AlertCard: View {
    let alert: SecurityAlert
    
    var body: some View {
        HStack {
            Circle()
                .fill(alert.severity.color)
                .frame(width: 8, height: 8)
            
            VStack(alignment: .leading) {
                Text(alert.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                Text(alert.message)
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Text(alert.severity.emoji)
        }
        .padding()
        .background(alert.severity.color.opacity(0.15))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(alert.severity.color.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - Settings View
struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @State private var licenseInput = ""
    @State private var showingActivation = false
    @State private var activationMessage = ""
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                Form {
                    // Premium Section
                    Section(header: Text("Premium Status")) {
                        HStack {
                            Image(systemName: appState.isPremium ? "checkmark.seal.fill" : "lock.fill")
                                .foregroundColor(appState.isPremium ? .yellow : .gray)
                            Text(appState.isPremium ? "Premium Active" : "Free Version")
                                .foregroundColor(.white)
                        }
                        
                        if !appState.isPremium {
                            Button(action: { showingActivation = true }) {
                                HStack {
                                    Image(systemName: "key.fill")
                                    Text("Enter License Key")
                                }
                                .foregroundColor(NullSecColors.primary)
                            }
                            
                            Link(destination: URL(string: "https://discord.gg/killers")!) {
                                HStack {
                                    Image(systemName: "gift.fill")
                                    Text("Get Premium at discord.gg/killers")
                                }
                                .foregroundColor(.yellow)
                            }
                        }
                    }
                    
                    // App Info
                    Section(header: Text("About")) {
                        HStack {
                            Text("Version")
                                .foregroundColor(.gray)
                            Spacer()
                            Text("1.0.0")
                                .foregroundColor(.white)
                        }
                        
                        HStack {
                            Text("Developer")
                                .foregroundColor(.gray)
                            Spacer()
                            Text("@AnonAntics")
                                .foregroundColor(NullSecColors.primary)
                        }
                        
                        Link(destination: URL(string: "https://github.com/bad-antics")!) {
                            HStack {
                                Text("GitHub")
                                    .foregroundColor(.gray)
                                Spacer()
                                Text("bad-antics")
                                    .foregroundColor(NullSecColors.primary)
                                Image(systemName: "arrow.up.right.square")
                                    .foregroundColor(NullSecColors.primary)
                            }
                        }
                        
                        Link(destination: URL(string: "https://twitter.com/AnonAntics")!) {
                            HStack {
                                Text("Twitter")
                                    .foregroundColor(.gray)
                                Spacer()
                                Text("@AnonAntics")
                                    .foregroundColor(NullSecColors.primary)
                                Image(systemName: "arrow.up.right.square")
                                    .foregroundColor(NullSecColors.primary)
                            }
                        }
                    }
                    
                    // Disclaimer
                    Section(header: Text("Disclaimer")) {
                        Text("This app is for authorized security testing and educational purposes only. Only analyze networks you own or have permission to test.")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingActivation) {
                LicenseActivationSheet(
                    licenseInput: $licenseInput,
                    message: $activationMessage,
                    onActivate: {
                        let result = appState.activateLicense(licenseInput)
                        activationMessage = result.message
                        if result.success {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                showingActivation = false
                            }
                        }
                    }
                )
            }
        }
    }
}

// MARK: - License Activation Sheet
struct LicenseActivationSheet: View {
    @Binding var licenseInput: String
    @Binding var message: String
    let onActivate: () -> Void
    
    var body: some View {
        NavigationView {
            ZStack {
                NullSecColors.backgroundDark.ignoresSafeArea()
                
                VStack(spacing: 24) {
                    Image(systemName: "key.fill")
                        .font(.system(size: 48))
                        .foregroundColor(NullSecColors.primary)
                    
                    Text("Activate Premium")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    TextField("NSWIFI-XXXX-XXXX-XXXX-XXXX", text: $licenseInput)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .autocapitalization(.allCharacters)
                        .disableAutocorrection(true)
                        .padding(.horizontal)
                    
                    if !message.isEmpty {
                        Text(message)
                            .font(.caption)
                            .foregroundColor(message.contains("✓") || message.contains("🔓") ? .green : .red)
                    }
                    
                    Button(action: onActivate) {
                        Text("Activate")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(NullSecColors.primary)
                            .cornerRadius(12)
                    }
                    .padding(.horizontal)
                    
                    Link(destination: URL(string: "https://discord.gg/killers")!) {
                        Text("Get key at discord.gg/killers")
                            .font(.subheadline)
                            .foregroundColor(.yellow)
                    }
                    
                    Spacer()
                }
                .padding(.top, 40)
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Premium Required View
struct PremiumRequiredView: View {
    let feature: String
    
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "lock.fill")
                .font(.system(size: 64))
                .foregroundColor(.yellow)
            
            Text("\(feature) requires Premium")
                .font(.headline)
                .foregroundColor(.white)
            
            Text("Unlock all features by getting a premium key")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            
            Link(destination: URL(string: "https://discord.gg/killers")!) {
                HStack {
                    Image(systemName: "gift.fill")
                    Text("Get Premium")
                }
                .font(.headline)
                .foregroundColor(.black)
                .padding()
                .background(Color.yellow)
                .cornerRadius(12)
            }
        }
        .padding()
    }
}

// MARK: - Helper Views
struct SignalIndicator: View {
    let strength: Int
    
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<4) { index in
                Rectangle()
                    .fill(index < signalBars ? signalColor : Color.gray.opacity(0.3))
                    .frame(width: 4, height: CGFloat(8 + index * 4))
            }
        }
    }
    
    var signalBars: Int {
        switch strength {
        case -50...0: return 4
        case -60..<(-50): return 3
        case -70..<(-60): return 2
        case -80..<(-70): return 1
        default: return 0
        }
    }
    
    var signalColor: Color {
        switch signalBars {
        case 4: return .green
        case 3: return .green
        case 2: return .yellow
        case 1: return .orange
        default: return .red
        }
    }
}

struct SecurityBadge: View {
    let security: SecurityType
    
    var body: some View {
        Text(security.rawValue)
            .font(.caption2)
            .fontWeight(.medium)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(badgeColor.opacity(0.2))
            .foregroundColor(badgeColor)
            .cornerRadius(6)
    }
    
    var badgeColor: Color {
        switch security {
        case .wpa3: return .green
        case .wpa2: return .green
        case .wpa: return .yellow
        case .wep: return .red
        case .open: return .red
        case .unknown: return .gray
        }
    }
}

struct StatBadge: View {
    let value: String
    let label: String
    
    var body: some View {
        VStack {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(NullSecColors.primary)
            Text(label)
                .font(.caption)
                .foregroundColor(.gray)
        }
    }
}

struct DetailItem: View {
    let icon: String
    let label: String
    let value: String
    
    var body: some View {
        VStack {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(NullSecColors.primary)
            Text(value)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.white)
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
    }
}

struct SecurityStat: View {
    let count: Int
    let label: String
    let color: Color
    
    var body: some View {
        VStack {
            Text("\(count)")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(color)
            Text(label)
                .font(.caption)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

struct RecommendationCard: View {
    let icon: String
    let color: Color
    let text: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(color)
            Text(text)
                .font(.subheadline)
                .foregroundColor(.white)
        }
    }
}

// MARK: - Models
struct WifiNetwork: Identifiable {
    let id = UUID()
    let ssid: String
    let bssid: String
    let signalStrength: Int
    let channel: Int
    let band: String
    let security: SecurityType
    let securityRating: SecurityRating
    let wpsEnabled: Bool
    let isHidden: Bool
}

struct ConnectedNetwork {
    let ssid: String
    let bssid: String
    let ipAddress: String
    let linkSpeed: Int
    let channel: Int
}

struct SecurityAlert: Identifiable {
    let id = UUID()
    let title: String
    let message: String
    let severity: AlertSeverity
    let timestamp: Date
}

enum SecurityType: String {
    case wpa3 = "WPA3"
    case wpa2 = "WPA2"
    case wpa = "WPA"
    case wep = "WEP"
    case open = "Open"
    case unknown = "Unknown"
}

enum SecurityRating: Int {
    case excellent = 5
    case good = 4
    case fair = 3
    case weak = 2
    case critical = 1
    case none = 0
    case unknown = -1
    
    var emoji: String {
        switch self {
        case .excellent: return "🟢"
        case .good: return "🟢"
        case .fair: return "🟡"
        case .weak: return "🟠"
        case .critical: return "🔴"
        case .none: return "⚫"
        case .unknown: return "⚪"
        }
    }
}

enum AlertSeverity {
    case critical, high, medium, low
    
    var color: Color {
        switch self {
        case .critical: return .red
        case .high: return .orange
        case .medium: return .yellow
        case .low: return .green
        }
    }
    
    var emoji: String {
        switch self {
        case .critical: return "🔴"
        case .high: return "🟠"
        case .medium: return "🟡"
        case .low: return "🟢"
        }
    }
}

// MARK: - Colors
struct NullSecColors {
    static let primary = Color(red: 0, green: 0.83, blue: 1)
    static let secondary = Color(red: 0, green: 1, blue: 0.53)
    static let backgroundDark = Color(red: 0.04, green: 0.04, blue: 0.06)
    static let surfaceDark = Color(red: 0.07, green: 0.07, blue: 0.09)
    static let cardBackground = Color(red: 0.09, green: 0.09, blue: 0.12)
    
    static let channelFree = Color.green
    static let channelLow = Color(red: 0.53, green: 1, blue: 0)
    static let channelMedium = Color.yellow
    static let channelCongested = Color.red
}

// MARK: - WiFi Scanner
class WifiScanner: ObservableObject {
    @Published var networks: [WifiNetwork] = []
    @Published var connectedNetwork: ConnectedNetwork?
    @Published var isScanning = false
    
    func startScan() {
        isScanning = true
        
        // Note: iOS restricts WiFi scanning capabilities
        // This would require NEHotspotHelper entitlement from Apple
        // For demo, we simulate scan results
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            self.simulateScanResults()
            self.isScanning = false
        }
    }
    
    private func simulateScanResults() {
        // In production, this would use NEHotspotHelper
        // For demo purposes, showing placeholder
        networks = [
            WifiNetwork(ssid: "Home Network", bssid: "AA:BB:CC:DD:EE:01", signalStrength: -45, channel: 6, band: "2.4 GHz", security: .wpa2, securityRating: .good, wpsEnabled: false, isHidden: false),
            WifiNetwork(ssid: "Office_5G", bssid: "AA:BB:CC:DD:EE:02", signalStrength: -52, channel: 36, band: "5 GHz", security: .wpa3, securityRating: .excellent, wpsEnabled: false, isHidden: false),
        ]
        
        connectedNetwork = ConnectedNetwork(ssid: "Home Network", bssid: "AA:BB:CC:DD:EE:01", ipAddress: "192.168.1.100", linkSpeed: 144, channel: 6)
    }
    
    func exportToJSON() -> String {
        return """
        {
            "scanner": "NullSec WiFi v1.0.0",
            "author": "@AnonAntics",
            "scan_time": "\(Date())",
            "networks": \(networks.count)
        }
        """
    }
}

// MARK: - License Manager
class LicenseManager {
    private let defaults = UserDefaults.standard
    private let premiumKey = "nullsec_wifi_premium"
    
    func isPremium() -> Bool {
        return defaults.bool(forKey: premiumKey)
    }
    
    func activate(key: String) -> (success: Bool, message: String) {
        // Validate key format: NSWIFI-XXXX-XXXX-XXXX-XXXX
        let pattern = "^NSWIFI-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"
        guard key.range(of: pattern, options: .regularExpression) != nil else {
            return (false, "Invalid key format")
        }
        
        // In production, validate against server
        defaults.set(true, forKey: premiumKey)
        return (true, "🔓 Premium activated!")
    }
}
