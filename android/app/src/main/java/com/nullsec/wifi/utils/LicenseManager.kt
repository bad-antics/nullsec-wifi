package com.nullsec.wifi.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.*

/**
 * NullSec License Manager
 * 
 * Validates premium license keys for NullSec WiFi
 * 
 * Get your premium key at discord.gg/killers
 * 
 * @author @AnonAntics
 * @discord discord.gg/killers
 */
class LicenseManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "nullsec_wifi_license"
        private const val KEY_LICENSE = "license_key"
        private const val KEY_ACTIVATED = "is_activated"
        private const val KEY_EXPIRY = "expiry_date"
        private const val KEY_DEVICE_ID = "device_id"
        
        // License key prefix for validation
        private const val LICENSE_PREFIX = "NSWIFI"
        private const val LICENSE_LENGTH = 29 // NSWIFI-XXXX-XXXX-XXXX-XXXX
    }
    
    /**
     * Check if premium is activated
     */
    fun isPremium(): Boolean {
        val isActivated = prefs.getBoolean(KEY_ACTIVATED, false)
        val expiry = prefs.getLong(KEY_EXPIRY, 0)
        
        // Check if license has expired
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            deactivate()
            return false
        }
        
        return isActivated
    }
    
    /**
     * Activate license with key
     * Returns result with success/failure message
     */
    fun activateLicense(licenseKey: String): LicenseResult {
        val cleanKey = licenseKey.trim().uppercase()
        
        // Basic format validation
        if (!validateKeyFormat(cleanKey)) {
            return LicenseResult(
                success = false,
                message = "Invalid key format. Keys should be: NSWIFI-XXXX-XXXX-XXXX-XXXX"
            )
        }
        
        // Validate checksum
        if (!validateChecksum(cleanKey)) {
            return LicenseResult(
                success = false,
                message = "Invalid license key. Get a valid key at discord.gg/killers"
            )
        }
        
        // Determine license type and expiry
        val licenseType = getLicenseType(cleanKey)
        val expiryDate = calculateExpiry(licenseType)
        
        // Save license
        prefs.edit()
            .putString(KEY_LICENSE, cleanKey)
            .putBoolean(KEY_ACTIVATED, true)
            .putLong(KEY_EXPIRY, expiryDate)
            .putString(KEY_DEVICE_ID, getDeviceId())
            .apply()
        
        return LicenseResult(
            success = true,
            message = "🔓 Premium activated! License: $licenseType",
            licenseType = licenseType,
            expiryDate = if (expiryDate > 0) Date(expiryDate) else null
        )
    }
    
    /**
     * Deactivate license
     */
    fun deactivate() {
        prefs.edit()
            .remove(KEY_LICENSE)
            .putBoolean(KEY_ACTIVATED, false)
            .remove(KEY_EXPIRY)
            .apply()
    }
    
    /**
     * Get current license key
     */
    fun getLicenseKey(): String? {
        return prefs.getString(KEY_LICENSE, null)
    }
    
    /**
     * Get expiry date
     */
    fun getExpiryDate(): Date? {
        val expiry = prefs.getLong(KEY_EXPIRY, 0)
        return if (expiry > 0) Date(expiry) else null
    }
    
    /**
     * Validate key format
     */
    private fun validateKeyFormat(key: String): Boolean {
        if (key.length != LICENSE_LENGTH) return false
        if (!key.startsWith(LICENSE_PREFIX)) return false
        
        // Check format: PREFIX-XXXX-XXXX-XXXX-XXXX
        val pattern = Regex("^$LICENSE_PREFIX-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
        return pattern.matches(key)
    }
    
    /**
     * Validate key checksum
     * Uses last character as checksum
     */
    private fun validateChecksum(key: String): Boolean {
        val keyWithoutChecksum = key.dropLast(1)
        val providedChecksum = key.last()
        
        // Calculate expected checksum
        val hash = MessageDigest.getInstance("MD5")
            .digest(keyWithoutChecksum.toByteArray())
        val expectedChecksum = (hash[0].toInt() and 0xFF) % 36
        val expectedChar = if (expectedChecksum < 10) {
            ('0' + expectedChecksum)
        } else {
            ('A' + expectedChecksum - 10)
        }
        
        return providedChecksum == expectedChar
    }
    
    /**
     * Determine license type from key
     */
    private fun getLicenseType(key: String): String {
        // Second segment indicates type
        val typeSegment = key.split("-")[1]
        
        return when {
            typeSegment.startsWith("LT") -> "Lifetime"
            typeSegment.startsWith("YR") -> "Yearly"
            typeSegment.startsWith("MO") -> "Monthly"
            typeSegment.startsWith("TR") -> "Trial"
            else -> "Standard"
        }
    }
    
    /**
     * Calculate expiry based on license type
     */
    private fun calculateExpiry(licenseType: String): Long {
        val calendar = Calendar.getInstance()
        
        return when (licenseType) {
            "Lifetime" -> 0L // Never expires
            "Yearly" -> {
                calendar.add(Calendar.YEAR, 1)
                calendar.timeInMillis
            }
            "Monthly" -> {
                calendar.add(Calendar.MONTH, 1)
                calendar.timeInMillis
            }
            "Trial" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
                calendar.timeInMillis
            }
            else -> {
                calendar.add(Calendar.YEAR, 1)
                calendar.timeInMillis
            }
        }
    }
    
    /**
     * Get unique device ID
     */
    @Suppress("HardwareIds")
    private fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * Get license status info
     */
    fun getLicenseStatus(): LicenseStatus {
        return LicenseStatus(
            isPremium = isPremium(),
            licenseKey = getLicenseKey()?.let { maskKey(it) },
            expiryDate = getExpiryDate(),
            daysRemaining = getDaysRemaining()
        )
    }
    
    /**
     * Mask license key for display
     */
    private fun maskKey(key: String): String {
        if (key.length < 10) return "****"
        return key.take(10) + "****-****"
    }
    
    /**
     * Get days remaining
     */
    private fun getDaysRemaining(): Int? {
        val expiry = prefs.getLong(KEY_EXPIRY, 0)
        if (expiry == 0L) return null // Lifetime
        
        val remaining = expiry - System.currentTimeMillis()
        return (remaining / (1000 * 60 * 60 * 24)).toInt()
    }
}

/**
 * License activation result
 */
data class LicenseResult(
    val success: Boolean,
    val message: String,
    val licenseType: String? = null,
    val expiryDate: Date? = null
)

/**
 * License status
 */
data class LicenseStatus(
    val isPremium: Boolean,
    val licenseKey: String?,
    val expiryDate: Date?,
    val daysRemaining: Int?
)
