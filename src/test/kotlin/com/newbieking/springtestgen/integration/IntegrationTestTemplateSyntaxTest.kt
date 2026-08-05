package com.newbieking.springtestgen.integration

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class IntegrationTestTemplateSyntaxTest : BasePlatformTestCase() {

    @BeforeEach
    fun initializePlatform() {
        setUp()
    }

    @AfterEach
    fun shutdownPlatform() {
        tearDown()
    }

    @Test
    fun `generated integration templates contain valid Java syntax`() {
        val generator = IntegrationTestTemplateGenerator()
        val metadata = ClassUnderTestMetadata(
            simpleName = "OrderRepository",
            qualifiedName = "sample.OrderRepository",
            packageName = "sample",
            targetType = TargetType.REPOSITORY,
            isInterface = true
        )
        val dependencies = ProjectTestDependencies(
            setOf(
                "org.springframework.boot:spring-boot-starter-data-jpa",
                "org.testcontainers:testcontainers",
                "org.testcontainers:junit-jupiter"
            )
        )
        val jpa = assertIs<IntegrationTemplateResult.Generated>(
            generator.generate(
                metadata,
                IntegrationTestConfiguration(true, IntegrationTestMode.JPA_SLICE, "test", "OrderRepositoryTest"),
                dependencies
            )
        )
        val containers = assertIs<IntegrationTemplateResult.Generated>(
            generator.generate(
                metadata,
                IntegrationTestConfiguration(true, IntegrationTestMode.TESTCONTAINERS, "container", "OrderRepositoryIT"),
                dependencies
            )
        )

        assertNoSyntaxErrors("OrderRepositoryTest.java", jpa.source)
        assertNoSyntaxErrors("OrderRepositoryIT.java", containers.source)
    }

    private fun assertNoSyntaxErrors(fileName: String, source: String) {
        val generatedPsi = ReadAction.compute<PsiFile, RuntimeException> {
            PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaFileType.INSTANCE, source)
        }
        val syntaxErrors = ReadAction.compute<List<PsiErrorElement>, RuntimeException> {
            PsiTreeUtil.findChildrenOfType(generatedPsi, PsiErrorElement::class.java).toList()
        }
        assertTrue(syntaxErrors.isEmpty(), syntaxErrors.joinToString { it.errorDescription })
    }
}
