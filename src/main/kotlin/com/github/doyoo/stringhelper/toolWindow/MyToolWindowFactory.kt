package com.github.doyoo.stringhelper.toolWindow

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.icons.AllIcons
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val editorFactory = EditorFactory.getInstance()

        // --------------------------------------------------
        // Documents and editors
        // --------------------------------------------------

        val inputDocument = editorFactory.createDocument("")
        val outputDocument = editorFactory.createDocument("")

        val inputEditor = editorFactory.createEditor(
            inputDocument,
            project
        ) as EditorEx

        val outputEditor = editorFactory.createEditor(
            outputDocument,
            project
        ) as EditorEx

        inputEditor.apply {
            setPlaceholder("Enter text to transform...")
            settings.isLineNumbersShown = true
            settings.isFoldingOutlineShown = false
            settings.isLineMarkerAreaShown = false
            settings.isAutoCodeFoldingEnabled = false
            settings.isRightMarginShown = false
        }

        outputEditor.apply {
            settings.isLineNumbersShown = true
            settings.isFoldingOutlineShown = false
            settings.isLineMarkerAreaShown = false
            settings.isAutoCodeFoldingEnabled = false
            settings.isRightMarginShown = false
            isViewer = true
        }

        // --------------------------------------------------
        // Syntax highlighting
        // --------------------------------------------------

        fun setSyntaxHighlighting(
            editor: EditorEx,
            fileType: FileType
        ) {
            editor.highlighter =
                EditorHighlighterFactory.getInstance()
                    .createEditorHighlighter(
                        fileType,
                        editor.colorsScheme,
                        project
                    )
        }

        fun getInputFileType(
            mode: StringTransformer.TransformMode
        ): FileType {
            return when (mode) {
                StringTransformer.TransformMode.JSON,
                StringTransformer.TransformMode.JSONMinify ->
                    JsonFileType.INSTANCE

                else -> PlainTextFileType.INSTANCE
            }
        }

        // --------------------------------------------------
        // Mode selector
        // --------------------------------------------------

        val modeComboBox = ComboBox(
            StringTransformer.TransformMode.entries.toTypedArray()
        ).apply {
            toolTipText = "Select transformation mode"
        }

        setSyntaxHighlighting(
            inputEditor,
            getInputFileType(
                modeComboBox.selectedItem as StringTransformer.TransformMode
            )
        )

        setSyntaxHighlighting(
            outputEditor,
            PlainTextFileType.INSTANCE
        )

        // --------------------------------------------------
        // Output: text or QR
        // --------------------------------------------------

        val outputCardLayout = CardLayout()

        val qrPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
        }

        val qrImageLabel = JBLabel().apply {
            horizontalAlignment = JBLabel.CENTER
            verticalAlignment = JBLabel.CENTER
        }

        qrPanel.add(qrImageLabel, BorderLayout.CENTER)

        val outputContainer = JPanel(outputCardLayout).apply {
            add(outputEditor.component, "TEXT")
            add(qrPanel, "QR")
        }

        fun showTextMode() {
            outputCardLayout.show(outputContainer, "TEXT")
        }

        fun showQrMode(image: Image) {
            qrImageLabel.icon = ImageIcon(image)
            outputCardLayout.show(outputContainer, "QR")
        }

        // --------------------------------------------------
        // Status
        // --------------------------------------------------

        val statusLabel = JBLabel("Ready").apply {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        }

        // --------------------------------------------------
        // Transform
        // --------------------------------------------------

        fun transform() {
            val input = inputDocument.text

            if (input.isBlank()) {
                statusLabel.text = "Enter text to transform"
                return
            }

            val mode = modeComboBox.selectedItem
                    as StringTransformer.TransformMode

            setSyntaxHighlighting(
                inputEditor,
                getInputFileType(mode)
            )

            try {
                when (
                    val output = StringTransformer.transformWithErrors(
                        input,
                        mode
                    )
                ) {
                    is StringTransformer.TransformOutput.QR -> {
                        showQrMode(output.result.image)
                        statusLabel.text = "QR code"
                    }

                    is StringTransformer.TransformOutput.Text -> {
                        val (
                            finalText,
                            fileType,
                            typeName
                        ) = StringTransformer.detect(
                            output.result.text
                        )

                        WriteCommandAction.runWriteCommandAction(project) {
                            @Suppress("UsePropertyAccessSyntax")
                            outputDocument.setText(finalText)
                        }

                        setSyntaxHighlighting(
                            outputEditor,
                            fileType
                        )

                        StringTransformer.applyHighlights(
                            outputEditor,
                            output.result.errors
                        )

                        showTextMode()
                        statusLabel.text = typeName
                    }
                }
            } catch (e: Exception) {
                statusLabel.text = "Transformation failed: ${
                    e.message ?: "Unknown error"
                }"
            }
        }

        // --------------------------------------------------
        // Clipboard
        // --------------------------------------------------

        fun copyOutput() {
            val text = outputDocument.text

            if (text.isBlank()) {
                statusLabel.text = "Nothing to copy"
                return
            }

            val selection = StringSelection(text)

            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(selection, selection)

            statusLabel.text = "Copied to clipboard"
        }

        // --------------------------------------------------
        // Clear
        // --------------------------------------------------

        fun clearAll() {
            WriteCommandAction.runWriteCommandAction(project) {
                @Suppress("UsePropertyAccessSyntax")
                inputDocument.setText("")

                @Suppress("UsePropertyAccessSyntax")
                outputDocument.setText("")
            }

            showTextMode()

            setSyntaxHighlighting(
                inputEditor,
                getInputFileType(
                    modeComboBox.selectedItem
                            as StringTransformer.TransformMode
                )
            )

            setSyntaxHighlighting(
                outputEditor,
                PlainTextFileType.INSTANCE
            )

            statusLabel.text = "Ready"
        }

        // --------------------------------------------------
        // Mode changes
        // --------------------------------------------------

        modeComboBox.addActionListener {
            val mode = modeComboBox.selectedItem
                    as? StringTransformer.TransformMode
                ?: return@addActionListener

            setSyntaxHighlighting(
                inputEditor,
                getInputFileType(mode)
            )

            statusLabel.text = "Mode: $mode"
        }

        // --------------------------------------------------
        // Compact action toolbar
        // --------------------------------------------------

        val runAction = object : AnAction(
            "Run",
            "Transform input text",
            AllIcons.Actions.Execute
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                transform()
            }
        }

        val copyAction = object : AnAction(
            "Copy",
            "Copy output text",
            AllIcons.Actions.Copy
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                copyOutput()
            }
        }

        val clearAction = object : AnAction(
            "Clear",
            "Clear input and output",
            AllIcons.Actions.GC
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                clearAll()
            }
        }

        fun createToolbar(
            place: String,
            vararg actions: AnAction
        ): JComponent {
            val group = DefaultActionGroup().apply {
                actions.forEach(::add)
            }

            return ActionManager.getInstance()
                .createActionToolbar(
                    place,
                    group,
                    true
                )
                .apply {
                    targetComponent = inputEditor.component
                }
                .component
        }

        // --------------------------------------------------
        // Section header
        // --------------------------------------------------

        fun createSectionHeader(
            title: String,
            toolbar: JComponent? = null
        ): JPanel {
            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(
                    4, 8, 4, 4
                )

                add(
                    JBLabel(title).apply {
                        foreground =
                            JBUI.CurrentTheme.ContextHelp.FOREGROUND
                    },
                    BorderLayout.WEST
                )

                if (toolbar != null) {
                    add(toolbar, BorderLayout.EAST)
                }
            }
        }

        // --------------------------------------------------
        // Input section
        // --------------------------------------------------

        val inputToolbar = JPanel()

        val inputPanel = JPanel(BorderLayout()).apply {
            add(
                createSectionHeader(
                    "INPUT",
                    inputToolbar
                ),
                BorderLayout.NORTH
            )

            add(
                inputEditor.component,
                BorderLayout.CENTER
            )
        }

        // --------------------------------------------------
        // Output section
        // --------------------------------------------------

        val outputToolbar = createToolbar(
            "StringHelper.Output",
            copyAction
        )

        val outputPanel = JPanel(BorderLayout()).apply {
            add(
                createSectionHeader(
                    "OUTPUT",
                    outputToolbar
                ),
                BorderLayout.NORTH
            )

            add(
                outputContainer,
                BorderLayout.CENTER
            )
        }

        // --------------------------------------------------
        // Main toolbar
        // --------------------------------------------------

        val topBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)

            val left = JPanel(BorderLayout()).apply {
                add(modeComboBox, BorderLayout.CENTER)
            }

            val right = JPanel(BorderLayout()).apply {
                add(
                    createToolbar(
                        "StringHelper.Main",
                        runAction,
                        clearAction
                    ),
                    BorderLayout.CENTER
                )
            }

            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }

        // --------------------------------------------------
        // Splitter
        // --------------------------------------------------

        val splitter = OnePixelSplitter(
            true,
            0.5f
        ).apply {
            firstComponent = inputPanel
            secondComponent = outputPanel
            setHonorComponentsMinimumSize(true)
        }

        // --------------------------------------------------
        // Bottom status bar
        // --------------------------------------------------

        val statusBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)

            add(statusLabel, BorderLayout.WEST)
        }

        // --------------------------------------------------
        // Root
        // --------------------------------------------------

        val rootPanel = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
            add(statusBar, BorderLayout.SOUTH)
        }

        // --------------------------------------------------
        // Register content
        // --------------------------------------------------

        val content = ContentFactory.getInstance()
            .createContent(
                rootPanel,
                "",
                false
            )

        toolWindow.contentManager.addContent(content)

        // --------------------------------------------------
        // Dispose editors
        // --------------------------------------------------

        Disposer.register(content) {
            editorFactory.releaseEditor(inputEditor)
            editorFactory.releaseEditor(outputEditor)
        }
    }
}