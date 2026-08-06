package com.newbieking.springtestgen.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.newbieking.springtestgen.utils.DiagnosticLogger

/**
 * 插件加载时记录启动信息，便于确认插件已注册。
 */
class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        DiagnosticLogger.log("[STARTUP] Spring Test Generator loaded | project=${project.name} | basePath=${project.basePath}")
    }
}
