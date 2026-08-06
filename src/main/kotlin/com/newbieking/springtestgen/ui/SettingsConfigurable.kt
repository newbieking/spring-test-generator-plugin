package com.newbieking.springtestgen.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.newbieking.springtestgen.services.GenerationMode
import com.newbieking.springtestgen.services.SettingsService
import com.newbieking.springtestgen.services.TestFrameworkOption
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JComboBox

/**
 * Settings page (Settings -> Tools -> Spring Test Generator)
 *
 * Provides configuration for AI provider, generation mode, test framework,
 * and scenario toggles.
 */
class SettingsConfigurable(private val project: Project) : Configurable {

    // --- AI Provider ---
    private val baseUrlField = JBTextField()
    private val apiKeyField = JBTextField()
    private val modelField = JBTextField()
    private val enableAICheckBox = JBCheckBox("Enable AI", true)

    // --- Generation Policy ---
    private val generationModeCombo = JComboBox(GenerationMode.entries.map { it.displayName }.toTypedArray())
    private val testFrameworkCombo = JComboBox(TestFrameworkOption.available().map { it.displayName }.toTypedArray())
    private val autoWriteCheckBox = JBCheckBox("Auto-write generated tests (skip preview)", false)
    private val previewCheckBox = JBCheckBox("Preview before writing", true)

    // --- Scenario Toggles ---
    private val securityScenariosCheckBox = JBCheckBox("Security scenarios (SQL injection, XSS, etc.)", false)
    private val idempotencyScenariosCheckBox = JBCheckBox("Idempotency scenarios (duplicate requests, etc.)", false)
    private val concurrencyScenariosCheckBox = JBCheckBox("Concurrency scenarios (optimistic lock, race conditions)", false)
    private val stateMachineScenariosCheckBox = JBCheckBox("State-machine scenarios (valid/invalid transitions)", false)

    private var settings: SettingsService? = null

    override fun getDisplayName(): String = "Spring Test Generator"

    override fun createComponent(): JComponent {
        settings = SettingsService.getInstance(project)
        val current = settings!!.getConfig()

        // AI Provider
        baseUrlField.text = current.baseUrl
        apiKeyField.text = current.apiKey
        modelField.text = current.model
        enableAICheckBox.isSelected = current.enableAI

        // Generation Policy
        generationModeCombo.selectedItem = GenerationMode.fromId(current.generationMode).displayName
        testFrameworkCombo.selectedItem = TestFrameworkOption.fromId(current.defaultTestFramework).displayName
        autoWriteCheckBox.isSelected = current.autoWriteEnabled
        previewCheckBox.isSelected = current.previewBeforeWrite

        // Scenario Toggles
        securityScenariosCheckBox.isSelected = current.enableSecurityScenarios
        idempotencyScenariosCheckBox.isSelected = current.enableIdempotencyScenarios
        concurrencyScenariosCheckBox.isSelected = current.enableConcurrencyScenarios
        stateMachineScenariosCheckBox.isSelected = current.enableStateMachineScenarios

        // Link auto-write and preview: auto-write disables preview
        autoWriteCheckBox.addActionListener {
            if (autoWriteCheckBox.isSelected) {
                previewCheckBox.isSelected = false
                previewCheckBox.isEnabled = false
            } else {
                previewCheckBox.isEnabled = true
                previewCheckBox.isSelected = true
            }
        }

        // Link AI enable with generation mode
        enableAICheckBox.addActionListener {
            if (!enableAICheckBox.isSelected) {
                generationModeCombo.selectedItem = GenerationMode.DETERMINISTIC.displayName
            }
        }

        return FormBuilder.createFormBuilder()
            // AI Provider section
            .addSeparator(10)
            .addComponent(createSectionHeader("AI Provider"))
            .addLabeledComponent("Base URL:", baseUrlField)
            .addLabeledComponent("API Key:", apiKeyField)
            .addLabeledComponent("Model Name:", modelField)
            .addComponent(enableAICheckBox)
            // Generation Policy section
            .addSeparator(10)
            .addComponent(createSectionHeader("Generation Policy"))
            .addLabeledComponent("Generation Mode:", generationModeCombo)
            .addLabeledComponent("Default Test Framework:", testFrameworkCombo)
            .addComponent(previewCheckBox)
            .addComponent(autoWriteCheckBox)
            // Scenario Toggles section
            .addSeparator(10)
            .addComponent(createSectionHeader("Risk Scenario Toggles"))
            .addComponent(securityScenariosCheckBox)
            .addComponent(idempotencyScenariosCheckBox)
            .addComponent(concurrencyScenariosCheckBox)
            .addComponent(stateMachineScenariosCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val current = settings!!.getConfig()
        return baseUrlField.text != current.baseUrl ||
            apiKeyField.text != current.apiKey ||
            modelField.text != current.model ||
            enableAICheckBox.isSelected != current.enableAI ||
            generationModeCombo.selectedItem != GenerationMode.fromId(current.generationMode).displayName ||
            testFrameworkCombo.selectedItem != TestFrameworkOption.fromId(current.defaultTestFramework).displayName ||
            autoWriteCheckBox.isSelected != current.autoWriteEnabled ||
            previewCheckBox.isSelected != current.previewBeforeWrite ||
            securityScenariosCheckBox.isSelected != current.enableSecurityScenarios ||
            idempotencyScenariosCheckBox.isSelected != current.enableIdempotencyScenarios ||
            concurrencyScenariosCheckBox.isSelected != current.enableConcurrencyScenarios ||
            stateMachineScenariosCheckBox.isSelected != current.enableStateMachineScenarios
    }

    override fun apply() {
        settings!!.setConfig(
            baseUrlField.text,
            apiKeyField.text,
            modelField.text,
            enableAICheckBox.isSelected
        )
        settings!!.setPolicy(
            generationMode = GenerationMode.entries.first { it.displayName == generationModeCombo.selectedItem }.id,
            defaultTestFramework = TestFrameworkOption.available()
                .first { it.displayName == testFrameworkCombo.selectedItem }.id,
            autoWriteEnabled = autoWriteCheckBox.isSelected,
            previewBeforeWrite = previewCheckBox.isSelected,
            enableSecurityScenarios = securityScenariosCheckBox.isSelected,
            enableIdempotencyScenarios = idempotencyScenariosCheckBox.isSelected,
            enableConcurrencyScenarios = concurrencyScenariosCheckBox.isSelected,
            enableStateMachineScenarios = stateMachineScenariosCheckBox.isSelected
        )
    }

    override fun reset() {
        val current = settings!!.getConfig()
        baseUrlField.text = current.baseUrl
        apiKeyField.text = current.apiKey
        modelField.text = current.model
        enableAICheckBox.isSelected = current.enableAI
        generationModeCombo.selectedItem = GenerationMode.fromId(current.generationMode).displayName
        testFrameworkCombo.selectedItem = TestFrameworkOption.fromId(current.defaultTestFramework).displayName
        autoWriteCheckBox.isSelected = current.autoWriteEnabled
        previewCheckBox.isSelected = current.previewBeforeWrite
        securityScenariosCheckBox.isSelected = current.enableSecurityScenarios
        idempotencyScenariosCheckBox.isSelected = current.enableIdempotencyScenarios
        concurrencyScenariosCheckBox.isSelected = current.enableConcurrencyScenarios
        stateMachineScenariosCheckBox.isSelected = current.enableStateMachineScenarios
    }

    private fun createSectionHeader(text: String): JPanel = JPanel().apply {
        add(javax.swing.JLabel("<html><b>$text</b></html>"))
    }
}