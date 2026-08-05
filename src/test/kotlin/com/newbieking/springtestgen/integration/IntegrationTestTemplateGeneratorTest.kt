package com.newbieking.springtestgen.integration

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntegrationTestTemplateGeneratorTest {

    private val generator = IntegrationTestTemplateGenerator()

    @Test
    fun `disabled integration generation is rejected without creating a template`() {
        val result = generator.generate(
            repository(),
            IntegrationTestConfiguration(false, IntegrationTestMode.JPA_SLICE, "test", "OrderRepositoryTest"),
            ProjectTestDependencies(setOf("org.springframework.boot:spring-boot-starter-data-jpa"))
        )

        val rejected = assertIs<IntegrationTemplateResult.Rejected>(result)
        assertTrue(rejected.reason.contains("disabled"))
        assertTrue(rejected.missingDependencies.isEmpty())
    }

    @Test
    fun `valid jpa slice configuration generates a profile aware template`() {
        val result = generator.generate(
            repository(),
            IntegrationTestConfiguration(true, IntegrationTestMode.JPA_SLICE, "integration", "OrderRepositoryTest"),
            ProjectTestDependencies(setOf("org.springframework.boot:spring-boot-starter-data-jpa:3.4.0"))
        )

        val generated = assertIs<IntegrationTemplateResult.Generated>(result)
        assertEquals(IntegrationTestMode.JPA_SLICE, generated.mode)
        assertTrue(generated.source.contains("@DataJpaTest"))
        assertTrue(generated.source.contains("@ActiveProfiles(\"integration\")"))
        assertTrue(!generated.source.contains("GenericContainer"))
    }

    @Test
    fun `missing dependencies reject mybatis and testcontainers templates`() {
        val mybatis = generator.generate(
            mapper(),
            IntegrationTestConfiguration(true, IntegrationTestMode.MYBATIS_SLICE, "test", "OrderMapperTest"),
            ProjectTestDependencies(emptySet())
        )
        val containers = generator.generate(
            repository(),
            IntegrationTestConfiguration(true, IntegrationTestMode.TESTCONTAINERS, "test", "OrderRepositoryIT"),
            ProjectTestDependencies(setOf("org.testcontainers:testcontainers"))
        )

        val mybatisRejected = assertIs<IntegrationTemplateResult.Rejected>(mybatis)
        val containersRejected = assertIs<IntegrationTemplateResult.Rejected>(containers)
        assertTrue("org.mybatis.spring.boot:mybatis-spring-boot-starter" in mybatisRejected.missingDependencies)
        assertTrue("org.testcontainers:junit-jupiter" in containersRejected.missingDependencies)
    }

    @Test
    fun `valid mybatis and testcontainers configurations stay explicit`() {
        val mybatis = generator.generate(
            mapper(),
            IntegrationTestConfiguration(true, IntegrationTestMode.MYBATIS_SLICE, "integration", "OrderMapperTest"),
            ProjectTestDependencies(setOf("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4"))
        )
        val containers = generator.generate(
            repository(),
            IntegrationTestConfiguration(
                enabled = true,
                mode = IntegrationTestMode.TESTCONTAINERS,
                testProfile = "container",
                testClassName = "OrderRepositoryIT",
                containerImage = "postgres:16-alpine"
            ),
            ProjectTestDependencies(
                setOf("org.testcontainers:testcontainers:1.20.0", "org.testcontainers:junit-jupiter:1.20.0")
            )
        )

        val mybatisGenerated = assertIs<IntegrationTemplateResult.Generated>(mybatis)
        val containersGenerated = assertIs<IntegrationTemplateResult.Generated>(containers)
        assertTrue(mybatisGenerated.source.contains("@MybatisTest"))
        assertTrue(containersGenerated.source.contains("@Testcontainers"))
        assertTrue(containersGenerated.source.contains("postgres:16-alpine"))
        assertTrue(containersGenerated.source.contains("@ActiveProfiles(\"container\")"))
    }

    @Test
    fun `rejects an integration mode that does not match the target type`() {
        val result = generator.generate(
            mapper(),
            IntegrationTestConfiguration(true, IntegrationTestMode.JPA_SLICE, "test", "OrderMapperTest"),
            ProjectTestDependencies(setOf("org.springframework.boot:spring-boot-starter-data-jpa"))
        )

        val rejected = assertIs<IntegrationTemplateResult.Rejected>(result)
        assertTrue(rejected.reason.contains("Repository"))
    }

    private fun repository() = ClassUnderTestMetadata(
        simpleName = "OrderRepository",
        qualifiedName = "sample.OrderRepository",
        packageName = "sample",
        targetType = TargetType.REPOSITORY,
        isInterface = true
    )

    private fun mapper() = ClassUnderTestMetadata(
        simpleName = "OrderMapper",
        qualifiedName = "sample.OrderMapper",
        packageName = "sample",
        targetType = TargetType.MAPPER,
        isInterface = true
    )
}
