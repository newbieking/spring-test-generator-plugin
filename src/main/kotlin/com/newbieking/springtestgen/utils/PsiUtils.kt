package com.newbieking.springtestgen.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile

/**
 * PSI 辅助工具
 */
object PsiUtils {
    /**
     * 将内容写入 PsiFile。调用方需自行确保已在 WriteCommandAction 内执行。
     */
    fun setContent(psiFile: PsiFile, content: String) {
        val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)
        document?.setText(content)
    }
}