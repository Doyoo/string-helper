package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.content.ContentFactory
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MyToolWindowFactory : ToolWindowFactory {

    override fun isDumbAware(): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val inputArea = JBTextArea(8, 60).apply {
            lineWrap = true
            emptyText.text = "在此输入原始字符串..."
        }

        val outputArea = JBTextArea(8, 60).apply {
            lineWrap = true
            isEditable = false
        }

        inputArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = syncClearOutput()
            override fun removeUpdate(e: DocumentEvent) = syncClearOutput()
            override fun changedUpdate(e: DocumentEvent) = syncClearOutput()

            private fun syncClearOutput() {
                if (inputArea.text.isEmpty()) {
                    outputArea.text = ""
                }
            }
        })

        val mainPanel = panel {
            row("Input:") {
                cell(JBScrollPane(inputArea)).align(Align.FILL)
            }.resizableRow()

            row("Output:") {
                cell(JBScrollPane(outputArea)).align(Align.FILL)
            }.resizableRow()

            row {
                val actions = mapOf(
                    "Unicode" to StringTransformer::transformUnicode,
                    "Base64" to StringTransformer::transformBase64,
                    "URL" to StringTransformer::transformUrl,
                    "Multipart" to StringTransformer::transformMultipart
                )

                actions.forEach { (label, func) ->
                    button(label) {
                        val inputText = inputArea.text
                        val result = func(inputText)
                        outputArea.text = result
                    }
                }
            }
        }

        val content = ContentFactory.getInstance().createContent(mainPanel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}