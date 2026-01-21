package com.nullsec.wifi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nullsec.wifi.fragments.*
import com.nullsec.wifi.utils.LicenseManager

/**
 * NullSec WiFi - Advanced Wireless Security Analyzer
 * 
 * Main Activity handling navigation and permissions
 * 
 * @author @AnonAntics
 * @website https://github.com/bad-antics
 * @discord discord.gg/killers
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var licenseManager: LicenseManager
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initializeApp()
        } else {
            Toast.makeText(
                this,
                "📡 Location permission required for WiFi scanning",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        licenseManager = LicenseManager(this)
        
        if (hasPermissions()) {
            initializeApp()
        } else {
            requestPermissions()
        }
    }
    
    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }
    
    private fun initializeApp() {
        setupBottomNavigation()
        loadFragment(ScannerFragment())
        
        // Show premium status
        if (licenseManager.isPremium()) {
            Toast.makeText(this, "🔓 Premium features unlocked!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "🔑 Get premium at discord.gg/killers", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_navigation)
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scanner -> {
                    loadFragment(ScannerFragment())
                    true
                }
                R.id.nav_channels -> {
                    loadFragment(ChannelFragment())
                    true
                }
                R.id.nav_analysis -> {
                    loadFragment(AnalysisFragment())
                    true
                }
                R.id.nav_history -> {
                    if (licenseManager.isPremium()) {
                        loadFragment(HistoryFragment())
                    } else {
                        showPremiumRequired("Signal History")
                    }
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun showPremiumRequired(feature: String) {
        Toast.makeText(
            this,
            "🔒 $feature requires premium!\n🔑 discord.gg/killers",
            Toast.LENGTH_LONG
        ).show()
    }
    
    fun isPremium(): Boolean = licenseManager.isPremium()
    
    fun getLicenseManager(): LicenseManager = licenseManager
}
