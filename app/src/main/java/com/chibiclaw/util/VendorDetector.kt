package com.chibiclaw.util

import android.os.Build

/**
 * Detect manufacturer device + per-OEM guidance (autostart, battery exception, dll).
 *
 * Tujuan: setup wizard kasih instruksi spesifik per vendor supaya ChibiService
 * tidak dimatikan oleh battery saver / autostart killer.
 *
 * Referensi: docs/research/07-floating-overlay-android.md (vendor death matrix).
 */
object VendorDetector {

    fun current(): Vendor {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> Vendor.XIAOMI
            manufacturer.contains("oppo") -> Vendor.OPPO
            manufacturer.contains("vivo") -> Vendor.VIVO
            manufacturer.contains("realme") -> Vendor.REALME
            manufacturer.contains("oneplus") -> Vendor.ONEPLUS
            manufacturer.contains("samsung") -> Vendor.SAMSUNG
            manufacturer.contains("honor") -> Vendor.HONOR
            manufacturer.contains("huawei") -> Vendor.HUAWEI
            manufacturer.contains("tecno") -> Vendor.TECNO
            manufacturer.contains("infinix") -> Vendor.INFINIX
            manufacturer.contains("nothing") -> Vendor.NOTHING
            manufacturer.contains("google") || brand.contains("pixel") -> Vendor.PIXEL
            else -> Vendor.UNKNOWN
        }
    }

    fun guidanceFor(vendor: Vendor): VendorGuidance = when (vendor) {
        Vendor.XIAOMI -> VendorGuidance(
            displayName = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
            steps = listOf(
                "Buka Settings → Apps → ChibiClaw",
                "Battery saver → No restrictions",
                "Autostart → ON",
                "Display pop-up windows while running in background → ON",
                "Other permissions → Display over other apps → ON",
                "Tahan ChibiClaw di Recent Apps → kunci (padlock icon)",
            ),
            killAggressiveness = KillLevel.VERY_HIGH,
        )
        Vendor.OPPO -> VendorGuidance(
            displayName = "Oppo (ColorOS)",
            steps = listOf(
                "Settings → Battery → Power saving for apps → ChibiClaw → No restriction",
                "Settings → Apps → Special permissions → Autostart → ChibiClaw → ON",
                "Settings → Apps → ChibiClaw → Battery usage → Allow background activity",
            ),
            killAggressiveness = KillLevel.HIGH,
        )
        Vendor.VIVO -> VendorGuidance(
            displayName = "Vivo (OriginOS / FuntouchOS)",
            steps = listOf(
                "Settings → Battery → Background app refresh → ChibiClaw → Allow",
                "Settings → More settings → Permission manager → Autostart → ChibiClaw → ON",
                "iManager → Background app management → ChibiClaw → Allow high background CPU usage",
            ),
            killAggressiveness = KillLevel.HIGH,
        )
        Vendor.REALME -> VendorGuidance(
            displayName = "Realme (realmeUI)",
            steps = listOf(
                "Settings → Battery → Power saving for apps → ChibiClaw → No restriction",
                "Settings → Apps → ChibiClaw → Battery usage → Allow background activity",
                "Settings → Apps → Special permissions → Autostart → ChibiClaw → ON",
            ),
            killAggressiveness = KillLevel.HIGH,
        )
        Vendor.ONEPLUS -> VendorGuidance(
            displayName = "OnePlus (OxygenOS)",
            steps = listOf(
                "Settings → Battery → Battery optimization → ChibiClaw → Don't optimize",
                "Settings → Apps → ChibiClaw → Battery → Background activity → Allow",
            ),
            killAggressiveness = KillLevel.MEDIUM,
        )
        Vendor.SAMSUNG -> VendorGuidance(
            displayName = "Samsung (One UI)",
            steps = listOf(
                "Settings → Apps → ChibiClaw → Battery → Unrestricted",
                "Settings → Device care → Battery → Background usage limits → Never sleeping apps → tambah ChibiClaw",
                "Settings → Apps → ChibiClaw → Mobile data → Allow background data usage",
            ),
            killAggressiveness = KillLevel.MEDIUM,
        )
        Vendor.HONOR, Vendor.HUAWEI -> VendorGuidance(
            displayName = if (vendor == Vendor.HONOR) "Honor (MagicOS)" else "Huawei (EMUI)",
            steps = listOf(
                "Settings → Apps → ChibiClaw → Battery usage → Manual",
                "Enable: Auto-launch, Secondary launch, Run in background",
                "Settings → Apps → ChibiClaw → Battery → No restriction",
            ),
            killAggressiveness = KillLevel.VERY_HIGH,
        )
        Vendor.TECNO, Vendor.INFINIX -> VendorGuidance(
            displayName = if (vendor == Vendor.TECNO) "Tecno (HiOS)" else "Infinix (XOS)",
            steps = listOf(
                "Settings → Battery → Smart Power Saving → ChibiClaw → No restriction",
                "Settings → Apps → ChibiClaw → App info → Autostart → ON",
            ),
            killAggressiveness = KillLevel.HIGH,
        )
        Vendor.NOTHING -> VendorGuidance(
            displayName = "Nothing Phone (Nothing OS)",
            steps = listOf(
                "Settings → Apps → ChibiClaw → Battery → Unrestricted",
                "Settings → Apps → ChibiClaw → Notifications → Allow",
            ),
            killAggressiveness = KillLevel.LOW,
        )
        Vendor.PIXEL -> VendorGuidance(
            displayName = "Google Pixel (stock Android)",
            steps = listOf(
                "Settings → Apps → ChibiClaw → Battery → Unrestricted",
                "Standard Android Doze + App Standby; ChibiClaw FGS akan tetap jalan",
            ),
            killAggressiveness = KillLevel.LOW,
        )
        Vendor.UNKNOWN -> VendorGuidance(
            displayName = "Vendor tidak terdeteksi (generic Android)",
            steps = listOf(
                "Settings → Apps → ChibiClaw → Battery → Unrestricted / Don't optimize",
                "Pastikan SYSTEM_ALERT_WINDOW (display over other apps) di-grant",
                "Disable battery optimization untuk ChibiClaw kalau ada toggle",
            ),
            killAggressiveness = KillLevel.MEDIUM,
        )
    }
}

enum class Vendor {
    XIAOMI, OPPO, VIVO, REALME, ONEPLUS, SAMSUNG,
    HONOR, HUAWEI, TECNO, INFINIX, NOTHING, PIXEL,
    UNKNOWN,
}

data class VendorGuidance(
    val displayName: String,
    val steps: List<String>,
    val killAggressiveness: KillLevel,
)

enum class KillLevel { LOW, MEDIUM, HIGH, VERY_HIGH }
