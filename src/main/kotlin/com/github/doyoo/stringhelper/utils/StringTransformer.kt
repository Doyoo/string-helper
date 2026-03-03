package com.github.doyoo.stringhelper.utils

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
    }
}