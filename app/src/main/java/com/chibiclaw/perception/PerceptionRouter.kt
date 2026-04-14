package com.chibiclaw.perception

import android.util.Log
import com.chibiclaw.perception.accessibility.SemanticDistiller
import com.chibiclaw.perception.accessibility.UiTreeScraper
import com.chibiclaw.perception.vision.GemmaVisionAnalyzer
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerceptionRouter @Inject constructor(
    private val uiTreeScraper: UiTreeScraper,
    private val semanticDistiller: SemanticDistiller,
    private val visionAnalyzer: GemmaVisionAnalyzer
) {
    private val MIN_NODES_THRESHOLD = 10

    fun scan(method: String = "accessibility"): String {
        return when {
            method == "screenshot" -> scanVision()
            uiTreeScraper.isAvailable() -> {
                val root = uiTreeScraper.getRootNode()
                val nodeCount = uiTreeScraper.countInteractiveNodes(root)
                if (nodeCount >= MIN_NODES_THRESHOLD) {
                    Log.d(TAG, "Path A: Accessibility tree ($nodeCount nodes)")
                    semanticDistiller.distill(root)
                } else {
                    Log.d(TAG, "Path A weak ($nodeCount nodes), using vision fallback")
                    scanVision()
                }
            }
            else -> {
                Log.w(TAG, "Accessibility not connected, using vision fallback")
                scanVision()
            }
        }
    }

    private fun scanVision(): String {
        return try {
            runBlocking { visionAnalyzer.analyzeCurrentScreen() }
        } catch (e: Exception) {
            Log.e(TAG, "Vision fallback failed: ${e.message}")
            "[VISION_UNAVAILABLE]"
        }
    }

    companion object {
        private const val TAG = "PerceptionRouter"
    }
}
