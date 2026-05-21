package com.wuxianggujun.tinaide.core.network

import java.util.Locale

object NetworkErrorClassifier {
    fun looksLikeNetworkError(output: String): Boolean {
        val lower = output.lowercase(Locale.ROOT)
        return (
            output.contains("198.18.") ||
                output.contains("198.19.") ||
                (output.contains("127.0.0.1") && lower.contains("archive.ubuntu.com")) ||
                lower.contains("connection timed out") ||
                lower.contains("unable to connect") ||
                lower.contains("could not connect") ||
                lower.contains("could not resolve") ||
                lower.contains("temporary failure") ||
                lower.contains("name or service not known") ||
                (lower.contains("ssl") && lower.contains("error")) ||
                (lower.contains("certificate") && lower.contains("error")) ||
                lower.contains("connection reset") ||
                lower.contains("connection refused")
            )
    }
}

