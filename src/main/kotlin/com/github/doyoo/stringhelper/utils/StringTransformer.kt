package com.github.doyoo.stringhelper.utils

import com.github.doyoo.stringhelper.bundle.MyPluginBundle
import com.github.doyoo.stringhelper.toolWindow.MyToolWindowFactory
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.ui.JBColor
import java.io.StringReader
import java.io.StringWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.regex.Pattern

/**
 * @Author: Aaron
 * @Date: 2026/02/25 00:17:49
 */
class StringTransformer {

    enum class TransformMode {
        Auto, JSON, XML, Unicode, Base64, URL, Multipart
    }

    data class TransformResult(
        val text: String,
        val errors: List<HighlightError> = emptyList()
    )

    data class HighlightError(
        val start: Int,
        val end: Int,
        val message: String
    )

    companion object {

        fun transformWithErrors(input: String, mode: TransformMode): TransformResult {
            return when (mode) {
                TransformMode.JSON -> transformJson(input)
                TransformMode.XML -> transformXml(input)
                TransformMode.Unicode -> transformUnicode(input)
                TransformMode.Base64 -> transformBase64(input)
                TransformMode.URL -> transformUrl(input)
                TransformMode.Multipart -> transformMultipart(input)
                TransformMode.Auto -> autoTransform(input)
            }
        }

        fun autoTransform(input: String): TransformResult {
            val t = input.trim()
            return when {
                isJson(t) -> transformJson(t)
                isXml(t) -> transformXml(t)
                isMultipart(t) -> transformMultipart(t) // 👈 加这个
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
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return TransformResult("")

            var processed = trimmed
            if (processed.contains("\\\"")) {
                processed = processed.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                if (processed.startsWith("\"") && processed.endsWith("\"")) {
                    processed = processed.substring(1, processed.length - 1)
                }
            }

            return try {
                val jsonElement = JsonParser.parseString(processed)
                val gsonPretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                val gsonCompact = GsonBuilder().disableHtmlEscaping().create()
                return if (trimmed.contains("\n")) {
                    TransformResult(gsonCompact.toJson(jsonElement))
                } else {
                    TransformResult(gsonPretty.toJson(jsonElement))
                }
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(HighlightError(0, input.length, "JSON Error: ${e.message}"))
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
            } catch (e: Exception) {
                TransformResult(
                    input,
                    listOf(HighlightError(0, input.length, "Invalid URL encoding"))
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

        private fun isProbablyJson(input: String): Boolean {
            val s = input.replace("\\\"", "\"")
            return (s.startsWith("{") && s.endsWith("}")) ||
                    (s.startsWith("[") && s.endsWith("]")) ||
                    (s.startsWith("\"") && s.endsWith("\"") && s.contains(":"))
        }

        private fun tryBase64(input: String): String = try {
            val decoded = Base64.getDecoder().decode(input)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            Base64.getEncoder().encodeToString(input.toByteArray())
        }

        fun detect(text: String): Triple<String, FileType, String> {
            val t = text.trim()
            return when {
                isJson(t) -> Triple(t, getFileType("json"), "JSON")
                isXml(t) -> Triple(t, getFileType("xml"), "XML")
                else -> Triple(t, PlainTextFileType.INSTANCE, "TEXT")
            }
        }

        fun isJson(text: String) =
            text.startsWith("{") || text.startsWith("[")

        fun isXml(text: String) =
            text.startsWith("<") && text.endsWith(">")

        fun isBase64(text: String) =
            text.length % 4 == 0 && text.matches(Regex("^[A-Za-z0-9+/=]+$"))

        fun isUnicode(text: String) =
            text.contains("\\u")

        fun isMultipart(text: String) =
            text.startsWith("--")

        fun getFileType(ext: String) =
            FileTypeManager.getInstance().getFileTypeByExtension(ext)
    }
}