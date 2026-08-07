package com.newbieking.springtestgen.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.newbieking.springtestgen.services.GenerationMode
import com.newbieking.springtestgen.services.SettingsService
import com.newbieking.springtestgen.services.TestFrameworkOption
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Settings page (Settings -> Tools -> Spring Test Generator)
 *
 * Provides configuration for AI providers (multi-provider with priority),
 * generation mode, test framework, and scenario toggles.
 */
class SettingsConfigurable(private val project: Project) : Configurable {

    // --- Global AI toggle ---
    private val enableAICheckBox = JBCheckBox("Enable AI", true)

    // --- Provider table ---
    private val providerTableModel = ProviderTableModel()
    private val providerTable = JBTable(providerTableModel)

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

        // Global AI toggle
        enableAICheckBox.isSelected = current.enableAI

        // Load providers into table
        loadProvidersFromSettings()

        // Configure table
        providerTable.setRowHeight(28)
        providerTable.preferredScrollableViewportSize = Dimension(800, 200)

        // Set column widths
        val columnModel = providerTable.columnModel
        columnModel.getColumn(0).preferredWidth = 150  // Display Name
        columnModel.getColumn(1).preferredWidth = 300  // Base URL
        columnModel.getColumn(2).preferredWidth = 150  // Model
        columnModel.getColumn(3).preferredWidth = 60   // Priority
        columnModel.getColumn(4).preferredWidth = 60   // Enabled

        // Widen combo boxes
        generationModeCombo.preferredSize = Dimension(300, generationModeCombo.preferredSize.height)
        testFrameworkCombo.preferredSize = Dimension(300, testFrameworkCombo.preferredSize.height)

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

        // Link auto-write and preview
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
            .addComponent(enableAICheckBox)
            .addComponent(createProviderPanel())
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

    /**
     * Create the provider management panel with table and action buttons.
     */
    private fun createProviderPanel(): JPanel {
        val scrollPane = JScrollPane(providerTable)

        val addButton = JButton("Add").apply {
            addActionListener { addProvider() }
        }
        val editButton = JButton("Edit").apply {
            addActionListener { editProvider() }
        }
        val removeButton = JButton("Remove").apply {
            addActionListener { removeProvider() }
        }
        val moveUpButton = JButton("Move Up").apply {
            addActionListener { moveProviderUp() }
        }
        val moveDownButton = JButton("Move Down").apply {
            addActionListener { moveProviderDown() }
        }

        // Double-click to edit
        providerTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) editProvider()
            }
        })

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(addButton)
            add(Box.createHorizontalStrut(5))
            add(editButton)
            add(Box.createHorizontalStrut(5))
            add(removeButton)
            add(Box.createHorizontalStrut(10))
            add(moveUpButton)
            add(Box.createHorizontalStrut(5))
            add(moveDownButton)
        }

        return JPanel(BorderLayout(5, 5)).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }

    private fun addProvider() {
        val dialog = ProviderConfigDialog()
        if (dialog.showAndGet()) {
            val state = dialog.getProviderState()
            // Check for duplicate ID
            if (providerTableModel.providers.any { it.id == state.id }) {
                JOptionPane.showMessageDialog(null, "Provider with ID '${state.id}' already exists", "Duplicate ID", JOptionPane.WARNING_MESSAGE)
                return
            }
            providerTableModel.addProvider(state)
        }
    }

    private fun editProvider() {
        val row = providerTable.selectedRow
        if (row < 0) return
        val existing = providerTableModel.getProvider(row)
        val dialog = ProviderConfigDialog(existing)
        if (dialog.showAndGet()) {
            providerTableModel.updateProvider(row, dialog.getProviderState())
        }
    }

    private fun removeProvider() {
        val row = providerTable.selectedRow
        if (row < 0) return
        providerTableModel.removeProvider(row)
    }

    private fun moveProviderUp() {
        val row = providerTable.selectedRow
        if (row <= 0) return
        providerTableModel.moveUp(row)
        providerTable.setRowSelectionInterval(row - 1, row - 1)
    }

    private fun moveProviderDown() {
        val row = providerTable.selectedRow
        if (row < 0 || row >= providerTableModel.rowCount - 1) return
        providerTableModel.moveDown(row)
        providerTable.setRowSelectionInterval(row + 1, row + 1)
    }

    private fun loadProvidersFromSettings() {
        val configs = settings!!.getProviderConfigs()
        providerTableModel.setProviders(configs.map { SettingsService.ProviderState.fromProviderConfig(it) }.toMutableList())
    }

    override fun isModified(): Boolean {
        val current = settings!!.getConfig()
        if (enableAICheckBox.isSelected != current.enableAI) return true
        if (generationModeCombo.selectedItem != GenerationMode.fromId(current.generationMode).displayName) return true
        if (testFrameworkCombo.selectedItem != TestFrameworkOption.fromId(current.defaultTestFramework).displayName) return true
        if (autoWriteCheckBox.isSelected != current.autoWriteEnabled) return true
        if (previewCheckBox.isSelected != current.previewBeforeWrite) return true
        if (securityScenariosCheckBox.isSelected != current.enableSecurityScenarios) return true
        if (idempotencyScenariosCheckBox.isSelected != current.enableIdempotencyScenarios) return true
        if (concurrencyScenariosCheckBox.isSelected != current.enableConcurrencyScenarios) return true
        if (stateMachineScenariosCheckBox.isSelected != current.enableStateMachineScenarios) return true

        // Check if provider list has changed
        val currentProviders = settings!!.getProviderConfigs().map { SettingsService.ProviderState.fromProviderConfig(it) }
        if (providerTableModel.providers != currentProviders) return true

        return false
    }

    override fun apply() {
        // Sync legacy single-provider toggle
        settings!!.setConfig(
            baseUrl = "",  // Legacy fields no longer primary
            apiKey = "",
            model = "",
            enableAI = enableAICheckBox.isSelected
        )

        // Sync provider list
        val existingIds = settings!!.getProviderConfigs().map { it.id }.toSet()
        val newIds = providerTableModel.providers.map { it.id }.toSet()

        // Remove providers that were deleted
        for (id in existingIds - newIds) {
            settings!!.removeProviderConfig(id)
        }

        // Add or update providers
        for (state in providerTableModel.providers) {
            settings!!.setProviderConfig(state.toProviderConfig())
        }

        // Reorder based on table order (priority = row index)
        val orderedIds = providerTableModel.providers.map { it.id }
        settings!!.reorderProviders(orderedIds)

        // Sync policy
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
        enableAICheckBox.isSelected = current.enableAI
        generationModeCombo.selectedItem = GenerationMode.fromId(current.generationMode).displayName
        testFrameworkCombo.selectedItem = TestFrameworkOption.fromId(current.defaultTestFramework).displayName
        autoWriteCheckBox.isSelected = current.autoWriteEnabled
        previewCheckBox.isSelected = current.previewBeforeWrite
        securityScenariosCheckBox.isSelected = current.enableSecurityScenarios
        idempotencyScenariosCheckBox.isSelected = current.enableIdempotencyScenarios
        concurrencyScenariosCheckBox.isSelected = current.enableConcurrencyScenarios
        stateMachineScenariosCheckBox.isSelected = current.enableStateMachineScenarios
        loadProvidersFromSettings()
    }

    private fun createSectionHeader(text: String): JPanel = JPanel().apply {
        add(JLabel("<html><b>$text</b></html>"))
    }

    /**
     * Table model for the provider list.
     */
    private class ProviderTableModel : AbstractTableModel() {
        val providers = mutableListOf<SettingsService.ProviderState>()

        private val columnNames = arrayOf("Display Name", "Base URL", "Model", "Priority", "Enabled")

        fun setProviders(list: MutableList<SettingsService.ProviderState>) {
            providers.clear()
            providers.addAll(list)
            fireTableDataChanged()
        }

        fun getProvider(row: Int): SettingsService.ProviderState = providers[row]

        fun addProvider(state: SettingsService.ProviderState) {
            providers.add(state)
            fireTableRowsInserted(providers.size - 1, providers.size - 1)
        }

        fun updateProvider(row: Int, state: SettingsService.ProviderState) {
            providers[row] = state
            fireTableRowsUpdated(row, row)
        }

        fun removeProvider(row: Int) {
            providers.removeAt(row)
            fireTableRowsDeleted(row, row)
        }

        fun moveUp(row: Int) {
            val temp = providers[row]
            providers[row] = providers[row - 1]
            providers[row - 1] = temp
            fireTableRowsUpdated(row - 1, row)
        }

        fun moveDown(row: Int) {
            val temp = providers[row]
            providers[row] = providers[row + 1]
            providers[row + 1] = temp
            fireTableRowsUpdated(row, row + 1)
        }

        override fun getRowCount(): Int = providers.size

        override fun getColumnCount(): Int = columnNames.size

        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val p = providers[rowIndex]
            return when (columnIndex) {
                0 -> p.displayName
                1 -> p.baseUrl
                2 -> p.model
                3 -> p.priority
                4 -> p.enabled
                else -> ""
            }
        }
    }
}