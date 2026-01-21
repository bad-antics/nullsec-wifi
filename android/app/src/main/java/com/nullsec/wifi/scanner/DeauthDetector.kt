package com.nullsec.wifi.scanner

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * NullSec Deauth Detector (Premium Feature)
 * 
 * Detects potential deauthentication attacks by monitoring
 * for rapid disconnection patterns and beacon anomalies.
 * 
 * Detection methods:
 * - Rapid signal drops
 * - Frequent reconnection attempts
 * - BSSID spoofing detection
 * - Beacon frame anomalies
 * 
 * @author @AnonAntics
 * @discord discord.gg/killers (Premium Required)
 */
class DeauthDetector(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _alerts = MutableStateFlow<List<DeauthAlert>>(emptyList())
    val alerts: StateFlow<List<DeauthAlert>> = _alerts
    
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring
    
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Tracking variables
    private var lastRssi = 0
    private var disconnectCount = 0
    private var lastCheckTime = 0L
    private val rssiHistory = mutableListOf<RssiSample>()
    private val bssidHistory = mutableMapOf<String, MutableList<Long>>()
    
    companion object {
        private const val CHECK_INTERVAL_MS = 1000L
        private const val RSSI_DROP_THRESHOLD = 30 // dBm sudden drop
        private const val DISCONNECT_THRESHOLD = 3 // disconnects per minute
        private const val RSSI_HISTORY_SIZE = 60
        private const val BSSID_SPOOF_THRESHOLD = 5 // same BSSID different networks
    }
    
    /**
     * Start monitoring for deauth attacks
     * PREMIUM FEATURE
     */
    fun startMonitoring() {
        _isMonitoring.value = true
        disconnectCount = 0
        rssiHistory.clear()
        bssidHistory.clear()
        
        monitorJob = scope.launch {
            while (isActive) {
                checkForDeauth()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        monitorJob?.cancel()
    }
    
    /**
     * Perform deauth detection checks
     */
    @Suppress("DEPRECATION")
    private fun checkForDeauth() {
        val wifiInfo = wifiManager.connectionInfo ?: return
        val currentRssi = wifiInfo.rssi
        val currentBssid = wifiInfo.bssid ?: return
        val currentTime = System.currentTimeMillis()
        
        // Store RSSI sample
        rssiHistory.add(RssiSample(currentRssi, currentTime))
        if (rssiHistory.size > RSSI_HISTORY_SIZE) {
            rssiHistory.removeAt(0)
        }
        
        // Check for sudden RSSI drop (potential deauth)
        if (lastRssi != 0) {
            val rssiDrop = lastRssi - currentRssi
            if (rssiDrop >= RSSI_DROP_THRESHOLD) {
                addAlert(DeauthAlert(
                    type = AlertType.SUDDEN_SIGNAL_DROP,
                    severity = Severity.HIGH,
                    message = "Sudden signal drop detected: ${rssiDrop}dBm",
                    details = "Previous: ${lastRssi}dBm, Current: ${currentRssi}dBm",
                    bssid = currentBssid,
                    timestamp = currentTime
                ))
            }
        }
        lastRssi = currentRssi
        
        // Track BSSID appearances for spoofing detection
        trackBssidHistory(currentBssid, currentTime)
        
        // Check for rapid reconnections
        checkReconnectionPattern(currentTime)
        
        // Analyze RSSI pattern for jamming
        analyzeRssiPattern()
        
        lastCheckTime = currentTime
    }
    
    /**
     * Track BSSID history for spoofing detection
     */
    private fun trackBssidHistory(bssid: String, timestamp: Long) {
        val history = bssidHistory.getOrPut(bssid) { mutableListOf() }
        history.add(timestamp)
        
        // Clean old entries (older than 1 minute)
        history.removeAll { timestamp - it > 60000 }
    }
    
    /**
     * Check for rapid reconnection patterns
     */
    private fun checkReconnectionPattern(currentTime: Long) {
        // Count disconnects in the last minute
        val recentAlerts = _alerts.value.filter { 
            currentTime - it.timestamp < 60000 && 
            it.type == AlertType.DISCONNECTION
        }
        
        if (recentAlerts.size >= DISCONNECT_THRESHOLD) {
            addAlert(DeauthAlert(
                type = AlertType.DEAUTH_ATTACK_SUSPECTED,
                severity = Severity.CRITICAL,
                message = "⚠️ Possible deauth attack detected!",
                details = "${recentAlerts.size} disconnections in the last minute",
                bssid = "",
                timestamp = currentTime
            ))
        }
    }
    
    /**
     * Analyze RSSI pattern for jamming signals
     */
    private fun analyzeRssiPattern() {
        if (rssiHistory.size < 10) return
        
        val recentSamples = rssiHistory.takeLast(10)
        val avgRssi = recentSamples.map { it.rssi }.average()
        val variance = recentSamples.map { (it.rssi - avgRssi) * (it.rssi - avgRssi) }.average()
        
        // High variance indicates potential jamming
        if (variance > 100) {
            addAlert(DeauthAlert(
                type = AlertType.SIGNAL_INTERFERENCE,
                severity = Severity.MEDIUM,
                message = "Unusual signal fluctuation detected",
                details = "Variance: ${String.format("%.2f", variance)} - Possible interference",
                bssid = "",
                timestamp = System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Manually report disconnection event
     */
    fun reportDisconnection(bssid: String) {
        disconnectCount++
        addAlert(DeauthAlert(
            type = AlertType.DISCONNECTION,
            severity = Severity.LOW,
            message = "WiFi disconnection detected",
            details = "BSSID: $bssid",
            bssid = bssid,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * Check for BSSID spoofing
     */
    @Suppress("DEPRECATION")
    fun checkBssidSpoofing(scanResults: List<WifiNetwork>): List<DeauthAlert> {
        val spoofAlerts = mutableListOf<DeauthAlert>()
        
        // Group by BSSID
        val bssidGroups = scanResults.groupBy { it.bssid }
        
        // Check for same BSSID with different SSIDs
        bssidGroups.forEach { (bssid, networks) ->
            val uniqueSsids = networks.map { it.ssid }.distinct()
            if (uniqueSsids.size > 1) {
                spoofAlerts.add(DeauthAlert(
                    type = AlertType.BSSID_SPOOFING,
                    severity = Severity.HIGH,
                    message = "Potential BSSID spoofing detected",
                    details = "BSSID $bssid seen with multiple SSIDs: ${uniqueSsids.joinToString()}",
                    bssid = bssid,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
        
        // Add to main alerts
        spoofAlerts.forEach { addAlert(it) }
        
        return spoofAlerts
    }
    
    /**
     * Check for Evil Twin attacks
     */
    fun checkEvilTwin(scanResults: List<WifiNetwork>, connectedSsid: String): List<DeauthAlert> {
        val evilTwinAlerts = mutableListOf<DeauthAlert>()
        
        // Find networks with same SSID but different BSSID
        val sameSSID = scanResults.filter { it.ssid == connectedSsid }
        
        if (sameSSID.size > 1) {
            // Multiple APs with same SSID - could be legitimate or evil twin
            val bssids = sameSSID.map { it.bssid }.distinct()
            
            // Check for significantly different signal strengths (suspicious)
            val signals = sameSSID.map { it.signalStrength }
            val maxSignal = signals.maxOrNull() ?: 0
            val minSignal = signals.minOrNull() ?: 0
            
            if (maxSignal - minSignal > 20) {
                evilTwinAlerts.add(DeauthAlert(
                    type = AlertType.EVIL_TWIN_SUSPECTED,
                    severity = Severity.HIGH,
                    message = "⚠️ Potential Evil Twin detected for '$connectedSsid'",
                    details = "${bssids.size} access points found with signal variance of ${maxSignal - minSignal}dBm",
                    bssid = bssids.joinToString(),
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
        
        evilTwinAlerts.forEach { addAlert(it) }
        return evilTwinAlerts
    }
    
    /**
     * Add alert to list
     */
    private fun addAlert(alert: DeauthAlert) {
        val currentAlerts = _alerts.value.toMutableList()
        currentAlerts.add(0, alert) // Add to front
        
        // Keep only last 100 alerts
        if (currentAlerts.size > 100) {
            currentAlerts.removeAt(currentAlerts.size - 1)
        }
        
        _alerts.value = currentAlerts
    }
    
    /**
     * Clear all alerts
     */
    fun clearAlerts() {
        _alerts.value = emptyList()
    }
    
    /**
     * Get alert statistics
     */
    fun getAlertStats(): AlertStats {
        val alerts = _alerts.value
        return AlertStats(
            total = alerts.size,
            critical = alerts.count { it.severity == Severity.CRITICAL },
            high = alerts.count { it.severity == Severity.HIGH },
            medium = alerts.count { it.severity == Severity.MEDIUM },
            low = alerts.count { it.severity == Severity.LOW }
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}

/**
 * RSSI sample for pattern analysis
 */
data class RssiSample(
    val rssi: Int,
    val timestamp: Long
)

/**
 * Deauth alert data class
 */
data class DeauthAlert(
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val details: String,
    val bssid: String,
    val timestamp: Long
)

/**
 * Alert types
 */
enum class AlertType {
    SUDDEN_SIGNAL_DROP,
    DISCONNECTION,
    DEAUTH_ATTACK_SUSPECTED,
    SIGNAL_INTERFERENCE,
    BSSID_SPOOFING,
    EVIL_TWIN_SUSPECTED,
    ROGUE_AP_DETECTED
}

/**
 * Alert severity levels
 */
enum class Severity(val emoji: String, val color: Int) {
    CRITICAL("🔴", 0xFFFF0000.toInt()),
    HIGH("🟠", 0xFFFF6600.toInt()),
    MEDIUM("🟡", 0xFFFFCC00.toInt()),
    LOW("🟢", 0xFF00CC00.toInt())
}

/**
 * Alert statistics
 */
data class AlertStats(
    val total: Int,
    val critical: Int,
    val high: Int,
    val medium: Int,
    val low: Int
)
