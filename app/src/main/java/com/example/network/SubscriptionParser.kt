package com.example.network

import android.util.Base64
import com.example.data.VpnServerEntity
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses VPN subscription content (plain text or base64) into server entities.
 * Supports: vless://, vmess://, trojan://, ss://, hysteria2://, hy2://
 */
object SubscriptionParser {

    const val DEFAULT_SUB_URL = "https://raw.githubusercontent.com/wearexstack/xstack/main/sub"

    fun parseSubscriptionBody(raw: String): List<VpnServerEntity> {
        val text = decodeIfBase64(raw.trim())
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        return lines.mapIndexedNotNull { index, line ->
            parseNodeLine(line, index)
        }
    }

    private fun decodeIfBase64(input: String): String {
        // If content looks like URI schemes already, use as-is
        if (input.contains("vless://") || input.contains("vmess://") ||
            input.contains("trojan://") || input.contains("hysteria2://") ||
            input.contains("ss://") || input.contains("hy2://")
        ) {
            return input
        }
        return try {
            val decoded = String(Base64.decode(input, Base64.DEFAULT))
            if (decoded.contains("://")) decoded else input
        } catch (_: Exception) {
            input
        }
    }

    private fun parseNodeLine(line: String, index: Int): VpnServerEntity? {
        return when {
            line.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(line, "VLESS", index)
            line.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(line, "Trojan", index)
            line.startsWith("hysteria2://", ignoreCase = true) ||
                line.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(line, index)
            line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line, index)
            line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line, index)
            else -> null
        }
    }

    /** vless://uuid@host:port?params#name  OR trojan://pass@host:port?params#name */
    private fun parseVlessOrTrojan(uri: String, protocol: String, index: Int): VpnServerEntity? {
        return try {
            val withoutScheme = uri.substringAfter("://")
            val namePart = withoutScheme.substringAfter("#", "")
            val mainPart = withoutScheme.substringBefore("#")
            val userAndHost = mainPart.substringBefore("?")
            val query = mainPart.substringAfter("?", "")

            val hostPort = userAndHost.substringAfter("@")
            val host = hostPort.substringBefore(":").ifBlank { hostPort }
            val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443

            val name = decodeFragment(namePart).ifBlank { "$protocol Node ${index + 1}" }
            val params = parseQuery(query)
            val sni = params["sni"] ?: params["host"] ?: host

            VpnServerEntity(
                id = "sub_${protocol.lowercase()}_$index",
                name = name.take(48),
                countryCode = guessCountryCode(name, host, sni),
                countryName = guessCountryName(name, host, sni),
                ipOrDomain = host,
                port = port,
                protocol = protocol,
                latencyMs = (40..150).random(),
                status = "ONLINE",
                userCapacityPercent = (20..70).random(),
                flagEmoji = guessFlag(name, host, sni),
                configRawUrl = uri
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHysteria2(uri: String, index: Int): VpnServerEntity? {
        return try {
            val withoutScheme = uri.substringAfter("://")
            val namePart = withoutScheme.substringAfter("#", "")
            val mainPart = withoutScheme.substringBefore("#")
            val userAndHost = mainPart.substringBefore("?")
            val hostPort = userAndHost.substringAfter("@", userAndHost)
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
            val name = decodeFragment(namePart).ifBlank { "Hysteria2 Node ${index + 1}" }

            VpnServerEntity(
                id = "sub_hy2_$index",
                name = name.take(48),
                countryCode = guessCountryCode(name, host, host),
                countryName = guessCountryName(name, host, host),
                ipOrDomain = host,
                port = port,
                protocol = "Hysteria2",
                latencyMs = (30..120).random(),
                status = "ONLINE",
                userCapacityPercent = (20..60).random(),
                flagEmoji = guessFlag(name, host, host),
                configRawUrl = uri
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVmess(uri: String, index: Int): VpnServerEntity? {
        return try {
            val b64 = uri.substringAfter("vmess://")
            val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            // Minimal parse without full JSON lib dependency issues
            val add = extractJsonString(json, "add") ?: return null
            val port = extractJsonString(json, "port")?.toIntOrNull() ?: 443
            val ps = extractJsonString(json, "ps") ?: "VMess Node ${index + 1}"
            val host = extractJsonString(json, "host") ?: add

            VpnServerEntity(
                id = "sub_vmess_$index",
                name = ps.take(48),
                countryCode = guessCountryCode(ps, add, host),
                countryName = guessCountryName(ps, add, host),
                ipOrDomain = add,
                port = port,
                protocol = "VMess",
                latencyMs = (40..140).random(),
                status = "ONLINE",
                userCapacityPercent = (25..65).random(),
                flagEmoji = guessFlag(ps, add, host),
                configRawUrl = uri
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(uri: String, index: Int): VpnServerEntity? {
        return try {
            val withoutScheme = uri.substringAfter("ss://")
            val namePart = withoutScheme.substringAfter("#", "")
            val main = withoutScheme.substringBefore("#")
            // ss://base64@host:port or ss://method:pass@host:port
            val hostPort = if (main.contains("@")) main.substringAfter("@") else main
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
            val name = decodeFragment(namePart).ifBlank { "SS Node ${index + 1}" }

            VpnServerEntity(
                id = "sub_ss_$index",
                name = name.take(48),
                countryCode = guessCountryCode(name, host, host),
                countryName = guessCountryName(name, host, host),
                ipOrDomain = host,
                port = port,
                protocol = "Shadowsocks",
                latencyMs = (40..130).random(),
                status = "ONLINE",
                userCapacityPercent = (20..55).random(),
                flagEmoji = guessFlag(name, host, host),
                configRawUrl = uri
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val k = pair.substringBefore("=")
            val v = pair.substringAfter("=", "")
            if (k.isBlank()) null else k to URLDecoder.decode(v, "UTF-8")
        }.toMap()
    }

    private fun decodeFragment(frag: String): String {
        if (frag.isBlank()) return ""
        return try {
            URLDecoder.decode(frag, "UTF-8")
        } catch (_: Exception) {
            frag
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val patterns = listOf(
            "\"$key\"\\s*:\\s*\"([^\"]*)\"",
            "\"$key\"\\s*:\\s*([0-9]+)"
        )
        for (p in patterns) {
            val m = Regex(p).find(json)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun guessCountryCode(name: String, host: String, sni: String): String {
        val t = "$name $host $sni".lowercase()
        return when {
            t.contains("de") || t.contains("germany") || t.contains("frankfurt") -> "DE"
            t.contains("nl") || t.contains("netherlands") || t.contains("amsterdam") -> "NL"
            t.contains("us") || t.contains("america") || t.contains("newyork") -> "US"
            t.contains("uk") || t.contains("london") || t.contains("britain") -> "GB"
            t.contains("fi") || t.contains("finland") || t.contains("helsinki") -> "FI"
            t.contains("sg") || t.contains("singapore") -> "SG"
            t.contains("jp") || t.contains("japan") || t.contains("tokyo") -> "JP"
            t.contains("ch") || t.contains("swiss") || t.contains("zurich") -> "CH"
            t.contains("fr") || t.contains("france") || t.contains("paris") -> "FR"
            t.contains("tr") || t.contains("turkey") || t.contains("istanbul") -> "TR"
            t.contains("ir") || t.contains("iran") -> "IR"
            t.contains("no") || t.contains("norway") -> "NO"
            else -> "UN"
        }
    }

    private fun guessCountryName(name: String, host: String, sni: String): String {
        return when (guessCountryCode(name, host, sni)) {
            "DE" -> "Germany"
            "NL" -> "Netherlands"
            "US" -> "United States"
            "GB" -> "United Kingdom"
            "FI" -> "Finland"
            "SG" -> "Singapore"
            "JP" -> "Japan"
            "CH" -> "Switzerland"
            "FR" -> "France"
            "TR" -> "Turkey"
            "IR" -> "Iran"
            "NO" -> "Norway"
            else -> "International"
        }
    }

    private fun guessFlag(name: String, host: String, sni: String): String {
        return when (guessCountryCode(name, host, sni)) {
            "DE" -> "🇩🇪"
            "NL" -> "🇳🇱"
            "US" -> "🇺🇸"
            "GB" -> "🇬🇧"
            "FI" -> "🇫🇮"
            "SG" -> "🇸🇬"
            "JP" -> "🇯🇵"
            "CH" -> "🇨🇭"
            "FR" -> "🇫🇷"
            "TR" -> "🇹🇷"
            "IR" -> "🇮🇷"
            "NO" -> "🇳🇴"
            else -> "🌐"
        }
    }
}
