package com.newbieking.springtestgen.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.newbieking.springtestgen.services.SettingsService
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 设置页面（Settings -> Tools -> Spring Test Generator）
 */
class SettingsConfigurable(private val project: Project) : Configurable {

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBTextField()
    private val modelField = JBTextField()
    private var settings: SettingsService? = null

    override fun getDisplayName(): String = "Spring Test Generator"

    override fun createComponent(): JComponent {
        settings = SettingsService.getInstance(project)
        val current = settings!!.getConfig()
        baseUrlField.text = current.baseUrl
        apiKeyField.text = current.apiKey
        modelField.text = current.model

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Base URL:", baseUrlField)
            .addLabeledComponent("API Key:", apiKeyField)
            .addLabeledComponent("Model Name:", modelField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val current = settings!!.getConfig()
        return baseUrlField.text != current.baseUrl ||
                apiKeyField.text != current.apiKey ||
                modelField.text != current.model
    }

    override fun apply() {
        settings!!.setConfig(
            baseUrlField.text,
            apiKeyField.text,
            modelField.text,
            true // 默认启用 AI
        )
    }

    override fun reset() {
        val current = settings!!.getConfig()
        baseUrlField.text = current.baseUrl
        apiKeyField.text = current.apiKey
        modelField.text = current.model
    }
}