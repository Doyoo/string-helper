package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val textArea = JBTextArea(10, 40).apply { lineWrap = true }
        val panel = JPanel(BorderLayout())
        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        val actions = mapOf(
            "Unicode" to StringTransformer::transformUnicode,
            "Base64" to StringTransformer::transformBase64,
            "URL" to StringTransformer::transformUrl
        )

        actions.forEach { (label, func) ->
            btnPanel.add(JButton(label).apply {
                addActionListener { textArea.text = func(textArea.text) }
            })
        }

        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
        panel.add(btnPanel, BorderLayout.SOUTH)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

}
