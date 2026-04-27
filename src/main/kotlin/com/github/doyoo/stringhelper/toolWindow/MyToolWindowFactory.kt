package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JLabel
import javax.swing.JPanel

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

        outputEditor.apply {
            isViewer = false
            settings.apply {
                isLineNumbersShown = true
                isFoldingOutlineShown = true
                isIndentGuidesShown = true
            }
        }

        val statusLabel = JLabel("Type: -")
        val modeComboBox = ComboBox(
            StringTransformer.TransformMode.entries.toTypedArray()
        ).apply {
            selectedItem = StringTransformer.TransformMode.Auto
        }

        fun transform() {
            val input = inputArea.text
            if (input.isBlank()) return

            val mode = modeComboBox.selectedItem as StringTransformer.TransformMode
            val result = StringTransformer.transformWithErrors(input, mode)

            val (finalText, fileType, typeName) =
                StringTransformer.detect(result.text)

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

            StringTransformer.applyHighlights(outputEditor, result.errors)
            statusLabel.text = "Type: $typeName"
        }

        val actionGroup = DefaultActionGroup().apply {

            add(object : AnAction("Run", "Execute", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) = transform()
            })

            add(object : AnAction("Copy", null, AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    val selection = StringSelection(document.text)
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(selection, selection)
                }
            })

            addSeparator()

            add(object : AnAction("Clear", null, AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    inputArea.text = ""
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.setText("")
                    }
                    statusLabel.text = "Type: -"
                }
            })
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ParserToolbar", actionGroup, true).apply {
                targetComponent = inputArea
            }

        // =========================
        // Top Panel (IDE style spacing)
        // =========================
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
                border = JBUI.Borders.empty(8)
            }

            secondComponent = outputEditor.component.apply {
                border = JBUI.Borders.empty(8)
            }
        }

        val container = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10, 12)

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