package com.newbieking.springtestgen.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 持久化设置
 */
@Service(Service.Level.PROJECT)
@State(name = "SpringTestGeneratorSettings", storages = [Storage("springTestGenerator.xml")])
class SettingsService : PersistentStateComponent<SettingsService.State> {

    data class State(
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-3.5-turbo",
        var enableAI: Boolean = true,
        var defaultTestFramework: String = "MOCK_MVC" // 扩展用
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getConfig(): State = myState

    fun setConfig(baseUrl: String, apiKey: String, model: String, enableAI: Boolean) {
        myState.baseUrl = baseUrl
        myState.apiKey = apiKey
        myState.model = model
        myState.enableAI = enableAI
    }

    companion object {
        fun getInstance(project: Project): SettingsService {
            return project.getService(SettingsService::class.java)
        }
    }
}