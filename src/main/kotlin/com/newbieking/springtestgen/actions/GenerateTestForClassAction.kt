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
import com.newbieking.springtestgen.utils.DiagnosticLogger
import com.newbieking.springtestgen.utils.TestFileWriter

/**
 * 为非 Controller 目标（Service / Component / Repository / Mapper / Utility）
 * 右键生成确定性 JUnit 5 测试。
 *
 * 对于 UNSUPPORTED 类型（@Configuration 等），菜单项保持可见但给出友好提示。
 */
class GenerateTestForClassAction : AnAction() {

    /**
     * 延迟初始化以隔离类加载失败。
     * 如果 eager init 中某个依赖类导致 NoClassDefFoundError，
     * 整个 Action 将永远不会被平台注册，update() 也永远不会被调用。
     * 改用 lazy 可确保类加载成功，仅在实际调用时暴露初始化错误。
     */
    private val log by lazy { Logger.getInstance(GenerateTestForClassAction::class.java) }
    private val parser by lazy { ClassUnderTestParser() }
    private val generator by lazy { ClassTestScriptGenerator() }

    /**
     * 轻量级 Controller 判断，仅检查注解，不触发完整 PSI 解析。
     */
    private fun isControllerClass(psiClass: PsiClass): Boolean =
        psiClass.getAnnotation("org.springframework.web.bind.annotation.RestController") != null ||
            psiClass.getAnnotation("org.springframework.stereotype.Controller") != null

    /** 能被 ClassTestScriptGenerator 直接处理的目标类型 */
    private companion object {
        val GENERATABLE_TYPES: Set<TargetType> = setOf(
            TargetType.SERVICE,
            TargetType.COMPONENT,
            TargetType.REPOSITORY,
            TargetType.MAPPER,
            TargetType.UTILITY
        )

        init {
            try {
                DiagnosticLogger.log("[GenerateTestForClass] COMPANION INIT - class is being loaded")
            } catch (_: Throwable) {
                // DiagnosticLogger broke — fall back to file
                try {
                    java.io.FileWriter(System.getProperty("user.home") + "/.spring-test-gen/diagnostic.log", true).use {
                        it.write("[GenerateTestForClass] COMPANION INIT via FileWriter\n")
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    /** 类体 init：实例化时必定触发（比 companion init 更可靠） */
    init {
        try {
            DiagnosticLogger.log("[GenerateTestForClass] INSTANCE INIT")
        } catch (_: Throwable) { }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val actionId = "GenerateTestForClass"
        try {
            DiagnosticLogger.log("[$actionId] update() called | thread=${Thread.currentThread().name}")

            val project = e.project
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)
            val editor = e.getData(CommonDataKeys.EDITOR)
            DiagnosticLogger.log("[$actionId] project=${project?.name ?: "null"} psiFile=${psiFile?.name ?: "null"} editor=${editor != null}")
            if (project == null || psiFile == null || editor == null) {
                DiagnosticLogger.log("[$actionId] -> HIDDEN: missing project/psiFile/editor")
                e.presentation.isEnabledAndVisible = false
                return
            }

            val element = psiFile.findElementAt(editor.caretModel.primaryCaret.offset)
            if (element == null) {
                DiagnosticLogger.log("[$actionId] -> HIDDEN: no element at offset=${editor.caretModel.primaryCaret.offset}")
                e.presentation.isEnabledAndVisible = false
                return
            }
            DiagnosticLogger.log("[$actionId] element at caret: ${element.javaClass.simpleName} text='${element.text.take(50)}'")

            val targetClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            if (targetClass == null) {
                DiagnosticLogger.log("[$actionId] -> HIDDEN: no PsiClass parent")
                e.presentation.isEnabledAndVisible = false
                return
            }

            val isCtrl = isControllerClass(targetClass)
            val annotations = targetClass.annotations.map { it.qualifiedName ?: it.text }
            DiagnosticLogger.log("[$actionId] class=${targetClass.qualifiedName ?: targetClass.name} isController=$isCtrl annotations=$annotations")
            DiagnosticLogger.log("[$actionId] visible=${!isCtrl} enabled=${!isCtrl}")
            e.presentation.isEnabledAndVisible = !isCtrl
        } catch (t: Throwable) {
            DiagnosticLogger.log("[$actionId] update() ERROR: ${t.javaClass.name}: ${t.message}")
            e.presentation.isEnabledAndVisible = false
        }
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
