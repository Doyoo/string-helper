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
import java.awt.Container
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 使用 Bundle 获取占位符文字
        val inputArea = JBTextArea().apply {
            lineWrap = true
            emptyText.text = MyPluginBundle.message("placeholder.input")
        }

        val outputArea = JBTextArea().apply {
            lineWrap = true
            isEditable = false
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }

        // 下拉框选项可以保持不变，或同样放入 Bundle
        val modeComboBox = ComboBox(arrayOf("Auto", "JSON", "Unicode", "Base64", "URL", "Multipart"))

        // --- 工具栏 Action 优化 ---
        val actionGroup = DefaultActionGroup().apply {
            // 执行转换
            add(object : AnAction(
                MyPluginBundle.message("button.transform"),
                MyPluginBundle.message("tooltip.execute"),
                AllIcons.Actions.Execute
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val inputText = inputArea.text
                    if (inputText.isBlank()) return

                    val selectedMode = modeComboBox.selectedItem?.toString() ?: "Auto"
                    outputArea.text = when (selectedMode) {
                        "Auto" -> StringTransformer.smartTransform(inputText)
                        "JSON" -> StringTransformer.transformJson(inputText)
                        // ... 其他逻辑 ...
                        else -> ""
                    }
                }
            })

            // 复制按钮
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

            // 清空按钮
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

        val toolbar = ActionManager.getInstance().createActionToolbar("ModernToolbar", actionGroup, true)
        toolbar.targetComponent = inputArea

        // --- 现代布局组装 ---
        val topPanel = panel {
            row(MyPluginBundle.message("label.mode")) {
                cell(modeComboBox)
                cell(toolbar.component)
            }
        }

        val splitter = OnePixelSplitter(true, 0.5f).apply {
            firstComponent = JBScrollPane(inputArea).apply {
                border = IdeBorderFactory.createTitledBorder(MyPluginBundle.message("label.input"), false)
            }
            secondComponent = JBScrollPane(outputArea).apply {
                border = IdeBorderFactory.createTitledBorder(MyPluginBundle.message("label.result"), false)
            }
        }

        val container = JPanel(BorderLayout()).apply {
            // 为整个面板增加四周的间距（上，左，下，右）
            // JBUI.scale(10) 会根据用户 IDE 的缩放比例自动调整像素
            border = JBUI.Borders.empty(10, 12)

            // 顶部操作栏
            val topPanel = panel {
                row(MyPluginBundle.message("label.mode")) {
                    cell(modeComboBox)
                    cell(toolbar.component)
                }
            }.apply {
                // 给模式选择行和下方的分割器之间加一点垂直间距
                border = JBUI.Borders.emptyBottom(8)
            }

            add(topPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(container, null, false)
        toolWindow.contentManager.addContent(content)
    }
}