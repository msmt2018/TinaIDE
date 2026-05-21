package com.wuxianggujun.tinaide.ui.compose.components.markdown

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Mermaid 图表渲染组件
 *
 * 使用 WebView + mermaid.js CDN 渲染，支持深/浅色主题自动切换。
 * 通过 JSInterface 获取内容高度并动态调整 WebView 大小。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MermaidDiagram(
    code: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val bgColor = colorScheme.surface.toArgb()
    val textColor = colorScheme.onSurface.toArgb()
    val primaryColor = colorScheme.primary.toArgb()
    val secondaryColor = colorScheme.secondary.toArgb()
    val surfaceColor = colorScheme.surfaceVariant.toArgb()
    val borderColor = colorScheme.outlineVariant.toArgb()

    var contentHeight by remember { mutableIntStateOf(300) }

    val html = remember(code, isDark) {
        buildMermaidHtml(
            code = code,
            isDark = isDark,
            bgColor = bgColor,
            textColor = textColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(bgColor)

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onHeightReady(height: Int) {
                                contentHeight = height
                            }
                        },
                        "AndroidBridge",
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var el = document.getElementById('mermaid-output');
                                    if (el) {
                                        var h = el.scrollHeight || el.offsetHeight || 300;
                                        AndroidBridge.onHeightReady(h);
                                    }
                                })();
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { contentHeight.toDp() }.coerceIn(100.dp, 600.dp)),
            update = { webView ->
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            },
        )
    }
}

// ── HTML 模板 ──────────────────────────────────────────────

private fun buildMermaidHtml(
    code: String,
    isDark: Boolean,
    bgColor: Int,
    textColor: Int,
    primaryColor: Int,
    secondaryColor: Int,
    surfaceColor: Int,
    borderColor: Int,
): String {
    val theme = if (isDark) "dark" else "default"
    val bgHex = String.format("#%06X", 0xFFFFFF and bgColor)
    val textHex = String.format("#%06X", 0xFFFFFF and textColor)
    val primaryHex = String.format("#%06X", 0xFFFFFF and primaryColor)
    val secondaryHex = String.format("#%06X", 0xFFFFFF and secondaryColor)
    val surfaceHex = String.format("#%06X", 0xFFFFFF and surfaceColor)
    val borderHex = String.format("#%06X", 0xFFFFFF and borderColor)

    // 转义 HTML 特殊字符
    val escapedCode = code
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <style>
                body {
                    margin: 0;
                    padding: 8px;
                    background: $bgHex;
                    color: $textHex;
                    overflow-x: auto;
                }
                #mermaid-output {
                    display: flex;
                    justify-content: center;
                }
                #mermaid-output svg {
                    max-width: 100%;
                    height: auto;
                }
                .error {
                    color: #f44336;
                    font-family: monospace;
                    font-size: 12px;
                    padding: 8px;
                }
            </style>
        </head>
        <body>
            <div id="mermaid-output">
                <pre class="mermaid">$escapedCode</pre>
            </div>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
            <script>
                mermaid.initialize({
                    startOnLoad: true,
                    theme: '$theme',
                    themeVariables: {
                        primaryColor: '$primaryHex',
                        secondaryColor: '$secondaryHex',
                        tertiaryColor: '$surfaceHex',
                        primaryTextColor: '$textHex',
                        secondaryTextColor: '$textHex',
                        lineColor: '$borderHex',
                        mainBkg: '$surfaceHex',
                        nodeBorder: '$borderHex',
                        fontSize: '14px'
                    },
                    securityLevel: 'strict',
                    fontFamily: 'sans-serif'
                });

                mermaid.run().then(function() {
                    setTimeout(function() {
                        var el = document.getElementById('mermaid-output');
                        if (el) {
                            var h = el.scrollHeight || el.offsetHeight || 300;
                            AndroidBridge.onHeightReady(h);
                        }
                    }, 100);
                }).catch(function(err) {
                    document.getElementById('mermaid-output').innerHTML =
                        '<div class="error">Mermaid render error: ' + err.message + '</div>';
                    AndroidBridge.onHeightReady(60);
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
