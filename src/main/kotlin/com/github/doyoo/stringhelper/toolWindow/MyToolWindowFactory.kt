package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.bundle.MyPluginBundle
import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {

    enum class TransformMode {
        Auto, JSON, Unicode, Base64, URL, Multipart
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val inputArea = JBTextArea().apply {
            lineWrap = true
            emptyText.text = MyPluginBundle.message("placeholder.input")
        }

        val outputArea = JBTextArea().apply {
            lineWrap = true
            isEditable = false
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }

        val modeComboBox = ComboBox(TransformMode.entries.toTypedArray()).apply {
            selectedItem = TransformMode.Auto
        }

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(
                MyPluginBundle.message("button.transform"),
                MyPluginBundle.message("tooltip.execute"),
                AllIcons.Actions.Execute
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val inputText = inputArea.text
                    if (inputText.isBlank()) return

                    val selectedMode =
                        modeComboBox.selectedItem as? TransformMode ?: TransformMode.Auto

                    outputArea.text = when (selectedMode) {
                        TransformMode.Auto -> StringTransformer.smartTransform(inputText)
                        TransformMode.JSON -> StringTransformer.transformJson(inputText)
                        TransformMode.Unicode -> StringTransformer.transformUnicode(inputText)
                        TransformMode.Base64 -> StringTransformer.transformBase64(inputText)
                        TransformMode.URL -> StringTransformer.transformUrl(inputText) // ✅ 修复点
                        TransformMode.Multipart -> StringTransformer.transformMultipart(inputText)
                    }
                }
            })

            // 📋 复制
            add(object : AnAction(
                MyPluginBundle.message("button.copy"),
                null,
                AllIcons.Actions.Copy
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val selection = StringSelection(outputArea.text)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                }
            })

            addSeparator()

            // 🧹 清空
            add(object : AnAction(
                MyPluginBundle.message("button.clear"),
                null,
                AllIcons.Actions.GC
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    inputArea.text = ""
                    outputArea.text = ""
                }
            })
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ModernToolbar", actionGroup, true)
        toolbar.targetComponent = inputArea

        val splitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = JBScrollPane(inputArea).apply {
                border = IdeBorderFactory.createTitledBorder(
                    MyPluginBundle.message("label.input"),
                    false
                )
            }
            secondComponent = JBScrollPane(outputArea).apply {
                border = IdeBorderFactory.createTitledBorder(
                    MyPluginBundle.message("label.result"),
                    false
                )
            }
        }

        val container = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10, 12)

            val topPanel = panel {
                row(MyPluginBundle.message("label.mode")) {
                    cell(modeComboBox)
                    cell(toolbar.component)
                }
            }.apply {
                border = JBUI.Borders.emptyBottom(8)
            }

            add(topPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance()
            .createContent(container, null, false)

        toolWindow.contentManager.addContent(content)
    }
}