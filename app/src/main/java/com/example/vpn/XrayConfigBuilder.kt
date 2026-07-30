package com.example.vpn

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Builds valid Xray-core JSON (vless/trojan/vmess/ss) with TUN inbound for libv2ray StartLoop(fd).
 */
object XrayConfigBuilder {

    fun build(rawUri: String): String {
        val uri = rawUri.trim()
        val outbound = when {
            uri.startsWith("vless://", true) -> vless(uri)
            uri.startsWith("trojan://", true) -> trojan(uri)
            uri.startsWith("vmess://", true) -> vmess(uri)
            uri.startsWith("ss://", true) -> shadowsocks(uri)
            else -> throw IllegalArgumentException("پروتکل پشتیبانی نمی‌شود (vless/trojan/vmess/ss)")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // Stats for QueryStats
        root.put("stats", JSONObject())
        root.put(
            "policy",
            JSONObject().put(
                "system",
                JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)
            )
        )

        // TUN inbound – fd supplied by libv2ray via env xray.tun.fd
        val tunInbound = JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put(
                "settings",
                JSONObject()
                    .put("mtu", 1500)
                    .put("name", "ifix0")
            )
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    .put("routeOnly", true)
            )

        val socksInbound = JSONObject()
            .put("tag", "socks-in")
            .put("port", 10808)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls"))
            )

        root.put("inbounds", JSONArray().put(tunInbound).put(socksInbound))
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
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "field")
                                .put("ip", JSONArray().put("geoip:private"))
                                .put("outboundTag", "direct")
                        )
                )
        )
        return root.toString(2)
    }

    private fun vless(uri: String): JSONObject {
        val p = parse(uri)
        val q = p.query
        val security = (q["security"] ?: "none").lowercase()
        val network = (q["type"] ?: "tcp").lowercase()
        val sni = q["sni"] ?: q["host"] ?: p.host

        val user = JSONObject()
            .put("id", p.user)
            .put("encryption", "none")
        val flow = q["flow"].orEmpty()
        if (flow.isNotBlank()) user.put("flow", flow)

        val stream = JSONObject().put("network", network)

        when (security) {
            "reality" -> {
                stream.put("security", "reality")
                stream.put(
                    "realitySettings",
                    JSONObject()
                        .put("serverName", sni)
                        .put("fingerprint", q["fp"] ?: "chrome")
                        .put("publicKey", q["pbk"] ?: "")
                        .put("shortId", q["sid"] ?: "")
                        .put("spiderX", q["spx"] ?: "")
                )
            }
            "tls" -> {
                stream.put("security", "tls")
                stream.put(
                    "tlsSettings",
                    JSONObject()
                        .put("serverName", sni)
                        .put("allowInsecure", q["allowInsecure"] == "1" || q["insecure"] == "1")
                        .put("fingerprint", q["fp"] ?: "chrome")
                )
            }
            else -> stream.put("security", "none")
        }

        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", q["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", q["host"] ?: sni))
            )
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", q["serviceName"] ?: q["service_name"] ?: "")
            )
            "tcp" -> {
                val headerType = q["headerType"] ?: q["header"]
                if (headerType == "http") {
                    stream.put(
                        "tcpSettings",
                        JSONObject().put(
                            "header",
                            JSONObject().put("type", "http")
                        )
                    )
                }
            }
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

    private fun trojan(uri: String): JSONObject {
        val p = parse(uri)
        val q = p.query
        val sni = q["sni"] ?: q["host"] ?: p.host
        val network = (q["type"] ?: "tcp").lowercase()
        val stream = JSONObject()
            .put("network", network)
            .put("security", "tls")
            .put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", sni)
                    .put("allowInsecure", q["allowInsecure"] == "1")
            )
        if (network == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", q["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", q["host"] ?: sni))
            )
        }
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
            .put("streamSettings", stream)
    }

    private fun vmess(uri: String): JSONObject {
        val b64 = uri.substringAfter("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
        val obj = JSONObject(json)
        val host = obj.optString("add")
        val port = obj.optString("port", "443").toIntOrNull() ?: 443
        val net = obj.optString("net", "tcp")
        val tls = obj.optString("tls", "")
        val sni = obj.optString("sni", obj.optString("host", host))
        val stream = JSONObject().put("network", net)
        if (tls == "tls") {
            stream.put("security", "tls")
            stream.put("tlsSettings", JSONObject().put("serverName", sni))
        } else {
            stream.put("security", "none")
        }
        if (net == "ws") {
            stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", obj.optString("path", "/"))
                    .put("headers", JSONObject().put("Host", obj.optString("host", sni)))
            )
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

    private fun shadowsocks(uri: String): JSONObject {
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

    private data class Parsed(val user: String, val host: String, val port: Int, val query: Map<String, String>)

    private fun parse(uri: String): Parsed {
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
        return Parsed(user, host, port, query)
    }
}
