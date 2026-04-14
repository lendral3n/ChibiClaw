package com.chibiclaw.core

import com.chibiclaw.executor.ChibiAction
import com.chibiclaw.executor.IntentAction
import com.chibiclaw.executor.LaunchAppAction
import com.chibiclaw.executor.SetAlarmAction
import com.chibiclaw.executor.SystemControlAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side regex interceptor for common single-action commands.
 *
 * Matches BEFORE Gemma inference so frequent simple commands don't depend on
 * the LLM's tool-calling reliability. Returns `null` for anything that doesn't
 * match, which falls through to the full orchestrator pipeline.
 *
 * This exists because Gemma 3n E2B in LiteRT-LM 0.10.0 sometimes emits literal
 * `<|tool_call|>` markers as text instead of structured tool calls, which means
 * even simple "buka X" commands silently fail — no tool ever runs, the user
 * just sees a bubble saying "Selesai." while nothing actually happened. By
 * intercepting deterministically here we guarantee these commands always work
 * regardless of model behaviour.
 *
 * Multi-step commands (containing "lalu", "kemudian", …) and commands with a
 * secondary action verb after the app name (e.g. "buka youtube cari ikan") are
 * intentionally NOT matched — those still need Gemma's reasoning to chain
 * multiple tools.
 */
@Singleton
class FastPathMatcher @Inject constructor() {

    data class Result(val action: ChibiAction, val friendly: String)

    fun match(command: String): Result? {
        val c = command.trim().lowercase()
        if (c.isEmpty()) return null

        // 1. Flashlight / senter — checked BEFORE "open app" because
        //    "buka senter" would otherwise be routed to AppLauncher.
        if (FLASH_ON.containsMatchIn(c)) {
            return Result(SystemControlAction("flashlight", "on"), "Senter dinyalakan.")
        }
        if (FLASH_OFF.containsMatchIn(c)) {
            return Result(SystemControlAction("flashlight", "off"), "Senter dimatikan.")
        }

        // 2. Multi-step markers → defer to Gemma.
        if (MULTI_STEP_MARKERS.any { c.contains(it) }) return null

        // 3. Open / launch app.
        val openMatch = OPEN_APP_PREFIX.find(c)
        if (openMatch != null) {
            var raw = c.substring(openMatch.range.last + 1).trim()
            raw = raw.removePrefix("app ").removePrefix("aplikasi ").trim()
            if (raw.isNotEmpty() && raw.length in 1..40) {
                // Secondary verb implies multi-step ("buka youtube cari ikan")
                // — let Gemma handle it.
                val secondary = SECONDARY_VERBS.any { v ->
                    raw == v || raw.startsWith("$v ") || raw.contains(" $v ") || raw.endsWith(" $v")
                }
                if (!secondary) {
                    return Result(
                        LaunchAppAction(appName = raw),
                        "Membuka $raw..."
                    )
                }
            }
        }

        // 4. WiFi settings.
        if (WIFI_MARK.containsMatchIn(c)) {
            return Result(
                IntentAction(action = "android.settings.WIFI_SETTINGS", uri = ""),
                "Membuka pengaturan Wi-Fi..."
            )
        }

        // 5. Bluetooth settings.
        if (BT_MARK.containsMatchIn(c)) {
            return Result(
                IntentAction(action = "android.settings.BLUETOOTH_SETTINGS", uri = ""),
                "Membuka pengaturan Bluetooth..."
            )
        }

        // 6. Alarm "HH:MM"
        ALARM_HM.find(c)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            if (h in 0..23 && min in 0..59) {
                return Result(
                    SetAlarmAction(hour = h, minute = min),
                    "Alarm disetel ${"%02d:%02d".format(h, min)}."
                )
            }
        }

        // 7. Alarm "jam H"
        ALARM_H.find(c)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            if (h in 0..23) {
                return Result(
                    SetAlarmAction(hour = h, minute = 0),
                    "Alarm disetel ${"%02d:00".format(h)}."
                )
            }
        }

        // 8. Volume numeric — "volume 30", "set volume ke 50", "volume 0"
        //    BUG-E: E2B model keeps misreading the number ("Volume 30" →
        //    SystemControlAction(state=0)) and also truncates digits in
        //    the free-text response. FastPath captures the raw digits
        //    before Gemma ever sees the command so the ground truth
        //    never gets lost in tokenisation.
        VOLUME_NUM.find(c)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            if (n in 0..100) {
                return Result(
                    SystemControlAction("volume", n.toString()),
                    "Volume diatur ke $n."
                )
            }
        }
        if (VOLUME_UP.containsMatchIn(c)) {
            return Result(
                SystemControlAction("volume", "up"),
                "Volume dinaikkan."
            )
        }
        if (VOLUME_DOWN.containsMatchIn(c)) {
            return Result(
                SystemControlAction("volume", "down"),
                "Volume diturunkan."
            )
        }
        if (VOLUME_MUTE.containsMatchIn(c)) {
            return Result(
                SystemControlAction("volume", "0"),
                "Volume dibisukan."
            )
        }

        // 9. Brightness numeric — "brightness 50", "cerahkan layar 80",
        //    "atur kecerahan 30". Same rationale as volume: deterministic
        //    digits beat LLM tool-call roulette.
        BRIGHTNESS_NUM.find(c)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            if (n in 0..100) {
                return Result(
                    SystemControlAction("brightness", n.toString()),
                    "Kecerahan diatur ke $n."
                )
            }
        }

        return null
    }

    companion object {
        private val MULTI_STEP_MARKERS = listOf(
            " lalu ", " kemudian ", " setelah itu ", " terus ",
            " then ", " after ", " and then ",
            " sambil ", " sekaligus "
        )

        private val SECONDARY_VERBS = listOf(
            "cari", "search", "find",
            "mainkan", "play",
            "tulis", "ketik", "type",
            "kirim", "send",
            "posting", "post"
        )

        private val FLASH_ON = Regex(
            """(?:nyalakan|aktifkan|hidupkan|turn\s*on|buka)\s+(?:senter|flashlight|lampu)"""
        )
        private val FLASH_OFF = Regex(
            """(?:matikan|padamkan|turn\s*off)\s+(?:senter|flashlight|lampu)"""
        )

        private val OPEN_APP_PREFIX = Regex("""^(buka|open|launch|jalankan|mulai)\b""")

        private val WIFI_MARK = Regex(
            """(?:buka|setting|pengaturan|aktifkan|nyalakan|matikan).{0,12}(?:wifi|wi-fi)"""
        )
        private val BT_MARK = Regex(
            """(?:buka|setting|pengaturan|aktifkan|nyalakan|matikan).{0,12}bluetooth"""
        )

        private val ALARM_HM = Regex("""alarm\D*?(\d{1,2})[:.](\d{1,2})""")
        private val ALARM_H = Regex("""alarm\D*?jam\s+(\d{1,2})""")

        // BUG-E: deterministic volume/brightness capture. We accept any
        // phrasing that ends with "<keyword> <digits>" OR "<keyword> ke
        // <digits>". Up to 3 digits to allow 0–100 without false-positive
        // matching on large numbers.
        private val VOLUME_NUM = Regex(
            """(?:volume|suara|vol)\D{0,8}(\d{1,3})"""
        )
        private val VOLUME_UP = Regex(
            """(?:naikkan|perbesar|besarkan|tambah)\s+(?:volume|suara)|volume\s+(?:up|naik|keras)"""
        )
        private val VOLUME_DOWN = Regex(
            """(?:turunkan|perkecil|kurangi|kecilkan)\s+(?:volume|suara)|volume\s+(?:down|turun|pelan)"""
        )
        private val VOLUME_MUTE = Regex(
            """(?:mute|bisukan|matikan\s+suara|silent)"""
        )

        private val BRIGHTNESS_NUM = Regex(
            """(?:brightness|kecerahan|cerahkan|cerah)\D{0,8}(\d{1,3})"""
        )
    }
}
