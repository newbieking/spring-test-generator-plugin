package com.newbieking.springtestgen.strategy

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestStrategyRegistryTest {

    private val registry = TestStrategyRegistry()

    @Test
    fun `selects utility junit strategy`() {
        val selection = registry.select(metadata(TargetType.UTILITY))

        val supported = assertIs<TestStrategySelection.Supported>(selection)
        assertEquals("junit5-utility", supported.strategy.id)
        assertEquals(TestFramework.JUNIT5, supported.strategy.framework)
        assertTrue(!supported.strategy.requiresInfrastructure)
    }

    @Test
    fun `selects mockito strategy for service and repository targets`() {
        val service = assertIs<TestStrategySelection.Supported>(registry.select(metadata(TargetType.SERVICE)))
        val repository = assertIs<TestStrategySelection.Supported>(registry.select(metadata(TargetType.REPOSITORY)))

        assertEquals("junit5-mockito-unit", service.strategy.id)
        assertEquals(service.strategy.id, repository.strategy.id)
        assertEquals(TestFramework.JUNIT5_MOCKITO, repository.strategy.framework)
    }

    @Test
    fun `reports controller and unknown targets instead of applying a web strategy`() {
        val controller = assertIs<TestStrategySelection.Unsupported>(registry.select(metadata(TargetType.CONTROLLER)))
        val unknown = assertIs<TestStrategySelection.Unsupported>(registry.select(metadata(TargetType.UNSUPPORTED)))

        assertTrue(controller.reason.contains("No test generation strategy"))
        assertTrue(unknown.reason.contains("No test generation strategy"))
    }

    private fun metadata(targetType: TargetType) = ClassUnderTestMetadata(
        simpleName = "Sample",
        qualifiedName = "sample.Sample",
        packageName = "sample",
        targetType = targetType
    )
}
