package com.github.cheremsha.decrypt.crypt.app.parser

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

data class VpnConfig(
    val protocol: String,
    val remarks: String,
    val endpoint: String,
    val rawLink: String
)

object VpnConfigParser {

    private val LINK_REGEX = Regex("""(?:vless|vmess|trojan|ss|ssr)://[^\s"'<>\\]+""")

    fun parse(raw: String): List<VpnConfig> {
        val cleaned = if ("<html" in raw.lowercase())
            raw.replace(Regex("<[^>]+>"), " ").trim()
        else raw

        return when {
            isJson(cleaned) -> fromJson(cleaned) + extractDirect(cleaned)
            else -> {
                val decoded = tryBase64(cleaned)
                when {
                    decoded != null && isJson(decoded) -> fromJson(decoded)
                    else -> extractDirect(decoded ?: cleaned)
                }
            }
        }.deduplicate()
    }

    private fun fromJson(json: String): List<VpnConfig> {
        val items: List<JSONObject> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            try { listOf(JSONObject(json)) } catch (_: Exception) { return emptyList() }
        }

        val links = mutableListOf<String>()
        for (item in items) {
            val remarks = item.optString("remarks", "Config")
            val outbounds = item.optJSONArray("outbounds") ?: continue
            for (i in 0 until outbounds.length()) {
                val out = outbounds.optJSONObject(i) ?: continue
                val proto = out.optString("protocol", "")
                if (proto in listOf("freedom", "blackhole", "dns")) continue
                val stream  = out.optJSONObject("streamSettings") ?: JSONObject()
                val net     = stream.optString("network", "tcp")
                val sec     = stream.optString("security", "none")
                val sets    = out.optJSONObject("settings") ?: JSONObject()
                val enc     = URLEncoder.encode(remarks, "UTF-8")

                when (proto) {
                    "vless" -> {
                        val vnext = sets.optJSONArray("vnext") ?: continue
                        val srv   = vnext.getJSONObject(0)
                        val user  = srv.getJSONArray("users").getJSONObject(0)
                        val flow  = user.optString("flow", "")
                        val params = buildString {
                            append("encryption=none&type=$net&security=$sec")
                            if (flow.isNotBlank()) append("&flow=$flow")
                        }
                        links += "vless://${user.getString("id")}@${srv.getString("address")}:${srv.getInt("port")}?$params#$enc"
                    }
                    "vmess" -> {
                        val vnext = sets.optJSONArray("vnext") ?: continue
                        val srv   = vnext.getJSONObject(0)
                        val user  = srv.getJSONArray("users").getJSONObject(0)
                        val obj   = JSONObject().apply {
                            put("v","2"); put("ps",remarks)
                            put("add", srv.getString("address"))
                            put("port", srv.getInt("port").toString())
                            put("id",  user.getString("id"))
                            put("aid", user.optInt("alterId",0).toString())
                            put("net", net); put("tls", sec); put("type","none")
                        }
                        links += "vmess://${Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)}"
                    }
                    "trojan" -> {
                        val srv = sets.optJSONArray("servers")?.getJSONObject(0) ?: continue
                        links += "trojan://${srv.getString("password")}@${srv.getString("address")}:${srv.getInt("port")}#$enc"
                    }
                    "shadowsocks" -> {
                        val srv = sets.optJSONArray("servers")?.getJSONObject(0) ?: continue
                        val b64 = Base64.encodeToString(
                            "${srv.getString("method")}:${srv.getString("password")}".toByteArray(),
                            Base64.NO_WRAP
                        )
                        links += "ss://$b64@${srv.getString("address")}:${srv.getInt("port")}#$enc"
                    }
                }
            }
        }
        return links.mapNotNull { parseForDisplay(it) }
    }

    private fun extractDirect(text: String): List<VpnConfig> {
        return LINK_REGEX.findAll(text).mapNotNull { parseForDisplay(it.value) }.toList()
    }

    fun parseForDisplay(rawLink: String): VpnConfig? = try {
        val protocol = when {
            rawLink.startsWith("vless://")  -> "VLESS"
            rawLink.startsWith("vmess://")  -> "VMESS"
            rawLink.startsWith("trojan://") -> "TROJAN"
            rawLink.startsWith("ss://")     -> "SS"
            rawLink.startsWith("ssr://")    -> "SSR"
            else -> return null
        }

        val remarks = rawLink.substringAfterLast('#', "").let { frag ->
            if (frag.isNotBlank()) try { URLDecoder.decode(frag, "UTF-8") }
            catch (_: Exception) { frag } else "Config"
        }

        val endpoint = when (protocol) {
            "VMESS" -> {
                val b64 = rawLink.removePrefix("vmess://").substringBefore("#")
                val json = JSONObject(String(Base64.decode(b64, Base64.DEFAULT)))
                "${json.optString("add","?")}:${json.optString("port","?")}"
            }
            else -> {
                val noScheme = rawLink.substringAfter("://")
                val atIdx = noScheme.indexOf('@')
                val hostPort = (if (atIdx >= 0) noScheme.substring(atIdx + 1) else noScheme)
                    .substringBefore("?").substringBefore("#")
                hostPort
            }
        }

        VpnConfig(protocol, remarks, endpoint, rawLink)
    } catch (_: Exception) { null }

    private fun isJson(s: String): Boolean {
        val t = s.trim(); return (t.startsWith("{") && t.endsWith("}")) || t.startsWith("[")
    }

    private fun tryBase64(s: String): String? = try {
        val p = s.trim(); val padded = p + "=".repeat((4 - p.length % 4) % 4)
        Base64.decode(padded, Base64.DEFAULT).toString(Charsets.UTF_8)
    } catch (_: Exception) { null }

    private fun List<VpnConfig>.deduplicate(): List<VpnConfig> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(it.rawLink.substringBefore("#")) }
    }
}
