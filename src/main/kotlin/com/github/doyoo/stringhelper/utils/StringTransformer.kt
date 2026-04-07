package com.github.doyoo.stringhelper.utils

import com.github.doyoo.stringhelper.bundle.MyPluginBundle
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.regex.Pattern

/**
 * @Author: Aaron
 * @Date: 2026/02/25 00:17:49
 */
class StringTransformer {

    companion object {
        fun smartTransform(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ""

            return when {
                isProbablyJson(trimmed) -> transformJson(trimmed)
                trimmed.contains("\\u") -> transformUnicode(trimmed)
                trimmed.contains("%") -> transformUrl(trimmed)
                trimmed.startsWith("--") -> transformMultipart(trimmed)
                else -> tryBase64(trimmed)
            }
        }

        fun transformUnicode(input: String): String {
            return if (input.contains("\\u")) {
                val regex = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
                val matcher = regex.matcher(input)
                val sb = StringBuilder()
                var lastEnd = 0
                while (matcher.find()) {
                    sb.append(input, lastEnd, matcher.start())
                    sb.append(matcher.group(1).toInt(16).toChar())
                    lastEnd = matcher.end()
                }
                sb.append(input.substring(lastEnd))
                sb.toString()
            } else {
                input.map { "\\u%04x".format(it.code) }.joinToString("")
            }
        }

        fun transformBase64(input: String): String {
            return try {
                val decoded = Base64.getDecoder().decode(input.trim())
                String(decoded, Charsets.UTF_8)
            } catch (e: Exception) {
                // 否则执行加密
                Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
            }
        }

        fun transformUrl(input: String): String {
            return if (input.contains("%")) {
                URLDecoder.decode(input, Charsets.UTF_8)
            } else {
                URLEncoder.encode(input, Charsets.UTF_8)
            }
        }

        fun transformMultipart(input: String): String {
            val fields = mutableMapOf<String, String>()
            val lines = input.lines()

            val boundary = lines.firstOrNull { it.startsWith("--") }?.trim() ?: return ""
            val parts = input.split(boundary)

            for (segment in parts) {
                val trimmed = segment.trim()
                if (trimmed.isEmpty() || trimmed == "--") continue

                // 尽量处理 header/body 分隔，无论是 \r\n\r\n 还是 \n\n
                val headerBodySepR = trimmed.indexOf("\r\n\r\n")
                val headerBodySepN = trimmed.indexOf("\n\n")
                val sep = when {
                    headerBodySepR >= 0 -> headerBodySepR
                    headerBodySepN >= 0 -> headerBodySepN
                    else -> continue
                }

                // 检查 sep + 分隔长度 是否在范围内
                val bodyStart = if (headerBodySepR >= 0) sep + 4 else sep + 2
                if (bodyStart >= trimmed.length) continue

                val headers = trimmed.substring(0, sep)
                val body = trimmed.substring(bodyStart).trim()

                val keyMatch = Regex("name=\"([^\"]+)\"").find(headers)
                val key = keyMatch?.groupValues?.get(1) ?: continue

                fields[key] = body
            }

            return fields.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8)
            }
        }

        fun transformJson(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ""

            // --- 核心逻辑：处理转义字符 ---
            // 如果字符串包含 \" 且以 { 或 [ 开头（或被引号包裹），先进行反转义
            var processed = trimmed
            if (processed.contains("\\\"")) {
                processed = processed.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                // 如果反转义后首尾有多余引号，剥离它们
                if (processed.startsWith("\"") && processed.endsWith("\"")) {
                    processed = processed.substring(1, processed.length - 1)
                }
            }

            return try {
                val jsonElement = JsonParser.parseString(processed)
                val gsonPretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                val gsonCompact = GsonBuilder().disableHtmlEscaping().create()

                // 如果原始输入包含换行符，则执行压缩，否则执行美化
                if (trimmed.contains("\n")) {
                    gsonCompact.toJson(jsonElement)
                } else {
                    gsonPretty.toJson(jsonElement)
                }
            } catch (e: Exception) {
                MyPluginBundle.message("msg.json.error", e.localizedMessage)
            }
        }

        private fun isProbablyJson(input: String): Boolean {
            val s = input.replace("\\\"", "\"")
            return (s.startsWith("{") && s.endsWith("}")) ||
                    (s.startsWith("[") && s.endsWith("]")) ||
                    (s.startsWith("\"") && s.endsWith("\"") && s.contains(":"))
        }

        private fun tryBase64(input: String): String = try {
            val decoded = Base64.getDecoder().decode(input)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            // 如果不是 Base64，执行编码
            Base64.getEncoder().encodeToString(input.toByteArray())
        }
    }
}