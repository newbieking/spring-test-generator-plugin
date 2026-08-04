package com.newbieking.springtestgen.actions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.psi.SpringEndpointParser
import com.newbieking.springtestgen.ui.GenerateTestDialog
import com.newbieking.springtestgen.utils.PsiUtils
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 为当前方法生成测试脚本的 Action（同时作为 Gutter 图标的触发）
 */
class GenerateTestForMethodAction : AnAction() {

    private val log = Logger.getInstance(GenerateTestForMethodAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = editor.caretModel.primaryCaret.offset
        var targetMethod: PsiMethod? = null

        // 查找当前光标所在的 PsiMethod (Java)
        val element = psiFile.findElementAt(offset)
        targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (targetMethod == null && psiFile.language.id == "kotlin") {
            // Kotlin 处理：尝试通过 KtNamedFunction 查找
            val ktFunction = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            if (ktFunction != null) {
                Messages.showWarningDialog(
                    project,
                    "Kotlin method detected but Kotlin PSI support is limited. Please use Java for full support.",
                    "Spring Test Generator"
                )
            }
            return
        }

        if (targetMethod == null) return
        log.info("Generate test action invoked for method: ${targetMethod.name}")
        generateTestForMethod(project, targetMethod)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val element = psiFile.findElementAt(editor.caretModel.primaryCaret.offset) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (method != null) {
            val parser = SpringEndpointParser(PsiManager.getInstance(project))
            val containingClass = method.containingClass
            if (containingClass != null) {
                e.presentation.isEnabledAndVisible = parser.parseController(containingClass).any { it.method == method }
                return
            }
        }
        e.presentation.isEnabledAndVisible = false
    }

    /**
     * 为指定方法生成测试（可供 LineMarker 调用）
     */
    fun generateTestForMethod(project: Project, targetMethod: PsiMethod) {
        val parser = SpringEndpointParser(PsiManager.getInstance(project))
        val containingClass = targetMethod.containingClass ?: return
        val allEndpoints = parser.parseController(containingClass)
        val targetEndpoint = allEndpoints.find { it.method == targetMethod } ?: run {
            log.warn("No Spring endpoint metadata found for method: ${containingClass.qualifiedName}#${targetMethod.name}")
            return
        }

        val dialog = GenerateTestDialog(project, listOf(targetEndpoint))
        if (dialog.showAndGet()) {
            log.info("Starting generation for endpoint ${targetEndpoint.httpMethod} ${targetEndpoint.path}")
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project, "Generating Test Script", true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Analyzing endpoint and generating test..."
                    val generator = TestScriptGenerator(project)
                    val testClass = runBlocking {
                        generator.generateTestClass(
                            listOf(targetEndpoint),
                            dialog.getTestClassName(),
                            dialog.isAIEnabled()
                        )
                    }
                    log.info("Test source generated for ${targetEndpoint.httpMethod} ${targetEndpoint.path}")
                    ApplicationManager.getApplication().invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            writeTestFile(project, dialog.getTestClassName(), testClass)
                        }
                    }
                }
            })
        }
    }

    /**
     * 将生成的测试类写入 src/test/java 目录
     */
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
                    log.info("Updated generated test file: ${existingFile.virtualFile.path}")
                } else {
                    psiDir.createFile(fileName).let {
                        PsiUtils.setContent(it, content)
                        log.info("Created generated test file: ${it.virtualFile.path}")
                    }
                }
            }
        } else {
            log.warn("Unable to write generated test '$testClassName': src/test/java directory not found in ${project.basePath}")
            Messages.showWarningDialog(
                project,
                "Could not find src/test/java directory. Please ensure your project has a standard Maven/Gradle layout.",
                "Spring Test Generator"
            )
        }
    }

    // ----------------- Gutter 图标提供者 -----------------
    class LineMarkerProvider : com.intellij.codeInsight.daemon.LineMarkerProvider {
        override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
            val method = element as? PsiMethod ?: return null
            val project = element.project
            val parser = SpringEndpointParser(PsiManager.getInstance(project))
            val controller = method.containingClass ?: return null
            val endpoints = parser.parseController(controller)
            if (endpoints.none { it.method == method }) return null

            return LineMarkerInfo(
                method,
                method.nameIdentifier?.textRange ?: method.textRange,
                AllIcons.RunConfigurations.TestState.Run,
                { "Generate test for this endpoint" },
                { _, elt ->
                    GenerateTestForMethodAction().generateTestForMethod(project, elt)
                },
                GutterIconRenderer.Alignment.LEFT
            )
        }
    }
}
