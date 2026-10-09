package com.github.doyoo.stringhelper.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.ui.JBColor
import java.awt.image.BufferedImage
import java.io.StringReader
import java.io.StringWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.regex.Pattern

/**
 * @Author: Aaron
 * @Date: 2026/02/25 00:17:49
 */
class StringTransformer {

    enum class TransformMode {
        Auto, JSON, XML, Unicode, Base64, URL, MD5, Multipart, QR
    }

    data class TransformResult(
        val text: String,
        val errors: List<HighlightError> = emptyList()
    )

    data class QRResult(
        val image: BufferedImage
    )

    sealed class TransformOutput {
        data class Text(val result: TransformResult) : TransformOutput()
        data class QR(val result: QRResult) : TransformOutput()
    }

    data class HighlightError(
        val start: Int,
        val end: Int,
        val message: String
    )

    companion object {
        fun transformWithErrors(
            input: String,
            mode: TransformMode
        ): TransformOutput {
            return when (mode) {
                TransformMode.QR -> {
                    TransformOutput.QR(
                        QRResult(generateQRCode(input.trim()))
                    )
                }

                TransformMode.JSON -> TransformOutput.Text(transformJson(input))
                TransformMode.JSONCompare -> TransformOutput.Text(transformJsonCompare(input))
                TransformMode.XML -> TransformOutput.Text(transformXml(input))
                TransformMode.Unicode -> TransformOutput.Text(transformUnicode(input))
                TransformMode.Base64 -> TransformOutput.Text(transformBase64(input))
                TransformMode.URL -> TransformOutput.Text(transformUrl(input))
                TransformMode.MD5 -> TransformOutput.Text(transformMd5(input))
                TransformMode.Multipart -> TransformOutput.Text(transformMultipart(input))
                TransformMode.JSONMinify -> TransformOutput.Text(transformJsonMinify(input))
                TransformMode.SQLMinify -> TransformOutput.Text(transformSqlMinify(input))
                TransformMode.AutoMinify -> TransformOutput.Text(autoMinify(input))
                TransformMode.Auto -> TransformOutput.Text(autoTransform(input))
            }
        }

        fun autoTransform(input: String): TransformResult {
            val t = input.trim()
            return when {
                isJson(t) -> transformJson(t)
                isXml(t) -> transformXml(t)
                isMultipart(t) -> transformMultipart(t)
                isBase64(t) -> transformBase64(t)
                else -> transformUnicode(t)
            }
        }

        fun transformXml(input: String): TransformResult {
            return try {
                val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(org.xml.sax.InputSource(StringReader(input)))
                val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
                val writer = StringWriter()
                transformer.transform(
                    javax.xml.transform.dom.DOMSource(doc),
                    javax.xml.transform.stream.StreamResult(writer)
                )
                TransformResult(writer.toString())
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(HighlightError(0, input.length, "XML Error: ${e.localizedMessage}"))
                )
            }
        }

        fun transformUnicode(input: String): TransformResult {
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
                TransformResult(sb.toString())
            } else {
                TransformResult(input.map { "\\u%04x".format(it.code) }.joinToString(""))
            }
        }

        fun transformBase64(input: String): TransformResult {
            return try {
                val decoded = Base64.getDecoder().decode(input.trim())
                TransformResult(String(decoded, Charsets.UTF_8))
            } catch (_: Exception) {
                TransformResult(Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8)))
            }
        }

        fun transformMultipart(input: String): TransformResult {
            return try {
                val fields = mutableMapOf<String, String>()
                val lines = input.lines()

                val boundary = lines.firstOrNull { it.startsWith("--") }?.trim()
                    ?: return TransformResult(
                        input,
                        listOf(HighlightError(0, input.length, "Missing multipart boundary"))
                    )

                val parts = input.split(boundary)

                for (segment in parts) {
                    val trimmed = segment.trim()
                    if (trimmed.isEmpty() || trimmed == "--") continue

                    val headerBodySepR = trimmed.indexOf("\r\n\r\n")
                    val headerBodySepN = trimmed.indexOf("\n\n")

                    val sep = when {
                        headerBodySepR >= 0 -> headerBodySepR
                        headerBodySepN >= 0 -> headerBodySepN
                        else -> continue
                    }

                    val bodyStart = if (headerBodySepR >= 0) sep + 4 else sep + 2
                    if (bodyStart >= trimmed.length) continue

                    val headers = trimmed.substring(0, sep)
                    val body = trimmed.substring(bodyStart).trim()

                    val key = Regex("name=\"([^\"]+)\"")
                        .find(headers)?.groupValues?.get(1)
                        ?: continue

                    fields[key] = body
                }

                val result = fields.entries.joinToString("&") { (k, v) ->
                    URLEncoder.encode(k, Charsets.UTF_8) + "=" +
                            URLEncoder.encode(v, Charsets.UTF_8)
                }

                TransformResult(result)

            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(HighlightError(0, input.length, "Multipart parse error: ${e.message}"))
                )
            }
        }

        fun transformJson(input: String): TransformResult {
            val text = input.trim()
            if (text.isEmpty()) {
                return TransformResult("")
            }

            return try {
                val jsonElement = parseDeep(
                    unescapeJsonIfNeeded(
                        text
                            .replace('“', '"')
                            .replace('”', '"')
                    )
                )
                val gson = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()

                TransformResult(
                    gson.toJson(jsonElement)
                )
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "JSON Error: ${e.message}"
                        )
                    )
                )
            }
        }

        fun transformJsonCompare(input: String): TransformResult {
            val separator = Regex("""\r?\n[ \t]*\r?\n""")

            val match = separator.find(input)
                ?: return TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "Missing separator: Please separate the two JSON documents with a blank line."
                        )
                    )
                )

            val leftText = input.substring(0, match.range.first).trim()
            val rightText = input.substring(match.range.last + 1).trim()

            if (leftText.isEmpty() || rightText.isEmpty()) {
                return TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "Both JSON inputs are required"
                        )
                    )
                )
            }

            return try {
                val left = JsonParser.parseString(leftText)
                val right = JsonParser.parseString(rightText)

                val differences = mutableListOf<String>()

                fun compare(
                    path: String,
                    oldValue: JsonElement?,
                    newValue: JsonElement?
                ) {
                    if (oldValue == null && newValue != null) {
                        differences += "ADDED $path: $newValue"
                        return
                    }

                    if (oldValue != null && newValue == null) {
                        differences += "REMOVED $path: $oldValue"
                        return
                    }

                    if (oldValue == null || newValue == null) return

                    when {
                        oldValue.isJsonObject && newValue.isJsonObject -> {
                            val oldObject = oldValue.asJsonObject
                            val newObject = newValue.asJsonObject

                            val keys = (
                                    oldObject.keySet() + newObject.keySet()
                                    ).toSortedSet()

                            for (key in keys) {
                                compare(
                                    "$path.$key",
                                    oldObject.get(key),
                                    newObject.get(key)
                                )
                            }
                        }

                        oldValue.isJsonArray && newValue.isJsonArray -> {
                            val oldArray = oldValue.asJsonArray
                            val newArray = newValue.asJsonArray

                            for (i in 0 until maxOf(
                                oldArray.size(),
                                newArray.size()
                            )) {
                                compare(
                                    "$path[$i]",
                                    if (i < oldArray.size()) oldArray[i] else null,
                                    if (i < newArray.size()) newArray[i] else null
                                )
                            }
                        }

                        oldValue != newValue -> {
                            differences += "CHANGED $path: $oldValue -> $newValue"
                        }
                    }
                }

                compare("$", left, right)

                TransformResult(
                    if (differences.isEmpty()) {
                        "JSONs are identical."
                    } else {
                        differences.joinToString("\n")
                    }
                )
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "JSON Compare Error: ${e.message}"
                        )
                    )
                )
            }
        }

        fun transformUrl(input: String): TransformResult {
            val trimmed = input.trim()
            return try {
                val decoded = URLDecoder.decode(trimmed, Charsets.UTF_8)
                if (decoded != trimmed) {
                    TransformResult(decoded)
                } else {
                    TransformResult(URLEncoder.encode(trimmed, Charsets.UTF_8))
                }
            } catch (_: Exception) {
                TransformResult(
                    input,
                    listOf(HighlightError(0, input.length, "Invalid URL encoding"))
                )
            }
        }

        fun transformMd5(input: String): TransformResult {
            val trimmed = input.trim()
            return try {
                if (trimmed.isEmpty()) {
                    return TransformResult("")
                }

                val md5 = MessageDigest
                    .getInstance("MD5")
                    .digest(trimmed.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                TransformResult(md5)
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "MD5 Error: ${e.message}"
                        )
                    )
                )
            }
        }

        fun transformJsonMinify(input: String): TransformResult {
            val text = input.trim()

            if (text.isEmpty()) {
                return TransformResult("")
            }

            return try {
                val jsonElement = parseDeep(
                    unescapeJsonIfNeeded(
                        text
                            .replace('“', '"')
                            .replace('”', '"')
                    )
                )

                val gson = GsonBuilder()
                    .disableHtmlEscaping()
                    .create()

                TransformResult(gson.toJson(jsonElement))
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "JSON Error: ${e.message}"
                        )
                    )
                )
            }
        }

        fun transformSqlMinify(input: String): TransformResult {
            if (input.isBlank()) return TransformResult("")

            return try {
                TransformResult(minifySql(input))
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "SQL Error: ${e.message}"
                        )
                    )
                )
            }
        }

        fun autoMinify(input: String): TransformResult {
            val t = input.trim()

            return when {
                isJson(t) -> transformJsonMinify(t)

                // 粗略 SQL 检测
                isSql(t) -> transformSqlMinify(t)

                else -> TransformResult(
                    input,
                    listOf(
                        HighlightError(
                            0,
                            input.length,
                            "Unsupported format for minify"
                        )
                    )
                )
            }
        }

        fun applyHighlights(editor: Editor, errors: List<HighlightError>, baseOffset: Int = 0) {
            val markupModel = editor.markupModel

            markupModel.allHighlighters
                .filter { it.layer == HighlighterLayer.ERROR }
                .forEach { markupModel.removeHighlighter(it) }

            errors.forEach {
                val attr = TextAttributes().apply {
                    effectType = EffectType.WAVE_UNDERSCORE
                    effectColor = JBColor.RED
                }

                val highlighter = markupModel.addRangeHighlighter(
                    baseOffset + it.start,
                    baseOffset + it.end,
                    HighlighterLayer.ERROR,
                    attr,
                    HighlighterTargetArea.EXACT_RANGE
                )

                highlighter.errorStripeTooltip = it.message
            }
        }

        fun detect(text: String): Triple<String, FileType, String> {
            val t = text.trim()

            return when {
                isJson(t) -> Triple(t, getFileType("json"), "JSON")
                isXml(t) -> Triple(t, getFileType("xml"), "XML")
                isSql(t) -> Triple(t, getFileType("sql"), "SQL")
                else -> Triple(t, PlainTextFileType.INSTANCE, "TEXT")
            }
        }

        fun generateQRCode(text: String, size: Int = 320): BufferedImage {
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.MARGIN to 0
            )
            val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            return MatrixToImageWriter.toBufferedImage(matrix)
        }

        private fun parseDeep(text: String): JsonElement {
            var element = JsonParser.parseString(text)
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                val inner = element
                    .asString
                    .trim()
                if (looksLikeJson(inner)) {
                    val normalized = unescapeJsonIfNeeded(inner)
                    element = JsonParser.parseString(normalized)
                }
            }
            return deepTransform(element)
        }

        private fun deepTransform(element: JsonElement): JsonElement {
            return when {
                element.isJsonObject -> {
                    val obj = JsonObject()
                    element.asJsonObject.entrySet()
                        .forEach { (k, v) ->
                            obj.add(k, deepTransform(v))
                        }
                    obj
                }

                element.isJsonArray -> {
                    val arr = JsonArray()
                    element.asJsonArray.forEach {
                        arr.add(deepTransform(it))
                    }
                    arr
                }

                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val str = element
                        .asString
                        .trim()
                    if (!looksLikeJson(str)) {
                        return element
                    }

                    try {
                        val normalized = unescapeJsonIfNeeded(str)
                        val parsed = JsonParser.parseString(normalized)
                        deepTransform(parsed)
                    } catch (_: Exception) {
                        element
                    }
                }

                else -> element
            }
        }

        private fun minifySql(input: String): String {
            val out = StringBuilder(input.length)
            var i = 0
            var needSpace = false

            fun space() {
                if (needSpace && out.isNotEmpty()) {
                    val last = out.last()
                    if (!last.isWhitespace() && last !in "(),;") {
                        out.append(' ')
                    }
                }
                needSpace = false
            }

            fun quoted(quote: Char) {
                space()
                out.append(quote)
                i++

                while (i < input.length) {
                    val c = input[i++]
                    out.append(c)

                    if (c == quote) {
                        if (i < input.length && input[i] == quote) {
                            out.append(input[i++]) // '', "", ``
                        } else {
                            break
                        }
                    }
                }
            }

            while (i < input.length) {
                when {
                    // 字符串 / quoted identifier
                    input[i] == '\'' ||
                            input[i] == '"' ||
                            input[i] == '`' -> {
                        quoted(input[i])
                    }

                    // -- line comment
                    input[i] == '-' &&
                            i + 1 < input.length &&
                            input[i + 1] == '-' -> {
                        i += 2
                        while (i < input.length && input[i] != '\n') i++
                        needSpace = true
                    }

                    // /* block comment */
                    input[i] == '/' &&
                            i + 1 < input.length &&
                            input[i + 1] == '*' -> {
                        i += 2
                        while (
                            i + 1 < input.length &&
                            !(input[i] == '*' && input[i + 1] == '/')
                        ) {
                            i++
                        }
                        if (i + 1 < input.length) i += 2
                        needSpace = true
                    }

                    // whitespace
                    input[i].isWhitespace() -> {
                        needSpace = true
                        i++
                    }

                    // 不需要空格的标点
                    input[i] in "(),;" -> {
                        needSpace = false
                        out.append(input[i++])
                    }

                    else -> {
                        space()
                        out.append(input[i++])
                    }
                }
            }

            return out.toString().trim()
        }

        private fun looksLikeJson(text: String): Boolean {
            val t = text.trim()
            return ((t.startsWith("{") && t.endsWith("}"))
                    || (t.startsWith("[") && t.endsWith("]"))
                    || t.startsWith("\\{")
                    || t.startsWith("\\["))
        }

        private fun unescapeJsonIfNeeded(text: String): String {
            return text
                .trim()
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        private fun isSql(text: String): Boolean {
            val t = text.trimStart()

            return Regex(
                """(?is)^(SELECT|INSERT\s+INTO|UPDATE|DELETE\s+FROM|CREATE\s+(TABLE|VIEW|INDEX)|ALTER\s+TABLE|DROP\s+(TABLE|VIEW|INDEX)|WITH)\b"""
            ).containsMatchIn(t)
        }

        private fun isJson(text: String) =
            text.startsWith("{") || text.startsWith("[")

        private fun isXml(text: String) =
            text.startsWith("<") && text.endsWith(">")

        private fun isBase64(text: String) =
            text.length % 4 == 0 && text.matches(Regex("^[A-Za-z0-9+/=]+$"))

        private fun isMultipart(text: String) =
            text.startsWith("--")

        private fun getFileType(ext: String) =
            FileTypeManager.getInstance().getFileTypeByExtension(ext)
    }
}