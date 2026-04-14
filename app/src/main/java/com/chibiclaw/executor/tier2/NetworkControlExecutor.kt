package com.chibiclaw.executor.tier2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.chibiclaw.executor.tier4.DeepSettings
import com.chibiclaw.executor.tier4.ShizukuExecutor
import com.chibiclaw.executor.ShizukuAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Network control: WiFi details, mobile data, hotspot, airplane mode, DNS, VPN status, data usage.
 */
@Singleton
class NetworkControlExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepSettings: DeepSettings,
    private val shizukuExecutor: ShizukuExecutor
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun perform(target: String, command: String, value: String): String {
        val t = target.trim().lowercase()
        val cmd = command.trim().lowercase()
        return when (t) {
            "wifi_info", "wifi_detail", "wifi_status" -> getWifiInfo()
            "mobile_data", "data", "seluler" -> handleMobileData(cmd)
            "hotspot", "tethering", "portable_hotspot" -> handleHotspot(cmd)
            "airplane", "airplane_mode", "flight_mode" -> handleAirplaneMode(cmd)
            "dns", "private_dns" -> handleDns(cmd, value)
            "vpn", "vpn_status" -> getVpnStatus()
            "data_usage" -> getDataUsage()
            "network_info", "connection" -> getNetworkInfo()
            else -> "network_error: unknown target '$t'"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getWifiInfo(): String {
        val wifi = wifiManager ?: return "wifi_error: WifiManager unavailable"
        if (!wifi.isWifiEnabled) return "wifi: OFF (not connected)"
        return try {
            val info = wifi.connectionInfo
            val dhcp = wifi.dhcpInfo
            val ssid = info.ssid?.replace("\"", "") ?: "unknown"
            val bssid = info.bssid ?: "unknown"
            val rssi = info.rssi
            val linkSpeed = info.linkSpeed
            val freq = info.frequency
            val ip = dhcp?.let {
                "${it.ipAddress and 0xff}.${it.ipAddress shr 8 and 0xff}.${it.ipAddress shr 16 and 0xff}.${it.ipAddress shr 24 and 0xff}"
            } ?: "unknown"
            val gateway = dhcp?.let {
                "${it.gateway and 0xff}.${it.gateway shr 8 and 0xff}.${it.gateway shr 16 and 0xff}.${it.gateway shr 24 and 0xff}"
            } ?: "unknown"
            val dns1 = dhcp?.let {
                "${it.dns1 and 0xff}.${it.dns1 shr 8 and 0xff}.${it.dns1 shr 16 and 0xff}.${it.dns1 shr 24 and 0xff}"
            } ?: "unknown"
            "WiFi: ON | SSID: $ssid | BSSID: $bssid | Signal: ${rssi}dBm | Speed: ${linkSpeed}Mbps | Freq: ${freq}MHz | IP: $ip | Gateway: $gateway | DNS: $dns1"
        } catch (e: Exception) {
            "wifi_error: ${e.message}"
        }
    }

    private suspend fun handleMobileData(cmd: String): String {
        return when (cmd) {
            "on" -> shizukuExecutor.execute("svc data enable")
            "off" -> shizukuExecutor.execute("svc data disable")
            "get", "status" -> {
                val network = connectivityManager.activeNetwork
                val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
                val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                "mobile_data: ${if (hasCellular) "ON (connected)" else "OFF or not connected"}"
            }
            "toggle" -> shizukuExecutor.execute("svc data enable && sleep 0.5 && svc data disable || svc data enable")
            else -> "data_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private suspend fun handleHotspot(cmd: String): String {
        return when (cmd) {
            "on" -> {
                try {
                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "hotspot_info: opened wireless settings — toggle hotspot manually. Shizuku: svc wifi enablesofthap"
                } catch (e: Exception) {
                    "hotspot_error: ${e.message}"
                }
            }
            "off" -> shizukuExecutor.execute("cmd wifi stop-softap")
            "get", "status" -> {
                val result = shizukuExecutor.execute("dumpsys wifi | grep -i softap")
                "hotspot: $result"
            }
            else -> "hotspot_error: unknown command '$cmd'"
        }
    }

    private suspend fun handleAirplaneMode(cmd: String): String {
        return when (cmd) {
            "on" -> {
                val r = deepSettings.setAirplaneMode(true)
                // Also broadcast the change so the system reacts
                shizukuExecutor.execute("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true")
                r
            }
            "off" -> {
                val r = deepSettings.setAirplaneMode(false)
                shizukuExecutor.execute("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false")
                r
            }
            "get", "status" -> {
                val enabled = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
                "airplane_mode: ${if (enabled == 1) "ON" else "OFF"}"
            }
            "toggle" -> {
                val current = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
                handleAirplaneMode(if (current == 1) "off" else "on")
            }
            else -> "airplane_error: unknown command '$cmd'"
        }
    }

    private suspend fun handleDns(cmd: String, value: String): String {
        return when (cmd) {
            "set" -> {
                if (value.isBlank()) return "dns_error: provide DNS server (e.g. dns.google, 1dot1dot1dot1.cloudflare-dns.com)"
                deepSettings.putRaw("global", "private_dns_mode", "hostname")
                deepSettings.putRaw("global", "private_dns_specifier", value)
            }
            "auto" -> deepSettings.putRaw("global", "private_dns_mode", "opportunistic")
            "off" -> deepSettings.putRaw("global", "private_dns_mode", "off")
            "get", "status" -> {
                val mode = deepSettings.getGlobal("private_dns_mode")
                val spec = deepSettings.getGlobal("private_dns_specifier")
                "dns: mode=$mode server=$spec"
            }
            else -> "dns_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private fun getVpnStatus(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            "vpn: ${if (hasVpn) "CONNECTED" else "NOT connected"}"
        } catch (e: Exception) {
            "vpn_error: ${e.message}"
        }
    }

    private fun getDataUsage(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val type = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                else -> "None"
            }
            val downKbps = caps?.linkDownstreamBandwidthKbps ?: 0
            val upKbps = caps?.linkUpstreamBandwidthKbps ?: 0
            "network: type=$type | down=${downKbps}Kbps | up=${upKbps}Kbps"
        } catch (e: Exception) {
            "data_usage_error: ${e.message}"
        }
    }

    private fun getNetworkInfo(): String {
        val parts = mutableListOf<String>()
        // WiFi
        val wifi = wifiManager
        if (wifi != null) {
            parts += "WiFi: ${if (wifi.isWifiEnabled) "ON" else "OFF"}"
            if (wifi.isWifiEnabled) {
                try {
                    val ssid = wifi.connectionInfo.ssid?.replace("\"", "") ?: "?"
                    parts += "SSID: $ssid"
                } catch (_: Exception) {}
            }
        }
        // Active network
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        parts += "Active: $type"
        parts += "VPN: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true}"
        // Airplane
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        parts += "Airplane: ${airplane == 1}"
        return parts.joinToString(" | ")
    }

    companion object {
        private const val TAG = "NetworkControlExecutor"
    }
}
