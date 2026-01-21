package com.nullsec.wifi.analysis

import com.nullsec.wifi.scanner.WifiNetwork
import com.nullsec.wifi.scanner.SecurityRating
import com.nullsec.wifi.scanner.SecurityType

/**
 * NullSec WiFi Channel Analyzer
 * 
 * Advanced channel analysis and optimization recommendations
 * 
 * @author @AnonAntics
 * @discord discord.gg/killers
 */
class ChannelAnalyzer {

    companion object {
        // 2.4 GHz non-overlapping channels
        val CHANNELS_2_4GHZ_NON_OVERLAP = listOf(1, 6, 11)
        
        // All 2.4 GHz channels
        val CHANNELS_2_4GHZ = (1..14).toList()
        
        // 5 GHz channels (US)
        val CHANNELS_5GHZ = listOf(
            36, 40, 44, 48,     // UNII-1
            52, 56, 60, 64,     // UNII-2A
            100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, // UNII-2C
            149, 153, 157, 161, 165 // UNII-3
        )
        
        // Channel width in MHz
        const val CHANNEL_WIDTH_2_4GHZ = 22
        const val CHANNEL_WIDTH_5GHZ = 20
    }
    
    /**
     * Analyze channel congestion
     */
    fun analyzeChannelCongestion(networks: List<WifiNetwork>): ChannelAnalysis {
        val channelStats = mutableMapOf<Int, ChannelStats>()
        
        // Initialize all channels
        CHANNELS_2_4GHZ.forEach { channelStats[it] = ChannelStats(it, "2.4 GHz") }
        CHANNELS_5GHZ.forEach { channelStats[it] = ChannelStats(it, "5 GHz") }
        
        // Count networks per channel and calculate interference
        networks.forEach { network ->
            val stats = channelStats[network.channel] ?: return@forEach
            stats.networkCount++
            stats.totalSignalStrength += network.signalStrength
            stats.networks.add(network)
            
            // Calculate overlap for 2.4 GHz
            if (network.band == "2.4 GHz") {
                calculateOverlap(network.channel, network.signalStrength, channelStats)
            }
        }
        
        // Calculate congestion scores
        channelStats.values.forEach { stats ->
            stats.congestionScore = calculateCongestionScore(stats)
        }
        
        // Find optimal channels
        val optimal2_4 = findOptimalChannel(channelStats, "2.4 GHz", CHANNELS_2_4GHZ_NON_OVERLAP)
        val optimal5 = findOptimalChannel(channelStats, "5 GHz", CHANNELS_5GHZ)
        
        return ChannelAnalysis(
            channelStats = channelStats,
            optimal2_4GHz = optimal2_4,
            optimal5GHz = optimal5,
            totalNetworks = networks.size,
            networks2_4GHz = networks.count { it.band == "2.4 GHz" },
            networks5GHz = networks.count { it.band == "5 GHz" }
        )
    }
    
    /**
     * Calculate channel overlap interference (2.4 GHz)
     */
    private fun calculateOverlap(channel: Int, signalStrength: Int, stats: MutableMap<Int, ChannelStats>) {
        // 2.4 GHz channels overlap by about 5 channels
        for (i in -2..2) {
            val overlappingChannel = channel + i
            if (overlappingChannel in 1..14 && i != 0) {
                val overlapStats = stats[overlappingChannel] ?: continue
                // Closer channels have more interference
                val overlapFactor = (3 - kotlin.math.abs(i)) / 3.0
                overlapStats.interferenceScore += (kotlin.math.abs(signalStrength) * overlapFactor).toInt()
            }
        }
    }
    
    /**
     * Calculate overall congestion score (0-100, lower is better)
     */
    private fun calculateCongestionScore(stats: ChannelStats): Int {
        if (stats.networkCount == 0) return 0
        
        var score = 0
        
        // Network count factor (each network adds to congestion)
        score += stats.networkCount * 10
        
        // Signal strength factor (stronger signals = more interference)
        val avgSignal = if (stats.networkCount > 0) {
            stats.totalSignalStrength / stats.networkCount
        } else 0
        score += kotlin.math.max(0, (avgSignal + 90) / 2) // Normalize -90 to -30 dBm
        
        // Overlap interference (2.4 GHz only)
        if (stats.band == "2.4 GHz") {
            score += stats.interferenceScore / 10
        }
        
        return kotlin.math.min(100, score)
    }
    
    /**
     * Find optimal channel from candidates
     */
    private fun findOptimalChannel(
        stats: Map<Int, ChannelStats>,
        band: String,
        candidates: List<Int>
    ): Int {
        return candidates
            .filter { stats[it]?.band == band }
            .minByOrNull { stats[it]?.congestionScore ?: Int.MAX_VALUE }
            ?: candidates.first()
    }
    
    /**
     * Get channel recommendations
     */
    fun getRecommendations(analysis: ChannelAnalysis): List<String> {
        val recommendations = mutableListOf<String>()
        
        // 2.4 GHz recommendations
        val best2_4 = analysis.channelStats[analysis.optimal2_4GHz]
        if (best2_4 != null) {
            if (best2_4.congestionScore > 50) {
                recommendations.add("⚠️ 2.4 GHz band is congested. Consider using 5 GHz if possible.")
            } else {
                recommendations.add("✅ Channel ${analysis.optimal2_4GHz} is the least congested 2.4 GHz channel.")
            }
        }
        
        // 5 GHz recommendations
        val best5 = analysis.channelStats[analysis.optimal5GHz]
        if (best5 != null) {
            if (best5.networkCount == 0) {
                recommendations.add("✅ Channel ${analysis.optimal5GHz} (5 GHz) is completely free!")
            } else {
                recommendations.add("📡 Channel ${analysis.optimal5GHz} is optimal for 5 GHz.")
            }
        }
        
        // DFS channel warnings
        val dfsChannels = listOf(52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144)
        if (analysis.optimal5GHz in dfsChannels) {
            recommendations.add("ℹ️ Channel ${analysis.optimal5GHz} uses DFS - may experience brief interruptions.")
        }
        
        // General recommendations
        if (analysis.networks2_4GHz > analysis.networks5GHz * 2) {
            recommendations.add("💡 More networks on 2.4 GHz. 5 GHz offers better performance.")
        }
        
        return recommendations
    }
    
    /**
     * Generate channel usage visualization data
     */
    fun getChannelVisualization(analysis: ChannelAnalysis, band: String): List<ChannelBar> {
        val channels = if (band == "2.4 GHz") CHANNELS_2_4GHZ else CHANNELS_5GHZ
        
        return channels.mapNotNull { channel ->
            val stats = analysis.channelStats[channel] ?: return@mapNotNull null
            ChannelBar(
                channel = channel,
                networkCount = stats.networkCount,
                congestionScore = stats.congestionScore,
                isOptimal = channel == if (band == "2.4 GHz") analysis.optimal2_4GHz else analysis.optimal5GHz
            )
        }
    }
}

/**
 * Channel statistics
 */
data class ChannelStats(
    val channel: Int,
    val band: String,
    var networkCount: Int = 0,
    var totalSignalStrength: Int = 0,
    var interferenceScore: Int = 0,
    var congestionScore: Int = 0,
    val networks: MutableList<WifiNetwork> = mutableListOf()
)

/**
 * Full channel analysis result
 */
data class ChannelAnalysis(
    val channelStats: Map<Int, ChannelStats>,
    val optimal2_4GHz: Int,
    val optimal5GHz: Int,
    val totalNetworks: Int,
    val networks2_4GHz: Int,
    val networks5GHz: Int
)

/**
 * Channel bar for visualization
 */
data class ChannelBar(
    val channel: Int,
    val networkCount: Int,
    val congestionScore: Int,
    val isOptimal: Boolean
)

/**
 * Security analyzer for networks
 */
class SecurityAnalyzer {
    
    /**
     * Analyze network security
     */
    fun analyzeNetworkSecurity(network: WifiNetwork): SecurityReport {
        val issues = mutableListOf<SecurityIssue>()
        val recommendations = mutableListOf<String>()
        var overallScore = 100
        
        // Check security type
        when (network.security) {
            SecurityType.OPEN -> {
                issues.add(SecurityIssue(
                    severity = "CRITICAL",
                    title = "No Encryption",
                    description = "Network has no encryption. All traffic is visible to anyone."
                ))
                recommendations.add("Avoid this network or use a VPN")
                overallScore -= 50
            }
            SecurityType.WEP -> {
                issues.add(SecurityIssue(
                    severity = "CRITICAL", 
                    title = "WEP Encryption",
                    description = "WEP can be cracked in minutes with freely available tools."
                ))
                recommendations.add("Upgrade to WPA2 or WPA3 immediately")
                overallScore -= 45
            }
            SecurityType.WPA -> {
                issues.add(SecurityIssue(
                    severity = "HIGH",
                    title = "Outdated WPA",
                    description = "WPA has known vulnerabilities and should be upgraded."
                ))
                recommendations.add("Upgrade to WPA2-AES or WPA3")
                overallScore -= 30
            }
            SecurityType.WPA2 -> {
                if (network.capabilities.contains("TKIP")) {
                    issues.add(SecurityIssue(
                        severity = "MEDIUM",
                        title = "WPA2-TKIP",
                        description = "TKIP has weaknesses. AES/CCMP is preferred."
                    ))
                    recommendations.add("Switch to WPA2-AES (CCMP) mode")
                    overallScore -= 15
                }
            }
            SecurityType.WPA3 -> {
                // Best security, no issues
            }
            else -> {}
        }
        
        // Check WPS
        if (network.wpsEnabled) {
            issues.add(SecurityIssue(
                severity = "HIGH",
                title = "WPS Enabled",
                description = "WPS PIN can be brute-forced, bypassing password security."
            ))
            recommendations.add("Disable WPS in router settings")
            overallScore -= 20
        }
        
        // Check signal strength (physical security)
        if (network.signalStrength >= -30) {
            issues.add(SecurityIssue(
                severity = "LOW",
                title = "Very Strong Signal",
                description = "Strong signal may extend beyond intended area."
            ))
            recommendations.add("Consider reducing transmit power if this is your network")
            overallScore -= 5
        }
        
        // Determine overall rating
        val rating = when {
            overallScore >= 90 -> "Excellent"
            overallScore >= 70 -> "Good"
            overallScore >= 50 -> "Fair"
            overallScore >= 30 -> "Poor"
            else -> "Critical"
        }
        
        return SecurityReport(
            network = network,
            overallScore = kotlin.math.max(0, overallScore),
            rating = rating,
            issues = issues,
            recommendations = recommendations
        )
    }
    
    /**
     * Get network security summary for all scanned networks
     */
    fun getSecuritySummary(networks: List<WifiNetwork>): SecuritySummary {
        return SecuritySummary(
            totalNetworks = networks.size,
            wpa3Count = networks.count { it.security == SecurityType.WPA3 },
            wpa2Count = networks.count { it.security == SecurityType.WPA2 },
            wpaCount = networks.count { it.security == SecurityType.WPA },
            wepCount = networks.count { it.security == SecurityType.WEP },
            openCount = networks.count { it.security == SecurityType.OPEN },
            wpsEnabled = networks.count { it.wpsEnabled },
            hiddenNetworks = networks.count { it.isHidden }
        )
    }
}

/**
 * Security issue
 */
data class SecurityIssue(
    val severity: String,
    val title: String,
    val description: String
)

/**
 * Security report for a network
 */
data class SecurityReport(
    val network: WifiNetwork,
    val overallScore: Int,
    val rating: String,
    val issues: List<SecurityIssue>,
    val recommendations: List<String>
)

/**
 * Security summary for all networks
 */
data class SecuritySummary(
    val totalNetworks: Int,
    val wpa3Count: Int,
    val wpa2Count: Int,
    val wpaCount: Int,
    val wepCount: Int,
    val openCount: Int,
    val wpsEnabled: Int,
    val hiddenNetworks: Int
)
