package com.chibiclaw.ai.llm.webview

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Universal cloud login WebView untuk Claude / GPT / vendor lain.
 *
 * Pattern: load `loginUrl`, listen URL/redirect ke `successUrlPrefix`. Saat
 * match, inject JS bridge yang fetch session data (via fetch API ke endpoint
 * mundane provider seperti /api/auth/session atau /api/organizations) lalu
 * panggil `window.ChibiBridge.onExtracted(json)`.
 *
 * Hasil di-route ke `onExtracted` callback — caller (Setup wizard) panggil
 * SessionExtractor.extractAndPersistClaude/GPT.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudLoginWebView(
    loginUrl: String,
    successUrlPrefix: String,
    extractScript: String,
    onExtracted: (jsonPayload: String) -> Unit,
    onCancel: () -> Unit,
) {
    var statusText by remember { mutableStateOf("Loading…") }
    var awaitingExtract by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = settings.userAgentString
                        ?.replace("; wv", "") // un-mask WebView signature

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onExtracted(payload: String) {
                            awaitingExtract = false
                            onExtracted(payload)
                        }

                        @JavascriptInterface
                        fun onError(message: String) {
                            statusText = "❌ Extract gagal: ${message.take(100)}"
                        }
                    }, "ChibiBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            statusText = if (url?.startsWith(successUrlPrefix) == true) {
                                if (awaitingExtract) {
                                    view?.evaluateJavascript(extractScript, null)
                                    "Extracting session…"
                                } else "✅ Selesai"
                            } else {
                                "Login dulu di tab ini"
                            }
                        }
                    }
                    loadUrl(loginUrl)
                }
            },
        )

        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Batal")
            }
        }
    }
}

object CloudLoginScripts {

    /**
     * Claude.ai extract script — fetch /api/organizations, get clientSha dari
     * window-level config, get deviceId dari localStorage. Format payload:
     * {"orgId":"...","userId":"...","activeConvId":"...","clientSha":"...",
     *  "clientVersion":"...","deviceId":"...","userAgent":"..."}
     */
    const val CLAUDE_EXTRACT = """
        (async () => {
          try {
            const ua = navigator.userAgent;
            const orgsResp = await fetch('/api/organizations', {credentials:'include'});
            const orgs = await orgsResp.json();
            if (!Array.isArray(orgs) || orgs.length === 0) {
              window.ChibiBridge.onError('no orgs in /api/organizations');
              return;
            }
            const org = orgs[0];
            const orgId = org.uuid || org.id || '';
            const userId = (org.capabilities && org.capabilities.user_id) || '';
            const convsResp = await fetch(`/api/organizations/${'$'}{orgId}/chat_conversations`, {credentials:'include'});
            const convs = await convsResp.json();
            const activeConvId = Array.isArray(convs) && convs.length > 0 ? (convs[0].uuid || convs[0].id || '') : '';
            const meta = document.querySelector('meta[name="anthropic-client-version"]');
            const clientVersion = meta ? meta.content : 'unknown';
            const clientSha = (window.__ANTHROPIC_CLIENT_SHA__) || 'unknown';
            const deviceId = localStorage.getItem('anthropic-device-id') || '';
            const payload = JSON.stringify({orgId, userId, activeConvId, clientSha, clientVersion, deviceId, userAgent: ua});
            window.ChibiBridge.onExtracted(payload);
          } catch (e) {
            window.ChibiBridge.onError(String(e));
          }
        })();
    """

    /**
     * ChatGPT extract — fetch /api/auth/session, ambil accessToken + user.id.
     */
    const val GPT_EXTRACT = """
        (async () => {
          try {
            const ua = navigator.userAgent;
            const resp = await fetch('/api/auth/session', {credentials:'include'});
            const data = await resp.json();
            if (!data || !data.accessToken) {
              window.ChibiBridge.onError('no accessToken in /api/auth/session');
              return;
            }
            const userId = (data.user && (data.user.id || data.user.sub)) || '';
            const payload = JSON.stringify({userId, accessToken: data.accessToken, conversationId: null, userAgent: ua});
            window.ChibiBridge.onExtracted(payload);
          } catch (e) {
            window.ChibiBridge.onError(String(e));
          }
        })();
    """
}
