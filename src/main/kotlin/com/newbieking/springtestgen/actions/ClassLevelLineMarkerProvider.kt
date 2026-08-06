package com.newbieking.springtestgen.actions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.newbieking.springtestgen.generator.ClassTestScriptGenerator
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.psi.ClassUnderTestParser
import com.newbieking.springtestgen.psi.SpringEndpointParser
import com.newbieking.springtestgen.ui.GenerateTestDialog
import com.newbieking.springtestgen.utils.TestFileWriter
import kotlinx.coroutines.runBlocking

/**
 * 统一的类级别 Gutter Icon 提供者。
 *
 * 对所有 TargetType 一视同仁 —— Controller、Service、Component、
 * Repository、Mapper、Utility —— 不做 Controller / 非 Controller 的二分区分。
 *
 * 在类声明处显示 Gutter Icon，点击后弹出统一的 [GenerateTestDialog]，
 * 根据目标类型动态显示配置选项（Controller 显示框架/AI 选项，其他类型显示摘要信息），
 * 确认后自动路由到对应的生成器。
 *
 * 注意：方法级别的 Gutter Icon（单个端点）由 GenerateTestForMethodAction.LineMarkerProvider 继续提供，
 *       与此类级别的 Provider 互不干扰。
 */
class ClassLevelLineMarkerProvider : com.intellij.codeInsight.daemon.LineMarkerProvider {

    private val log = Logger.getInstance(ClassLevelLineMarkerProvider::class.java)

    /** 轻量级注解检查：仅判断类别，不触发完整的 PSI 解析。 */
    private fun detectTargetType(psiClass: PsiClass): TargetType {
        val annotations = psiClass.annotations.mapNotNull { it.qualifiedName }.toSet()
        return when {
            annotations.any { it in CONTROLLER_ANNOTATIONS } -> TargetType.CONTROLLER
            annotations.any { it in SERVICE_ANNOTATIONS } -> TargetType.SERVICE
            annotations.any { it in COMPONENT_ANNOTATIONS } -> TargetType.COMPONENT
            annotations.any { it in MAPPER_ANNOTATIONS } -> TargetType.MAPPER
            annotations.any { it in REPOSITORY_ANNOTATIONS } || isRepositoryInterface(psiClass) -> TargetType.REPOSITORY
            annotations.any { it in KNOWN_UNSUPPORTED_ANNOTATIONS } -> TargetType.UNSUPPORTED
            psiClass.isInterface -> TargetType.UNSUPPORTED
            else -> TargetType.UTILITY
        }
    }

    /** 检查是否为 Spring Data Repository 接口（含继承链）。 */
    private fun isRepositoryInterface(psiClass: PsiClass, visited: Set<String> = emptySet()): Boolean {
        if (!psiClass.isInterface) return false
        val qName = psiClass.qualifiedName
        if (qName != null && qName in visited) return false
        val nextVisited = qName?.let { visited + it } ?: visited
        val directSuperNames = psiClass.superTypes.map { it.canonicalText.substringBefore('<') }
        if (directSuperNames.any { it in REPOSITORY_SUPER_TYPES || it.startsWith("org.springframework.data.repository.") }) return true
        return psiClass.supers.any { superClass ->
            val superQName = superClass.qualifiedName
            (superQName in REPOSITORY_SUPER_TYPES || (superQName != null && superQName.startsWith("org.springframework.data.repository."))) ||
                isRepositoryInterface(superClass, nextVisited)
        }
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val psiClass = element as? PsiClass ?: return null
        if (psiClass.nameIdentifier == null) return null

        val targetType = detectTargetType(psiClass)
        if (targetType == TargetType.UNSUPPORTED) return null

        return LineMarkerInfo(
            psiClass,
            psiClass.nameIdentifier!!.textRange,
            AllIcons.RunConfigurations.TestState.Run,
            { "Generate tests for $targetType" },
            { _, elt ->
                handleGenerate(elt.project, elt, targetType)
            },
            GutterIconRenderer.Alignment.LEFT
        )
    }

    /** 统一的点击处理入口，使用 [GenerateTestDialog] 完成配置后路由到对应生成器。 */
    private fun handleGenerate(project: Project, psiClass: PsiClass, targetType: TargetType) {
        // 使用 ClassUnderTestParser 获取完整元数据
        val parser = ClassUnderTestParser()
        val metadata = parser.parse(psiClass)

        when (targetType) {
            TargetType.CONTROLLER -> handleController(project, psiClass)
            else -> handleNonController(project, metadata)
        }
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

    private fun handleNonController(project: Project, metadata: com.newbieking.springtestgen.model.ClassUnderTestMetadata) {
        if (metadata.targetType == TargetType.UNSUPPORTED) {
            Messages.showInfoMessage(
                project,
                "${metadata.simpleName} (${metadata.targetType}) is not supported for automatic test generation.\n\n" +
                    "Reason: ${metadata.unsupportedReason ?: "No applicable test strategy."}",
                "Spring Test Generator"
            )
            return
        }

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

    companion object {
        // 以下常量与 ClassUnderTestParser 中的注解集合保持一致
        private val CONTROLLER_ANNOTATIONS = setOf(
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.Controller"
        )
        private val SERVICE_ANNOTATIONS = setOf("org.springframework.stereotype.Service")
        private val COMPONENT_ANNOTATIONS = setOf("org.springframework.stereotype.Component")
        private val REPOSITORY_ANNOTATIONS = setOf("org.springframework.stereotype.Repository")
        private val MAPPER_ANNOTATIONS = setOf(
            "org.apache.ibatis.annotations.Mapper",
            "org.mapstruct.Mapper"
        )
        private val REPOSITORY_SUPER_TYPES = setOf(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository"
        )
        private val KNOWN_UNSUPPORTED_ANNOTATIONS = setOf(
            "org.springframework.context.annotation.Configuration",
            "org.springframework.stereotype.ControllerAdvice",
            "org.springframework.web.bind.annotation.RestControllerAdvice",
            "org.aspectj.lang.annotation.Aspect"
        )
    }
}