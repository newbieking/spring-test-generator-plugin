package com.newbieking.springtestgen.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.services.SettingsService
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * 生成测试脚本的配置对话框
 */
class GenerateTestDialog(
    private val project: Project,
    private val endpoints: List<EndpointMetadata> // 如果批量，则多个；否则单个
) : DialogWrapper(project) {

    private val testClassNameField = JBTextField()
    private val enableAICheckBox = JBCheckBox("Enable AI data generation", true)
    private val frameworkCombo = JComboBox(arrayOf("MockMvc", "RestAssured", "WebTestClient"))

    init {
        title = "Generate Spring Test Script"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))

        val formPanel = JPanel(GridLayout(0, 2, 5, 5))
        formPanel.add(JLabel("Test Class Name:"))
        testClassNameField.text = suggestTestClassName()
        formPanel.add(testClassNameField)

        formPanel.add(JLabel("Test Framework:"))
        formPanel.add(frameworkCombo)

        formPanel.add(JLabel("Options:"))
        formPanel.add(enableAICheckBox)

        panel.add(formPanel, BorderLayout.CENTER)

        // 从设置中加载 AI 开关状态
        val settings = SettingsService.getInstance(project)
        enableAICheckBox.isSelected = settings.getConfig().enableAI

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val name = testClassNameField.text
        return if (name.isBlank()) {
            ValidationInfo("Test class name cannot be empty", testClassNameField)
        } else null
    }

    private fun suggestTestClassName(): String {
        // 根据第一个端点 Controller 类名生成
        val controller = endpoints.firstOrNull()?.controllerClass
        return if (controller != null) "${controller.name}Test" else "ControllerTest"
    }

    // 提供 getter 供 Action 调用
    fun getTestClassName(): String = testClassNameField.text
    fun isAIEnabled(): Boolean = enableAICheckBox.isSelected
    fun getSelectedFramework(): String = frameworkCombo.selectedItem as String
}