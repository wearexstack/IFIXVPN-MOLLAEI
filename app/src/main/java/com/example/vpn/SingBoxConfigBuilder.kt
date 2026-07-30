package com.example.vpn

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Builds a sing-box JSON config from share-link URIs (vless/trojan/hysteria2/vmess/ss).
 * The TUN inbound lets Android VpnService feed traffic into sing-box / compatible cores.
 */
object SingBoxConfigBuilder {

    fun buildFromShareLink(rawUri: String, serverName: String = "proxy"): String {
        val uri = rawUri.trim()
        val outbound = when {
            uri.startsWith("vless://", true) -> buildVlessOutbound(uri)
            uri.startsWith("trojan://", true) -> buildTrojanOutbound(uri)
            uri.startsWith("hysteria2://", true) || uri.startsWith("hy2://", true) ->
                buildHysteria2Outbound(uri)
            uri.startsWith("vmess://", true) -> buildVmessOutbound(uri)
            uri.startsWith("ss://", true) -> buildShadowsocksOutbound(uri)
            else -> throw IllegalArgumentException("پروتکل پشتیبانی نمی‌شود. لینک vless/trojan/hy2/vmess/ss لازم است.")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("level", "info").put("timestamp", true))

        // DNS
        root.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(JSONObject().put("tag", "dns-remote").put("address", "8.8.8.8").put("detour", "proxy"))
                        .put(JSONObject().put("tag", "dns-local").put("address", "local").put("detour", "direct"))
                )
                .put("strategy", "prefer_ipv4")
        )

        // Inbounds: mixed local + tun for system VPN
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("type", "tun")
                .put("tag", "tun-in")
                .put("interface_name", "ifix-tun")
                .put("address", JSONArray().put("172.19.0.1/30"))
                .put("mtu", 9000)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("stack", "system")
                .put("sniff", true)
                .put("sniff_override_destination", true)
        )
        inbounds.put(
            JSONObject()
                .put("type", "mixed")
                .put("tag", "mixed-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", 2080)
                .put("sniff", true)
        )
        root.put("inbounds", inbounds)

        outbound.put("tag", "proxy")
        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "block").put("tag", "block"))
            .put(JSONObject().put("type", "dns").put("tag", "dns-out"))
        root.put("outbounds", outbounds)

        root.put(
            "route",
            JSONObject()
                .put(
                    "rules",
                    JSONArray()
                        .put(JSONObject().put("protocol", "dns").put("outbound", "dns-out"))
                        .put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))
                )
                .put("final", "proxy")
                .put("auto_detect_interface", true)
        )

        return root.toString(2)
    }

    /** Xray-compatible full config (for cores that expect Xray JSON). */
    fun buildXrayConfigFromShareLink(rawUri: String): String {
        val uri = rawUri.trim()
        val outbound = when {
            uri.startsWith("vless://", true) -> xrayVless(uri)
            uri.startsWith("trojan://", true) -> xrayTrojan(uri)
            uri.startsWith("vmess://", true) -> xrayVmess(uri)
            uri.startsWith("ss://", true) -> xraySs(uri)
            else -> {
                // Fall back: try parse as vless-like or throw
                throw IllegalArgumentException("برای هسته Xray فعلاً vless/trojan/vmess/ss پشتیبانی می‌شود.")
            }
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "socks")
                    .put("port", 10808)
                    .put("listen", "127.0.0.1")
                    .put("protocol", "socks")
                    .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
                    .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))
            )
        )
        root.put(
            "outbounds",
            JSONArray()
                .put(outbound)
                .put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
                .put(JSONObject().put("protocol", "blackhole").put("tag", "block"))
        )
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put("ip", JSONArray().put("geoip:private"))
                            .put("outboundTag", "direct")
                    )
                )
        )
        return root.toString(2)
    }

    // ── sing-box outbounds ───────────────────────────────────────────────────

    private fun buildVlessOutbound(uri: String): JSONObject {
        val parsed = parseUserHostUri(uri)
        val uuid = parsed.user
        val host = parsed.host
        val port = parsed.port
        val q = parsed.query
        val security = q["security"] ?: "none"
        val network = q["type"] ?: "tcp"
        val sni = q["sni"] ?: q["host"] ?: host
        val flow = q["flow"] ?: ""
        val fp = q["fp"] ?: "chrome"
        val pbk = q["pbk"] ?: ""
        val sid = q["sid"] ?: ""

        val outbound = JSONObject()
            .put("type", "vless")
            .put("server", host)
            .put("server_port", port)
            .put("uuid", uuid)
            .put("packet_encoding", "xudp")

        if (flow.isNotBlank()) outbound.put("flow", flow)

        when (security) {
            "reality" -> {
                outbound.put(
                    "tls",
                    JSONObject()
                        .put("enabled", true)
                        .put("server_name", sni)
                        .put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
                        .put(
                            "reality",
                            JSONObject()
                                .put("enabled", true)
                                .put("public_key", pbk)
                                .put("short_id", sid)
                        )
                )
            }
            "tls" -> {
                outbound.put(
                    "tls",
                    JSONObject()
                        .put("enabled", true)
                        .put("server_name", sni)
                        .put("insecure", q["allowInsecure"] == "1" || q["insecure"] == "1")
                )
            }
        }

        when (network) {
            "ws" -> {
                outbound.put(
                    "transport",
                    JSONObject()
                        .put("type", "ws")
                        .put("path", q["path"] ?: "/")
                        .put("headers", JSONObject().put("Host", q["host"] ?: sni))
                )
            }
            "grpc" -> {
                outbound.put(
                    "transport",
                    JSONObject()
                        .put("type", "grpc")
                        .put("service_name", q["serviceName"] ?: q["service_name"] ?: "")
                )
            }
        }
        return outbound
    }

    private fun buildTrojanOutbound(uri: String): JSONObject {
        val parsed = parseUserHostUri(uri)
        val q = parsed.query
        val sni = q["sni"] ?: q["host"] ?: parsed.host
        val outbound = JSONObject()
            .put("type", "trojan")
            .put("server", parsed.host)
            .put("server_port", parsed.port)
            .put("password", parsed.user)
            .put(
                "tls",
                JSONObject()
                    .put("enabled", true)
                    .put("server_name", sni)
                    .put("insecure", q["allowInsecure"] == "1")
            )
        val network = q["type"] ?: "tcp"
        if (network == "ws") {
            outbound.put(
                "transport",
                JSONObject()
                    .put("type", "ws")
                    .put("path", q["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", q["host"] ?: sni))
            )
        }
        return outbound
    }

    private fun buildHysteria2Outbound(uri: String): JSONObject {
        val parsed = parseUserHostUri(uri)
        val q = parsed.query
        val sni = q["sni"] ?: parsed.host
        return JSONObject()
            .put("type", "hysteria2")
            .put("server", parsed.host)
            .put("server_port", parsed.port)
            .put("password", parsed.user)
            .put(
                "tls",
                JSONObject()
                    .put("enabled", true)
                    .put("server_name", sni)
                    .put("insecure", q["insecure"] == "1")
            )
    }

    private fun buildVmessOutbound(uri: String): JSONObject {
        val b64 = uri.substringAfter("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
        val obj = JSONObject(json)
        val host = obj.optString("add")
        val port = obj.optString("port", "443").toIntOrNull() ?: 443
        val uuid = obj.optString("id")
        val aid = obj.optString("aid", "0")
        val net = obj.optString("net", "tcp")
        val tls = obj.optString("tls", "")
        val sni = obj.optString("sni", obj.optString("host", host))
        val path = obj.optString("path", "/")

        val outbound = JSONObject()
            .put("type", "vmess")
            .put("server", host)
            .put("server_port", port)
            .put("uuid", uuid)
            .put("security", obj.optString("scy", "auto"))
            .put("alter_id", aid.toIntOrNull() ?: 0)

        if (tls == "tls") {
            outbound.put("tls", JSONObject().put("enabled", true).put("server_name", sni))
        }
        if (net == "ws") {
            outbound.put(
                "transport",
                JSONObject()
                    .put("type", "ws")
                    .put("path", path)
                    .put("headers", JSONObject().put("Host", obj.optString("host", sni)))
            )
        }
        return outbound
    }

    private fun buildShadowsocksOutbound(uri: String): JSONObject {
        val withoutScheme = uri.substringAfter("ss://")
        val main = withoutScheme.substringBefore("#")
        val hostPort: String
        val method: String
        val password: String
        if (main.contains("@")) {
            val userInfo = main.substringBefore("@")
            hostPort = main.substringAfter("@")
            val decoded = try {
                String(Base64.decode(userInfo, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            } catch (_: Exception) {
                userInfo
            }
            method = decoded.substringBefore(":")
            password = decoded.substringAfter(":")
        } else {
            val decoded = String(Base64.decode(main, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            method = decoded.substringBefore(":")
            val rest = decoded.substringAfter(":")
            password = rest.substringBeforeLast("@")
            hostPort = rest.substringAfterLast("@")
        }
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
        return JSONObject()
            .put("type", "shadowsocks")
            .put("server", host)
            .put("server_port", port)
            .put("method", method)
            .put("password", password)
    }

    // ── Xray outbounds ───────────────────────────────────────────────────────

    private fun xrayVless(uri: String): JSONObject {
        val p = parseUserHostUri(uri)
        val q = p.query
        val security = q["security"] ?: "none"
        val network = q["type"] ?: "tcp"
        val sni = q["sni"] ?: q["host"] ?: p.host

        val user = JSONObject()
            .put("id", p.user)
            .put("encryption", "none")
            .put("flow", q["flow"] ?: "")

        val stream = JSONObject().put("network", network)
        when (security) {
            "tls", "reality" -> {
                stream.put("security", security)
                val tlsSettings = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", q["fp"] ?: "chrome")
                    .put("allowInsecure", q["allowInsecure"] == "1")
                if (security == "reality") {
                    tlsSettings.put(
                        "realitySettings",
                        JSONObject()
                            .put("publicKey", q["pbk"] ?: "")
                            .put("shortId", q["sid"] ?: "")
                    )
                }
                stream.put("tlsSettings", tlsSettings)
            }
            else -> stream.put("security", "none")
        }
        if (network == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", q["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", q["host"] ?: sni))
            )
        }
        if (network == "grpc") {
            stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", q["serviceName"] ?: "")
            )
        }

        return JSONObject()
            .put("protocol", "vless")
            .put("tag", "proxy")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", p.host)
                            .put("port", p.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream)
    }

    private fun xrayTrojan(uri: String): JSONObject {
        val p = parseUserHostUri(uri)
        val q = p.query
        val sni = q["sni"] ?: p.host
        return JSONObject()
            .put("protocol", "trojan")
            .put("tag", "proxy")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", p.host)
                            .put("port", p.port)
                            .put("password", p.user)
                    )
                )
            )
            .put(
                "streamSettings",
                JSONObject()
                    .put("network", q["type"] ?: "tcp")
                    .put("security", "tls")
                    .put("tlsSettings", JSONObject().put("serverName", sni))
            )
    }

    private fun xrayVmess(uri: String): JSONObject {
        val b64 = uri.substringAfter("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
        val obj = JSONObject(json)
        val host = obj.optString("add")
        val port = obj.optString("port", "443").toIntOrNull() ?: 443
        val stream = JSONObject()
            .put("network", obj.optString("net", "tcp"))
            .put("security", if (obj.optString("tls") == "tls") "tls" else "none")
        if (obj.optString("tls") == "tls") {
            stream.put("tlsSettings", JSONObject().put("serverName", obj.optString("sni", host)))
        }
        return JSONObject()
            .put("protocol", "vmess")
            .put("tag", "proxy")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put(
                                "users",
                                JSONArray().put(
                                    JSONObject()
                                        .put("id", obj.optString("id"))
                                        .put("alterId", obj.optString("aid", "0").toIntOrNull() ?: 0)
                                        .put("security", obj.optString("scy", "auto"))
                                )
                            )
                    )
                )
            )
            .put("streamSettings", stream)
    }

    private fun xraySs(uri: String): JSONObject {
        val withoutScheme = uri.substringAfter("ss://")
        val main = withoutScheme.substringBefore("#")
        val hostPort: String
        val method: String
        val password: String
        if (main.contains("@")) {
            val userInfo = main.substringBefore("@")
            hostPort = main.substringAfter("@")
            val decoded = try {
                String(Base64.decode(userInfo, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            } catch (_: Exception) {
                userInfo
            }
            method = decoded.substringBefore(":")
            password = decoded.substringAfter(":")
        } else {
            val decoded = String(Base64.decode(main, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            method = decoded.substringBefore(":")
            val rest = decoded.substringAfter(":")
            password = rest.substringBeforeLast("@")
            hostPort = rest.substringAfterLast("@")
        }
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
        return JSONObject()
            .put("protocol", "shadowsocks")
            .put("tag", "proxy")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("method", method)
                            .put("password", password)
                    )
                )
            )
    }

    private data class ParsedUri(
        val user: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>
    )

    private fun parseUserHostUri(uri: String): ParsedUri {
        val withoutScheme = uri.substringAfter("://")
        val main = withoutScheme.substringBefore("#")
        val userAndHost = main.substringBefore("?")
        val queryStr = main.substringAfter("?", "")
        val user = URLDecoder.decode(userAndHost.substringBefore("@"), "UTF-8")
        val hostPort = userAndHost.substringAfter("@")
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
        val query = if (queryStr.isBlank()) emptyMap() else {
            queryStr.split("&").mapNotNull { pair ->
                val k = pair.substringBefore("=")
                val v = URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8")
                if (k.isBlank()) null else k to v
            }.toMap()
        }
        return ParsedUri(user, host, port, query)
    }
}
