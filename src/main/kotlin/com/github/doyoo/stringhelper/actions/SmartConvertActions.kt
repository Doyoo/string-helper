package com.github.doyoo.stringhelper.actions

import com.github.doyoo.stringhelper.utils.StringTransformer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction

/**
 * @Author: Aaron
 * @Date: 2026/02/25 00:19:46
 */
abstract class SmartConvertActions(private val transform: (String) -> String) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: return
        val result = transform(selectedText)

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, result)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

class UnicodeSmartAction : SmartConvertActions({ StringTransformer.transformUnicode(it) })
class Base64SmartAction : SmartConvertActions({ StringTransformer.transformBase64(it) })
class UrlSmartAction : SmartConvertActions({ StringTransformer.transformUrl(it) })
class MultipartAction : SmartConvertActions({ StringTransformer.transformMultipart(it) })