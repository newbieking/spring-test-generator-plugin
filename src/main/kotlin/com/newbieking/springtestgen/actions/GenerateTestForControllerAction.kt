package com.newbieking.springtestgen.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.psi.SpringEndpointParser
import com.newbieking.springtestgen.ui.GenerateTestDialog
import com.newbieking.springtestgen.utils.TestFileWriter
import kotlinx.coroutines.runBlocking

/**
 * 为整个 Controller 生成所有端点的测试
 */
class GenerateTestForControllerAction : AnAction() {

    private val log = Logger.getInstance(GenerateTestForControllerAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.primaryCaret?.offset ?: 0
        val element = psiFile.findElementAt(offset)
        val targetClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return

        val parser = SpringEndpointParser(PsiManager.getInstance(project))
        val endpoints = parser.parseController(targetClass)
        if (endpoints.isEmpty()) {
            log.warn("No Spring endpoints found in class: ${targetClass.qualifiedName ?: targetClass.name}")
            return
        }
        log.info("Generate tests action invoked for controller: ${targetClass.qualifiedName ?: targetClass.name} (${endpoints.size} endpoint(s))")

        val dialog = GenerateTestDialog(project, endpoints)
        if (dialog.showAndGet()) {
            log.info("Starting test generation for ${endpoints.size} endpoint(s)")
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
                    log.info("Test source generated for ${endpoints.size} endpoint(s)")
                    ApplicationManager.getApplication().invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            TestFileWriter.writeTestFile(
                                project,
                                dialog.getTestClassName(),
                                TestFileWriter.deriveTestPackageName(endpoints.first().controllerQualifiedName),
                                testClass
                            )
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
}