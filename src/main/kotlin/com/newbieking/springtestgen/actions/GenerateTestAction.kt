package com.newbieking.springtestgen.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.newbieking.springtestgen.generator.ClassTestScriptGenerator
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.psi.ClassUnderTestParser
import com.newbieking.springtestgen.psi.SpringEndpointParser
import com.newbieking.springtestgen.ui.GenerateTestDialog
import com.newbieking.springtestgen.utils.TestFileWriter
import kotlinx.coroutines.runBlocking

/**
 * 统一的测试生成 Action。
 *
 * 对所有 Spring bean 类型一视同仁（Controller / Service / Component / Repository / Mapper / Utility），
 * 点击后在统一的 [GenerateTestDialog] 中选择配置选项（测试类名、框架、AI 等），
 * 确认后自动路由到对应的生成器：
 * - Controller → [TestScriptGenerator]（MockMvc / RestAssured / WebTestClient）
 * - 其他类型   → [ClassTestScriptGenerator]（JUnit 5 + Mockito）
 */
class GenerateTestAction : AnAction() {

    private val log = Logger.getInstance(GenerateTestAction::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val psiClass = getPsiClassFromEvent(event) ?: run {
            Messages.showWarningDialog(project, "No Java/Kotlin class selected.", "Spring Test Generator")
            return
        }

        // 使用 ClassUnderTestParser 统一判定类型
        val parser = ClassUnderTestParser()
        val metadata = parser.parse(psiClass)

        when (metadata.targetType) {
            TargetType.UNSUPPORTED -> {
                Messages.showInfoMessage(
                    project,
                    "${metadata.simpleName} (${metadata.targetType}) is not supported for automatic test generation.\n\n" +
                        "Reason: ${metadata.unsupportedReason ?: "No applicable test strategy."}",
                    "Spring Test Generator"
                )
            }
            TargetType.CONTROLLER -> handleController(project, psiClass)
            else -> handleNonController(project, metadata)
        }
    }

    override fun update(event: AnActionEvent) {
        val psiClass = getPsiClassFromEvent(event)
        event.presentation.isEnabledAndVisible = psiClass != null
    }

    // ---- Controller 路径 ----

    private fun handleController(project: Project, psiClass: PsiClass) {
        val parser = SpringEndpointParser(PsiManager.getInstance(project))
        val endpoints = parser.parseController(psiClass)
        if (endpoints.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "No Spring MVC endpoints found in ${psiClass.qualifiedName ?: psiClass.name}.",
                "Spring Test Generator"
            )
            return
        }

        val dialog = GenerateTestDialog(project)
        dialog.configureForController(psiClass.name ?: "Controller", endpoints.size)
        if (!dialog.showAndGet()) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Tests for ${psiClass.name}", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Generating test class..."
                val generator = TestScriptGenerator(project)
                val testClass = runBlocking {
                    generator.generateTestClass(endpoints, dialog.getTestClassName(), dialog.isAIEnabled())
                }
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

    // ---- 非 Controller 路径 ----

    private fun handleNonController(project: Project, metadata: ClassUnderTestMetadata) {
        val dialog = GenerateTestDialog(project)
        dialog.configureForClass(metadata)
        if (!dialog.showAndGet()) return

        val testClassName = dialog.getTestClassName()
        if (testClassName.isBlank()) {
            Messages.showWarningDialog(project, "Test class name cannot be empty.", "Spring Test Generator")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Test for ${metadata.simpleName}", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing class and generating test..."
                val generator = ClassTestScriptGenerator()
                val testClass = generator.generateTestClass(metadata, testClassName)
                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        TestFileWriter.writeTestFile(
                            project,
                            testClassName,
                            TestFileWriter.deriveTestPackageName(metadata.qualifiedName),
                            testClass
                        )
                    }
                }
            }
        })
    }

    /** 从 ActionEvent 中提取当前光标所在的 Java/Kotlin 类。 */
    private fun getPsiClassFromEvent(event: AnActionEvent): PsiClass? {
        // 优先从 Editor 的 caret 位置获取
        val editor = event.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null
            val element = psiFile.findElementAt(editor.caretModel.offset) ?: return null
            val psiClass = findContainingClass(element)
            if (psiClass != null) return psiClass
        }

        // 回退：从 Project View 导航选择中获取
        val navElements = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        if (navElements != null) {
            for (nav in navElements) {
                val psiClass = nav as? PsiClass ?: continue
                return psiClass
            }
        }

        return null
    }

    private fun findContainingClass(element: PsiElement): PsiClass? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiClass) return current
            current = current.parent
        }
        return null
    }
}