package com.nullsec.wifi.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * NullSec WiFi Scanner
 * 
 * Advanced WiFi network scanner with security analysis
 * 
 * Features:
 * - Real-time network scanning
 * - Security type detection
 * - WPS vulnerability check
 * - Signal strength analysis
 * - Hidden network detection (Premium)
 * 
 * @author @AnonAntics
 * @discord discord.gg/killers
 */
class WifiScanner(private val context: Context) {

    private val wifiManager: WifiManager = 
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _scanResults = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val scanResults: StateFlow<List<WifiNetwork>> = _scanResults
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    processScanResults()
                }
            }
        }
    }
    
    init {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
    }
    
    /**
     * Start continuous WiFi scanning
     */
    fun startScanning(intervalMs: Long = 5000) {
        _isScanning.value = true
        
        scanJob = scope.launch {
            while (isActive) {
                performScan()
                delay(intervalMs)
            }
        }
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        _isScanning.value = false
        scanJob?.cancel()
    }
    
    /**
     * Perform single scan
     */
    @Suppress("DEPRECATION")
    fun performScan(): Boolean {
        return wifiManager.startScan()
    }
    
    /**
     * Process scan results and convert to WifiNetwork objects
     */
    @Suppress("DEPRECATION")
    private fun processScanResults() {
        val results = wifiManager.scanResults
        val networks = results.map { result ->
            WifiNetwork(
                ssid = getSSID(result),
                bssid = result.BSSID,
                signalStrength = result.level,
                frequency = result.frequency,
                channel = frequencyToChannel(result.frequency),
                security = getSecurityType(result),
                securityRating = getSecurityRating(result),
                wpsEnabled = isWPSEnabled(result),
                isHidden = result.SSID.isNullOrEmpty(),
                capabilities = result.capabilities,
                timestamp = System.currentTimeMillis(),
                band = if (result.frequency > 5000) "5 GHz" else "2.4 GHz"
            )
        }.sortedByDescending { it.signalStrength }
        
        _scanResults.value = networks
    }
    
    /**
     * Get SSID from scan result
     */
    @Suppress("DEPRECATION")
    private fun getSSID(result: ScanResult): String {
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
        } else {
            result.SSID ?: ""
        }
        return if (ssid.isEmpty()) "[Hidden Network]" else ssid
    }
    
    /**
     * Determine security type from capabilities
     */
    private fun getSecurityType(result: ScanResult): SecurityType {
        val caps = result.capabilities.uppercase()
        
        return when {
            caps.contains("WPA3") -> SecurityType.WPA3
            caps.contains("WPA2") && caps.contains("WPA-") -> SecurityType.WPA_WPA2
            caps.contains("WPA2") -> SecurityType.WPA2
            caps.contains("WPA") -> SecurityType.WPA
            caps.contains("WEP") -> SecurityType.WEP
            caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }
    }
    
    /**
     * Get security rating for network
     */
    private fun getSecurityRating(result: ScanResult): SecurityRating {
        val caps = result.capabilities.uppercase()
        
        return when {
            caps.contains("WPA3") -> SecurityRating.EXCELLENT
            caps.contains("WPA2") && caps.contains("CCMP") -> SecurityRating.GOOD
            caps.contains("WPA2") && caps.contains("TKIP") -> SecurityRating.FAIR
            caps.contains("WPA") && !caps.contains("WPA2") -> SecurityRating.WEAK
            caps.contains("WEP") -> SecurityRating.CRITICAL
            !caps.contains("WPA") && !caps.contains("WEP") -> SecurityRating.NONE
            else -> SecurityRating.UNKNOWN
        }
    }
    
    /**
     * Check if WPS is enabled (potential vulnerability)
     */
    private fun isWPSEnabled(result: ScanResult): Boolean {
        return result.capabilities.contains("WPS")
    }
    
    /**
     * Convert frequency to channel number
     */
    private fun frequencyToChannel(frequency: Int): Int {
        return when {
            // 2.4 GHz band
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
            // 5 GHz band
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
            // 6 GHz band (WiFi 6E)
            frequency in 5955..7115 -> (frequency - 5955) / 5 + 1
            else -> 0
        }
    }
    
    /**
     * Get currently connected network
     */
    @Suppress("DEPRECATION")
    fun getConnectedNetwork(): ConnectedNetwork? {
        val wifiInfo = wifiManager.connectionInfo ?: return null
        
        return ConnectedNetwork(
            ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "Unknown",
            bssid = wifiInfo.bssid ?: "00:00:00:00:00:00",
            ipAddress = intToIp(wifiInfo.ipAddress),
            linkSpeed = wifiInfo.linkSpeed,
            rssi = wifiInfo.rssi,
            frequency = wifiInfo.frequency,
            channel = frequencyToChannel(wifiInfo.frequency)
        )
    }
    
    /**
     * Convert int IP to string
     */
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
    
    /**
     * Get networks grouped by channel for analysis
     */
    fun getNetworksByChannel(): Map<Int, List<WifiNetwork>> {
        return _scanResults.value.groupBy { it.channel }
    }
    
    /**
     * Get optimal channel recommendation
     */
    fun getOptimalChannel(band: String = "2.4 GHz"): Int {
        val networks = _scanResults.value.filter { it.band == band }
        val channelCongestion = networks.groupBy { it.channel }
            .mapValues { it.value.size }
        
        val targetChannels = if (band == "2.4 GHz") listOf(1, 6, 11) else (36..165 step 4).toList()
        
        return targetChannels.minByOrNull { channel ->
            channelCongestion[channel] ?: 0
        } ?: targetChannels.first()
    }
    
    /**
     * Export scan results to JSON
     */
    fun exportToJson(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val networks = _scanResults.value
        
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"scan_time\": \"${dateFormat.format(Date())}\",\n")
        jsonBuilder.append("  \"scanner\": \"NullSec WiFi v1.0.0\",\n")
        jsonBuilder.append("  \"author\": \"@AnonAntics\",\n")
        jsonBuilder.append("  \"network_count\": ${networks.size},\n")
        jsonBuilder.append("  \"networks\": [\n")
        
        networks.forEachIndexed { index, network ->
            jsonBuilder.append("    {\n")
            jsonBuilder.append("      \"ssid\": \"${network.ssid}\",\n")
            jsonBuilder.append("      \"bssid\": \"${network.bssid}\",\n")
            jsonBuilder.append("      \"signal_dbm\": ${network.signalStrength},\n")
            jsonBuilder.append("      \"channel\": ${network.channel},\n")
            jsonBuilder.append("      \"frequency\": ${network.frequency},\n")
            jsonBuilder.append("      \"band\": \"${network.band}\",\n")
            jsonBuilder.append("      \"security\": \"${network.security.name}\",\n")
            jsonBuilder.append("      \"security_rating\": \"${network.securityRating.name}\",\n")
            jsonBuilder.append("      \"wps_enabled\": ${network.wpsEnabled},\n")
            jsonBuilder.append("      \"is_hidden\": ${network.isHidden},\n")
            jsonBuilder.append("      \"capabilities\": \"${network.capabilities}\"\n")
            jsonBuilder.append("    }${if (index < networks.size - 1) "," else ""}\n")
        }
        
        jsonBuilder.append("  ]\n")
        jsonBuilder.append("}")
        
        return jsonBuilder.toString()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopScanning()
        scope.cancel()
        try {
            context.unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }
}

/**
 * Represents a scanned WiFi network
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val frequency: Int,
    val channel: Int,
    val security: SecurityType,
    val securityRating: SecurityRating,
    val wpsEnabled: Boolean,
    val isHidden: Boolean,
    val capabilities: String,
    val timestamp: Long,
    val band: String
) {
    /**
     * Get signal quality percentage (0-100)
     */
    fun getSignalQuality(): Int {
        return when {
            signalStrength >= -50 -> 100
            signalStrength >= -60 -> 80
            signalStrength >= -70 -> 60
            signalStrength >= -80 -> 40
            signalStrength >= -90 -> 20
            else -> 0
        }
    }
    
    /**
     * Get signal description
     */
    fun getSignalDescription(): String {
        return when {
            signalStrength >= -50 -> "Excellent"
            signalStrength >= -60 -> "Good"
            signalStrength >= -70 -> "Fair"
            signalStrength >= -80 -> "Weak"
            else -> "Very Weak"
        }
    }
}

/**
 * Connected network info
 */
data class ConnectedNetwork(
    val ssid: String,
    val bssid: String,
    val ipAddress: String,
    val linkSpeed: Int,
    val rssi: Int,
    val frequency: Int,
    val channel: Int
)

/**
 * Security type enum
 */
enum class SecurityType {
    WPA3,
    WPA2,
    WPA_WPA2,
    WPA,
    WEP,
    OPEN,
    UNKNOWN
}

/**
 * Security rating enum
 */
enum class SecurityRating(val emoji: String, val description: String) {
    EXCELLENT("🟢", "Excellent - WPA3"),
    GOOD("🟢", "Good - WPA2-AES"),
    FAIR("🟡", "Fair - Consider upgrading"),
    WEAK("🟠", "Weak - Outdated security"),
    CRITICAL("🔴", "Critical - Easily compromised"),
    NONE("⚫", "No encryption"),
    UNKNOWN("⚪", "Unknown")
}
