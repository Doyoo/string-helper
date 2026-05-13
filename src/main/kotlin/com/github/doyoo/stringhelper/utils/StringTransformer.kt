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
import java.util.Base64
import java.util.regex.Pattern

/**
 * @Author: Aaron
 * @Date: 2026/02/25 00:17:49
 */
class StringTransformer {

    enum class TransformMode {
        Auto, JSON, XML, Unicode, Base64, URL, Multipart, QR
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
                TransformMode.XML -> TransformOutput.Text(transformXml(input))
                TransformMode.Unicode -> TransformOutput.Text(transformUnicode(input))
                TransformMode.Base64 -> TransformOutput.Text(transformBase64(input))
                TransformMode.URL -> TransformOutput.Text(transformUrl(input))
                TransformMode.Multipart -> TransformOutput.Text(transformMultipart(input))
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
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                return TransformResult("")
            }

            return try {
                var jsonElement = JsonParser.parseString(trimmed)
                if (
                    jsonElement.isJsonPrimitive &&
                    jsonElement.asJsonPrimitive.isString
                ) {
                    val inner = jsonElement.asString.trim()
                    if (
                        inner.startsWith("{") ||
                        inner.startsWith("[")
                    ) {
                        jsonElement = JsonParser.parseString(inner)
                    }
                }

                fun deepTransform(element: JsonElement): JsonElement {

                    return when {
                        element.isJsonObject -> {
                            val newObj = JsonObject()
                            element.asJsonObject.entrySet().forEach { (key, value) ->
                                newObj.add(key, deepTransform(value))
                            }

                            newObj
                        }

                        element.isJsonArray -> {
                            val newArray = JsonArray()
                            element.asJsonArray.forEach {
                                newArray.add(deepTransform(it))
                            }
                            newArray
                        }

                        element.isJsonPrimitive &&
                                element.asJsonPrimitive.isString -> {
                            val text = element.asString.trim()
                            val mayBeJson =
                                (text.startsWith("{") && text.endsWith("}")) ||
                                        (text.startsWith("[") && text.endsWith("]"))

                            if (mayBeJson) {

                                try {
                                    val parsed =
                                        JsonParser.parseString(text)
                                    deepTransform(parsed)
                                } catch (_: Exception) {
                                    element
                                }
                            } else {
                                element
                            }
                        }

                        else -> element
                    }
                }

                jsonElement = deepTransform(jsonElement)
                val gsonPretty = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()

                val gsonCompact = GsonBuilder()
                    .disableHtmlEscaping()
                    .create()

                val result = if (trimmed.contains("\n")) {
                    gsonCompact.toJson(jsonElement)
                } else {
                    gsonPretty.toJson(jsonElement)
                }

                TransformResult(result)

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