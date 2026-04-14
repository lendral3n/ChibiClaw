package com.chibiclaw.executor.tier1

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkResolver @Inject constructor() {

    fun resolve(appHint: String, data: String): String {
        val prefix = IntentRegistry.knownDeepLinks[appHint.lowercase()]
        return if (prefix != null) "$prefix$data" else data
    }

    fun getPackage(appHint: String): String? =
        IntentRegistry.packageNames[appHint.lowercase()]
}
