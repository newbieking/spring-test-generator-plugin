package com.newbieking.springtestgen.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.psi.SpringEndpointParser
import com.newbieking.springtestgen.ui.GenerateTestDialog
import com.newbieking.springtestgen.utils.PsiUtils
import kotlinx.coroutines.runBlocking

/**
 * 为整个 Controller 生成所有端点的测试
 */
class GenerateTestForControllerAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.primaryCaret?.offset ?: 0
        val element = psiFile.findElementAt(offset)
        val targetClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return

        val parser = SpringEndpointParser(PsiManager.getInstance(project))
        val endpoints = parser.parseController(targetClass)
        if (endpoints.isEmpty()) return

        val dialog = GenerateTestDialog(project, endpoints)
        if (dialog.showAndGet()) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project, "Generating Tests for All Endpoints", true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Generating test class..."
                    val generator = TestScriptGenerator(project)
                    val testClass = runBlocking {
                        generator.generateTestClass(
                            endpoints,
                            dialog.getTestClassName(),
                            dialog.isAIEnabled()
                        )
                    }
                    ApplicationManager.getApplication().invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            writeTestFile(project, dialog.getTestClassName(), testClass)
                        }
                    }
                }
            })
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (project == null || psiFile == null || editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val element = psiFile.findElementAt(editor.caretModel.primaryCaret.offset) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val targetClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (targetClass != null) {
            val parser = SpringEndpointParser(PsiManager.getInstance(project))
            e.presentation.isEnabledAndVisible = parser.parseController(targetClass).isNotEmpty()
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    private fun writeTestFile(
        project: Project,
        testClassName: String,
        content: String
    ) {
        val baseDir = project.baseDir
        val testSrcDir = baseDir.findChild("src")?.findChild("test")?.findChild("java")
        if (testSrcDir != null) {
            val psiDir = PsiManager.getInstance(project).findDirectory(testSrcDir)
            if (psiDir != null) {
                val fileName = "$testClassName.java"
                val existingFile = psiDir.findFile(fileName)
                if (existingFile != null) {
                    PsiUtils.setContent(existingFile, content)
                } else {
                    psiDir.createFile(fileName).let { PsiUtils.setContent(it, content) }
                }
            }
        } else {
            Messages.showWarningDialog(
                project,
                "Could not find src/test/java directory.",
                "Spring Test Generator"
            )
        }
    }
}