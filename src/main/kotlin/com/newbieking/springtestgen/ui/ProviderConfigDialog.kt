package com.newbieking.springtestgen.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.newbieking.springtestgen.services.SettingsService
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Dialog for adding or editing an AI provider configuration.
 *
 * When [existingState] is provided, the dialog operates in edit mode
 * with the ID field disabled. Otherwise, it creates a new provider.
 */
class ProviderConfigDialog(
    private val existingState: SettingsService.ProviderState? = null
) : DialogWrapper(null) {

    private val idField = JBTextField()
    private val displayNameField = JBTextField()
    private val baseUrlField = JBTextField()
    private val apiKeyField = JBTextField()
    private val modelField = JBTextField()
    private val timeoutField = JBTextField()
    private val retriesField = JBTextField()
    private val priorityField = JBTextField()
    private val enabledCheckBox = JBCheckBox("Enabled", true)

    init {
        title = if (existingState != null) "Edit AI Provider" else "Add AI Provider"
        init()

        // Pre-populate fields when editing
        existingState?.let { state ->
            idField.text = state.id
            displayNameField.text = state.displayName
            baseUrlField.text = state.baseUrl
            apiKeyField.text = state.apiKey
            modelField.text = state.model
            timeoutField.text = state.timeoutSeconds.toString()
            retriesField.text = state.maxRetries.toString()
            priorityField.text = state.priority.toString()
            enabledCheckBox.isSelected = state.enabled
            idField.isEnabled = false // Cannot change ID of existing provider
        } ?: run {
            // Default values for new provider
            timeoutField.text = "30"
            retriesField.text = "1"
            priorityField.text = "0"
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 8, 4, 8)
        }

        var row = 0

        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0
            panel.add(component, gbc)
            row++
        }

        addRow("Provider ID:", idField)
        addRow("Display Name:", displayNameField)
        addRow("Base URL:", baseUrlField)
        addRow("API Key:", apiKeyField)
        addRow("Model:", modelField)
        addRow("Timeout (seconds):", timeoutField)
        addRow("Max Retries:", retriesField)
        addRow("Priority (lower = higher):", priorityField)

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(enabledCheckBox, gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.CENTER)
        return wrapper
    }

    override fun doValidate(): ValidationInfo? {
        if (idField.text.isBlank()) {
            return ValidationInfo("Provider ID is required", idField)
        }
        if (displayNameField.text.isBlank()) {
            return ValidationInfo("Display name is required", displayNameField)
        }
        if (baseUrlField.text.isBlank()) {
            return ValidationInfo("Base URL is required", baseUrlField)
        }
        timeoutField.text.toIntOrNull()?.let {
            if (it <= 0) return ValidationInfo("Timeout must be positive", timeoutField)
        } ?: return ValidationInfo("Timeout must be a number", timeoutField)

        retriesField.text.toIntOrNull()?.let {
            if (it < 1) return ValidationInfo("Retries must be at least 1", retriesField)
        } ?: return ValidationInfo("Retries must be a number", retriesField)

        priorityField.text.toIntOrNull()
            ?: return ValidationInfo("Priority must be a number", priorityField)

        return null
    }

    /**
     * Build a ProviderState from the dialog fields.
     * Should only be called after [doValidate] succeeds (i.e., when OK is clicked).
     */
    fun getProviderState(): SettingsService.ProviderState = SettingsService.ProviderState(
        id = idField.text.trim(),
        displayName = displayNameField.text.trim(),
        baseUrl = baseUrlField.text.trim(),
        apiKey = apiKeyField.text.trim(),
        model = modelField.text.trim(),
        timeoutSeconds = timeoutField.text.trim().toInt(),
        maxRetries = retriesField.text.trim().toInt(),
        priority = priorityField.text.trim().toInt(),
        enabled = enabledCheckBox.isSelected
    )
}