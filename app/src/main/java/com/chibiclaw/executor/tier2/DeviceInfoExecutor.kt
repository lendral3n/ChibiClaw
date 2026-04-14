package com.chibiclaw.executor.tier2

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Device info queries: identity, cpu, network, sensors, display, all.
 */
@Singleton
class DeviceInfoExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun perform(target: String): String {
        val t = target.trim().lowercase()
        return try {
            when (t) {
                "identity", "device", "hp", "info" -> getDeviceIdentity()
                "cpu", "processor", "chipset" -> getCpuInfo()
                "gpu" -> getGpuInfo()
                "network", "network_info", "jaringan" -> getNetworkInfo()
                "sensors", "sensor" -> getSensorList()
                "display", "layar", "screen" -> getDisplayInfo()
                "memory", "ram" -> getRamInfo()
                "storage", "penyimpanan" -> getStorageInfo()
                "os", "android", "system" -> getOsInfo()
                "all", "semua", "lengkap" -> getAllInfo()
                else -> "device_info_error: unknown target '$t'"
            }
        } catch (e: Exception) {
            "device_info_error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceIdentity(): String {
        return "Brand: ${Build.BRAND} | Model: ${Build.MODEL} | Device: ${Build.DEVICE} | " +
            "Manufacturer: ${Build.MANUFACTURER} | Product: ${Build.PRODUCT} | " +
            "Hardware: ${Build.HARDWARE} | Board: ${Build.BOARD}"
    }

    private fun getCpuInfo(): String {
        val parts = mutableListOf<String>()
        parts += "SoC: ${Build.HARDWARE}"
        parts += "ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}"
        // Read /proc/cpuinfo for details
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            val model = Regex("Hardware\\s*:\\s*(.+)").find(cpuInfo)?.groupValues?.get(1)
            if (model != null) parts += "Hardware: $model"
            val cores = Regex("processor\\s*:").findAll(cpuInfo).count()
            parts += "Cores: $cores"
            // Read max freq
            try {
                val maxFreq = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim()
                parts += "Max Freq: ${maxFreq.toLongOrNull()?.div(1000) ?: maxFreq} MHz"
            } catch (_: Exception) {}
        } catch (_: Exception) {
            parts += "Cores: ${Runtime.getRuntime().availableProcessors()}"
        }
        return parts.joinToString(" | ")
    }

    private fun getGpuInfo(): String {
        // GPU info is limited without OpenGL context
        return "GPU: ${Build.HARDWARE} (Adreno — query via OpenGL for detailed info) | ABI: ${Build.SUPPORTED_ABIS[0]}"
    }

    @SuppressLint("MissingPermission")
    private fun getNetworkInfo(): String {
        val parts = mutableListOf<String>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
        parts += "Connection: $type"
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.connectionInfo?.let { info ->
                parts += "SSID: ${info.ssid?.replace("\"", "")}"
                parts += "Signal: ${info.rssi}dBm"
                parts += "Speed: ${info.linkSpeed}Mbps"
            }
        }
        parts += "Down: ${caps?.linkDownstreamBandwidthKbps ?: 0}Kbps"
        parts += "Up: ${caps?.linkUpstreamBandwidthKbps ?: 0}Kbps"
        parts += "VPN: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true}"
        return parts.joinToString(" | ")
    }

    private fun getSensorList(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val sb = StringBuilder("[sensors] ${sensors.size} total\n")
        sensors.forEach { s ->
            sb.append("• ${s.name} (${s.stringType}) — vendor: ${s.vendor}, power: ${s.power}mA\n")
        }
        return sb.toString()
    }

    private fun getDisplayInfo(): String {
        val dm = context.resources.displayMetrics
        return "Resolution: ${dm.widthPixels}x${dm.heightPixels} | " +
            "DPI: ${dm.densityDpi} | " +
            "Density: ${dm.density} | " +
            "Font Scale: ${context.resources.configuration.fontScale}"
    }

    private fun getRamInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGB = memInfo.totalMem / (1024.0 * 1024 * 1024)
        val availGB = memInfo.availMem / (1024.0 * 1024 * 1024)
        val usedGB = totalGB - availGB
        val pct = ((usedGB / totalGB) * 100).toInt()
        return "RAM: ${f(usedGB)}/${f(totalGB)} GB ($pct% used) | Low Memory: ${memInfo.lowMemory}"
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes / (1024.0 * 1024 * 1024)
        val free = stat.availableBytes / (1024.0 * 1024 * 1024)
        val used = total - free
        return "Storage: ${f(used)}/${f(total)} GB (${f(free)} GB free)"
    }

    private fun getOsInfo(): String {
        return "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
            "Security Patch: ${Build.VERSION.SECURITY_PATCH} | " +
            "Build: ${Build.DISPLAY} | " +
            "Bootloader: ${Build.BOOTLOADER} | " +
            "Radio: ${Build.getRadioVersion() ?: "N/A"} | " +
            "Kernel: ${System.getProperty("os.version") ?: "N/A"}"
    }

    private fun getAllInfo(): String {
        return listOf(
            getDeviceIdentity(),
            getOsInfo(),
            getCpuInfo(),
            getRamInfo(),
            getStorageInfo(),
            getDisplayInfo(),
            getNetworkInfo()
        ).joinToString("\n")
    }

    private fun f(gb: Double) = String.format("%.1f", gb)
}
