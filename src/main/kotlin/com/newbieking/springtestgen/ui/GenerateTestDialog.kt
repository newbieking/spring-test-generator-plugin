package com.newbieking.springtestgen.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.services.SettingsService
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * 统一的测试生成配置对话框。
 *
 * 对所有 bean 类型（Controller / Service / Component / Repository / Mapper / Utility）
 * 提供完全一致的配置入口：
 * - 摘要信息区：目标类型、源类、公开方法数
 * - 配置选项区：测试类名、测试框架、AI 数据生成
 *
 * Controller 可选框架：MockMvc / RestAssured / WebTestClient
 * 其他类型可选框架：JUnit 5 + Mockito（当前唯一选项）
 */
class GenerateTestDialog(
    private val project: Project
) : DialogWrapper(project) {

    private val testClassNameField = JBTextField(30)
    private val enableAICheckBox = JBCheckBox("Enable AI data generation", true)

    /** 框架选项列表，在 [configureForController] / [configureForClass] 中按类型填充 */
    private val frameworkCombo = JComboBox<String>()

    private var metadata: ClassUnderTestMetadata? = null
    private var isController: Boolean = false
    private var methodCount: Int = 0

    init {
        title = "Generate Spring Test"
        isModal = true
    }

    /** 为 Controller 端点配置对话框。 */
    fun configureForController(className: String, endpointCount: Int) {
        isController = true
        methodCount = endpointCount
        testClassNameField.text = "${className}Test"
        title = "Generate Test for $className"
        frameworkCombo.removeAllItems()
        listOf("MockMvc", "RestAssured", "WebTestClient").forEach { frameworkCombo.addItem(it) }
        frameworkCombo.selectedItem = "MockMvc"
        init()
    }

    /** 为非 Controller 目标配置对话框。 */
    fun configureForClass(metadata: ClassUnderTestMetadata) {
        isController = false
        this.metadata = metadata
        methodCount = metadata.methods.count { it.visibility == com.newbieking.springtestgen.model.Visibility.PUBLIC }
        testClassNameField.text = "${metadata.simpleName}Test"
        title = "Generate Test for ${metadata.simpleName}"
        frameworkCombo.removeAllItems()
        listOf("JUnit 5 + Mockito").forEach { frameworkCombo.addItem(it) }
        frameworkCombo.selectedItem = "JUnit 5 + Mockito"
        init()
    }

    /** 兼容旧 Controller 批量端点 API */
    @Deprecated("Use configureForController or configureForClass instead", ReplaceWith("configureForController"))
    constructor(project: Project, endpoints: List<EndpointMetadata>) : this(project) {
        val controller = endpoints.firstOrNull()
        val className = controller?.controllerClass?.name ?: "Controller"
        configureForController(className, endpoints.size)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))

        // ---- 摘要信息区域（所有类型统一展示） ----
        panel.add(buildInfoPanel(), BorderLayout.NORTH)

        // ---- 配置选项区域（所有类型统一展示） ----
        panel.add(buildFormPanel(), BorderLayout.CENTER)

        return panel
    }

    private fun buildInfoPanel(): JPanel {
        val infoPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        var row = 0

        val simpleName = if (isController) testClassNameField.text.removeSuffix("Test") else metadata!!.simpleName
        val qualifiedName = if (isController) null else metadata!!.qualifiedName
        val targetTypeName = if (isController) TargetType.CONTROLLER.name else metadata!!.targetType.name

        addRow(infoPanel, gbc, "Target Type:", targetTypeName, row++)
        addRow(infoPanel, gbc, "Source Class:", qualifiedName ?: simpleName, row++)

        val methodLabel = if (isController) "Endpoints:" else "Public Methods:"
        addRow(infoPanel, gbc, methodLabel, methodCount.toString(), row)

        return infoPanel
    }

    private fun buildFormPanel(): JPanel {
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        var row = 0

        // 测试类名（所有类型通用）
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        formPanel.add(JLabel("Test Class Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        formPanel.add(testClassNameField, gbc)
        row++

        // 测试框架选择（所有类型通用）
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        formPanel.add(JLabel("Test Framework:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        formPanel.add(frameworkCombo, gbc)
        row++

        // AI 开关（所有类型通用）
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        formPanel.add(JLabel("Options:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        val settings = SettingsService.getInstance(project)
        enableAICheckBox.isSelected = settings.getConfig().enableAI
        formPanel.add(enableAICheckBox, gbc)

        return formPanel
    }

    private fun addRow(panel: JPanel, gbc: GridBagConstraints, labelText: String, value: String, row: Int) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JBLabel(labelText), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(JBLabel(value), gbc)
    }

    override fun doValidate(): ValidationInfo? {
        val name = testClassNameField.text
        return if (name.isBlank()) {
            ValidationInfo("Test class name cannot be empty", testClassNameField)
        } else null
    }

    // ---- 公共访问器 ----

    fun getTestClassName(): String = testClassNameField.text.trim()
    fun isAIEnabled(): Boolean = enableAICheckBox.isSelected
    fun getSelectedFramework(): String = frameworkCombo.selectedItem as String
    fun isControllerMode(): Boolean = isController
}