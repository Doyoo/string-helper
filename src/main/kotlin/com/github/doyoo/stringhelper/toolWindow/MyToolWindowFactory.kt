package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val inputArea = JBTextArea().apply {
            lineWrap = true
            emptyText.text = "Enter text..."
            border = JBUI.Borders.empty(6)
        }

        val document = EditorFactory.getInstance().createDocument("")
        val outputEditor = EditorFactory.getInstance()
            .createEditor(document, project) as EditorEx
        val statusLabel = JLabel("Type: -")

        val modeComboBox = ComboBox(
            StringTransformer.TransformMode.entries.toTypedArray()
        )

        val cardLayout = CardLayout()
        val outputContainer = JPanel(cardLayout).apply {
            border = JBUI.Borders.empty(6)
        }

        val qrPanel = JPanel(BorderLayout())

        outputContainer.add(outputEditor.component, "TEXT")
        outputContainer.add(qrPanel, "QR")

        fun showTextMode() {
            cardLayout.show(outputContainer, "TEXT")
        }

        fun showQrMode(image: Image) {
            qrPanel.removeAll()
            qrPanel.add(JLabel(ImageIcon(image)), BorderLayout.CENTER)
            qrPanel.revalidate()
            qrPanel.repaint()

            cardLayout.show(outputContainer, "QR")
        }

        fun transform() {
            val input = inputArea.text
            if (input.isBlank()) return

            val mode = modeComboBox.selectedItem as StringTransformer.TransformMode

            when (val output = StringTransformer.transformWithErrors(input, mode)) {
                is StringTransformer.TransformOutput.QR -> {
                    showQrMode(output.result.image)
                    statusLabel.text = "Type: QR"
                }

                is StringTransformer.TransformOutput.Text -> {

                    val (finalText, fileType, typeName) =
                        StringTransformer.detect(output.result.text)

                    WriteCommandAction.runWriteCommandAction(project) {
                        document.setText(finalText)
                    }

                    outputEditor.highlighter =
                        EditorHighlighterFactory.getInstance()
                            .createEditorHighlighter(
                                fileType,
                                outputEditor.colorsScheme,
                                project
                            )

                    StringTransformer.applyHighlights(
                        outputEditor,
                        output.result.errors
                    )

                    showTextMode()
                    statusLabel.text = "Type: $typeName"
                }
            }
        }

        val actionGroup = DefaultActionGroup().apply {

            add(object : AnAction("Run", null, AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) = transform()
            })

            add(object : AnAction("Copy", null, AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val sel = StringSelection(document.text)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                }
            })

            add(object : AnAction("Clear", null, AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    inputArea.text = ""

                    WriteCommandAction.runWriteCommandAction(project) {
                        document.setText("")
                    }

                    showTextMode()
                    statusLabel.text = "Type: -"
                }
            })
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ParserToolbar", actionGroup, true).apply {
                targetComponent = inputArea
                component.border = JBUI.Borders.empty(0, 4)
            }

        val topPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)

            val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(modeComboBox)
            }

            val center = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(toolbar.component)
            }

            val right = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(statusLabel)
            }

            add(left, BorderLayout.WEST)
            add(center, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }

        val splitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = JBScrollPane(inputArea).apply {
                border = JBUI.Borders.empty(6)
            }

            secondComponent = outputContainer
        }

        val container = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 10)
            add(topPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance()
            .createContent(container, null, false)

        toolWindow.contentManager.addContent(content)

        Disposer.register(content) {
            EditorFactory.getInstance().releaseEditor(outputEditor)
        }
    }
}