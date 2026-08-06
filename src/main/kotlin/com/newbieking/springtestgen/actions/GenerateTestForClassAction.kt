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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.newbieking.springtestgen.generator.ClassTestScriptGenerator
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.psi.ClassUnderTestParser
import com.newbieking.springtestgen.ui.ClassTestDialog
import com.newbieking.springtestgen.utils.TestFileWriter

/**
 * 为非 Controller 目标（Service / Component / Repository / Mapper / Utility）
 * 右键生成确定性 JUnit 5 测试。
 *
 * 对于 UNSUPPORTED 类型（@Configuration 等），菜单项保持可见但给出友好提示。
 */
class GenerateTestForClassAction : AnAction() {

    private val log = Logger.getInstance(GenerateTestForClassAction::class.java)
    private val parser = ClassUnderTestParser()
    private val generator = ClassTestScriptGenerator()

    /** 能被 ClassTestScriptGenerator 直接处理的目标类型 */
    private companion object {
        val GENERATABLE_TYPES: Set<TargetType> = setOf(
            TargetType.SERVICE,
            TargetType.COMPONENT,
            TargetType.REPOSITORY,
            TargetType.MAPPER,
            TargetType.UTILITY
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
        if (targetClass == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val metadata = parser.parse(targetClass)
        // 仅对非 Controller 的非 UNSUPPORTED 类型显示菜单项
        e.presentation.isEnabledAndVisible = metadata.targetType != TargetType.CONTROLLER
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.primaryCaret?.offset ?: 0
        val element = psiFile.findElementAt(offset) ?: return
        val targetClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return

        val metadata = parser.parse(targetClass)

        when {
            metadata.targetType == TargetType.UNSUPPORTED -> {
                showUnsupportedNotification(project, metadata)
            }
            metadata.targetType in GENERATABLE_TYPES -> {
                generateTestForClass(project, metadata)
            }
            else -> {
                // 防御性分支（CONTROLLER 被 update 隐藏后理论上不会到这里）
                log.warn("Unexpected target type in actionPerformed: ${metadata.targetType}")
            }
        }
    }

    /**
     * UNSUPPORTED 类型：弹框告知用户原因。
     */
    private fun showUnsupportedNotification(
        project: Project,
        metadata: com.newbieking.springtestgen.model.ClassUnderTestMetadata
    ) {
        Messages.showInfoMessage(
            project,
            "${metadata.simpleName} (${metadata.targetType}) 不支持自动生成测试。\n\n" +
                "原因：${metadata.unsupportedReason ?: "该类型暂无适用的测试策略。"}",
            "Spring Test Generator"
        )
    }

    /**
     * 为非 Controller 目标生成并写入测试类。
     */
    private fun generateTestForClass(
        project: Project,
        metadata: com.newbieking.springtestgen.model.ClassUnderTestMetadata
    ) {
        log.info("Generate test action invoked for ${metadata.targetType}: ${metadata.qualifiedName}")

        // 构造一个虚拟 project 给 dialog（实际上 ClassTestDialog 构造函数有个问题）
        val dialog = ClassTestDialog(project, metadata)
        if (!dialog.showAndGet()) return

        val testClassName = dialog.getTestClassName()
        if (testClassName.isBlank()) {
            Messages.showWarningDialog(
                project,
                "Test class name cannot be empty.",
                "Spring Test Generator"
            )
            return
        }

        log.info("Starting test generation for ${metadata.targetType} ${metadata.qualifiedName} -> $testClassName")
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Test for ${metadata.simpleName}", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing class and generating test..."
                val testClass = generator.generateTestClass(metadata, testClassName)
                log.info("Test source generated for ${metadata.targetType} ${metadata.qualifiedName}")
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
}
