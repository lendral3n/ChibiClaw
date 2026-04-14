package com.chibiclaw.executor.tier3

import android.util.Log
import com.chibiclaw.perception.accessibility.SemanticDistiller
import com.chibiclaw.perception.accessibility.UiTreeScraper
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerificationLoop @Inject constructor(
    private val uiTreeScraper: UiTreeScraper,
    private val semanticDistiller: SemanticDistiller
) {
    suspend fun verify(expectedChange: String, delayMs: Long = 800): String {
        delay(delayMs) // wait for UI to settle
        val root = uiTreeScraper.getRootNode()
        val uiMap = semanticDistiller.distill(root)
        Log.d(TAG, "Post-action UI: $uiMap")
        return uiMap
    }

    companion object {
        private const val TAG = "VerificationLoop"
    }
}
