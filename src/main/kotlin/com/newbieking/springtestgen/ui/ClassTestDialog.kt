package com.newbieking.springtestgen.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * 非 Controller 目标（Service/Component/Repository/Mapper/Utility）的测试生成对话框。
 * 仅收集测试类名，不涉及 AI 开关（非 Controller 使用确定性生成）。
 */
class ClassTestDialog(
    project: Project,
    private val metadata: ClassUnderTestMetadata
) : DialogWrapper(project) {

    private val testClassNameField = JTextField(
        "${metadata.simpleName}Test",
        30
    )

    init {
        title = "Generate Test for ${metadata.simpleName}"
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        group("Test Class Configuration") {
            row("Target Type:") { label(metadata.targetType.name) }
            row("Source Class:") { label(metadata.qualifiedName ?: metadata.simpleName) }
            row("Public Methods:") {
                label(metadata.methods.count { it.visibility == com.newbieking.springtestgen.model.Visibility.PUBLIC }.toString())
            }
            row("Test Class Name:") {
                cell(testClassNameField)
                    .comment("The generated test class will be placed in src/test/java under ${metadata.packageName}.test")
            }
        }
    }

    fun getTestClassName(): String = testClassNameField.text.trim()
}
