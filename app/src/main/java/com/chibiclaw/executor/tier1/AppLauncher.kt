package com.chibiclaw.executor.tier1

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a human-readable app name (e.g. "TikTok", "Honor of Kings",
 * "YouTube Music") into a real installed package name and launches it.
 *
 * This is the safe alternative to Gemma guessing package strings — the model
 * keeps hallucinating fakes like `com.zhilove.android.tiktok`. Instead of
 * trusting Gemma, we query PackageManager directly for every launcher-visible
 * app on the device and fuzzy-match on the label.
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm: PackageManager = context.packageManager

    /**
     * Look up installed apps and return the best match for [query]. Launches
     * the matched app via its launch intent and returns a status string.
     */
    fun launchByName(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return "launch_error: empty app name"

        // If caller already passed a package-shaped string, try that first.
        if (trimmed.contains('.') && !trimmed.contains(' ')) {
            launchByPackage(trimmed)?.let { return it }
        }

        val candidates = listLauncherApps()
        val match = findBestMatch(trimmed, candidates)
            ?: return "launch_error: app '$trimmed' not installed. " +
                "Pilihan terdekat: ${candidates.take(5).joinToString { it.label }}"

        return launchByPackage(match.packageName)
            ?: "launch_error: cannot launch ${match.packageName}"
    }

    /** Direct package launch, returns null if package isn't installed/launchable. */
    fun launchByPackage(packageName: String): String? {
        val launch = pm.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            Log.d(TAG, "Launched $packageName")
            "launch_success: $packageName"
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed for $packageName: ${e.message}")
            "launch_error: ${e.message}"
        }
    }

    data class AppEntry(val label: String, val packageName: String)

    /** Every app with a launcher icon on the device. */
    fun listLauncherApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> =
            pm.queryIntentActivities(intent, 0)
        return resolved.map { ri ->
            AppEntry(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
    }

    /**
     * Scoring-based fuzzy resolver. The previous implementation iterated
     * candidates with `firstOrNull`, which caused BUG-H: "YouTube Music"
     * was losing to plain "YouTube" because the sorted PackageManager
     * result hit "YouTube" first and `"youtubemusic".contains("youtube")`
     * is already true. A proper fix needs to score EVERY candidate and
     * return the strongest match — longest label wins.
     *
     * Scoring table (higher = better):
     *   1000                 → exact label match (space/case insensitive)
     *   500 + label.length   → label contains query (query is a prefix/substring)
     *   400 + label.length   → query contains label (label length ≥ 3)
     *   300 + common.length  → token overlap fallback
     *   50                   → package name contains query
     *
     * The `+ label.length` bias is the key — both "YouTube" and "YouTube
     * Music" match "youtubemusic", but YouTube Music has a longer label
     * so its score wins. Works symmetrically for "spotify" vs "spotify
     * lite" and "chrome" vs "chrome canary".
     */
    private fun findBestMatch(query: String, apps: List<AppEntry>): AppEntry? {
        val q = query.lowercase().replace(" ", "")
        if (q.isEmpty()) return null

        data class Scored(val entry: AppEntry, val score: Int)

        val scored = apps.mapNotNull { app ->
            val label = app.label.lowercase().replace(" ", "")
            val pkg = app.packageName.lowercase()

            val s: Int = when {
                label == q -> 1000
                q.isNotEmpty() && label.contains(q) -> 500 + label.length
                label.length >= 3 && q.contains(label) -> 400 + label.length
                pkg.contains(q) -> 50
                else -> 0
            }
            if (s > 0) Scored(app, s) else null
        }

        if (scored.isEmpty()) return null

        // maxByOrNull returns any one of the top candidates on a tie,
        // which is fine — ties are usually identical labels from
        // work-profile clones anyway.
        val best = scored.maxByOrNull { it.score }
        Log.d(TAG, "Match \"$query\" → ${best?.entry?.label} (score=${best?.score})")
        return best?.entry
    }

    companion object {
        private const val TAG = "AppLauncher"
    }
}
