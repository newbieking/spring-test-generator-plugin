package com.newbieking.springtestgen.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.Visibility
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextField
import javax.swing.SwingConstants

/**
 * 非 Controller 目标（Service/Component/Repository/Mapper/Utility）的测试生成对话框。
 */
class ClassTestDialog(
    project: Project,
    private val metadata: ClassUnderTestMetadata
) : DialogWrapper(project) {

    private val testClassNameField = JTextField("${metadata.simpleName}Test", 30)

    init {
        title = "Generate Test for ${metadata.simpleName}"
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        var row = 0

        fun addRow(labelText: String, value: String) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            panel.add(JBLabel(labelText), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(JBLabel(value), gbc)
            row++
        }

        addRow("Target Type:", metadata.targetType.name)
        addRow("Source Class:", metadata.qualifiedName ?: metadata.simpleName)
        val pubCount = metadata.methods.count { it.visibility == Visibility.PUBLIC }
        addRow("Public Methods:", pubCount.toString())

        // separator
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(SwingConstants.HORIZONTAL), gbc)
        row++
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.HORIZONTAL

        // test class name input
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        panel.add(JBLabel("Test Class Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(testClassNameField, gbc)

        return panel
    }

    fun getTestClassName(): String = testClassNameField.text.trim()
}
