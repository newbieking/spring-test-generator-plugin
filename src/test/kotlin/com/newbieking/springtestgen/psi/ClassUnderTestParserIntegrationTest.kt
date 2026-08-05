package com.newbieking.springtestgen.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.util.PsiTreeUtil
import com.newbieking.springtestgen.model.DependencyKind
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility
import com.newbieking.springtestgen.strategy.TestStrategyRegistry
import com.newbieking.springtestgen.strategy.TestStrategySelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ClassUnderTestParserIntegrationTest : BasePlatformTestCase() {

    @BeforeEach
    fun initializePlatform() {
        setUp()
    }

    @AfterEach
    fun shutdownPlatform() {
        tearDown()
    }

    @Test
    fun `parses non controller classes into psi free metadata`() {
        val file: PsiFile = myFixture.configureByText(
            "ApplicationTargets.java",
            """
            package sample;

            @org.springframework.stereotype.Service
            class PaymentService {
                private final PaymentRepository repository;

                public PaymentService(PaymentRepository repository) {
                    this.repository = repository;
                }

                public Receipt charge(String orderId, int amount) throws java.io.IOException {
                    return null;
                }

                private void internalOnly() { }
            }

            @org.springframework.stereotype.Repository
            interface PaymentRepository {
                Receipt find(String orderId);
            }

            interface SpringDataRepository extends org.springframework.data.repository.Repository<Receipt, Long> { }

            @org.apache.ibatis.annotations.Mapper
            interface PaymentMapper { }

            class StringUtils {
                public static String normalize(String input) { return input.trim(); }
            }

            @org.springframework.web.bind.annotation.RestController
            class PaymentController { }

            @org.springframework.context.annotation.Configuration
            class ApplicationConfig { }

            class Receipt { }
            """.trimIndent()
        )

        val parser = ClassUnderTestParser()
        val service = parser.parse(findClass(file, "PaymentService"))
        val repository = parser.parse(findClass(file, "PaymentRepository"))
        val springDataRepository = parser.parse(findClass(file, "SpringDataRepository"))
        val mapper = parser.parse(findClass(file, "PaymentMapper"))
        val utility = parser.parse(findClass(file, "StringUtils"))
        val controller = parser.parse(findClass(file, "PaymentController"))
        val config = parser.parse(findClass(file, "ApplicationConfig"))

        assertEquals(TargetType.SERVICE, service.targetType)
        assertEquals("sample", service.packageName)
        assertEquals(listOf("repository"), service.constructors.single().parameters.map { it.name })
        assertEquals(
            listOf(DependencyKind.CONSTRUCTOR_PARAMETER, DependencyKind.FIELD),
            service.dependencies.map { it.kind }
        )
        assertEquals(Visibility.PUBLIC, service.methods.first { it.name == "charge" }.visibility)
        assertEquals(listOf("orderId", "amount"), service.methods.first { it.name == "charge" }.parameters.map { it.name })
        assertEquals(listOf("java.io.IOException"), service.methods.first { it.name == "charge" }.declaredExceptions)

        assertEquals(TargetType.REPOSITORY, repository.targetType)
        assertEquals(TargetType.REPOSITORY, springDataRepository.targetType)
        assertEquals(TargetType.MAPPER, mapper.targetType)
        assertEquals(TargetType.UTILITY, utility.targetType)
        assertEquals(TargetType.CONTROLLER, controller.targetType)
        assertEquals(TargetType.UNSUPPORTED, config.targetType)
        assertNotNull(config.unsupportedReason)

        val controllerSelection = TestStrategyRegistry().select(controller)
        assertTrue(controllerSelection is TestStrategySelection.Unsupported)
        assertTrue((controllerSelection as TestStrategySelection.Unsupported).reason.contains("MockMvc"))
    }

    private fun findClass(file: PsiFile, name: String): PsiClass =
        ReadAction.compute<PsiClass, RuntimeException> {
            PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java)
                .firstOrNull { it.name == name }
                ?: error("Class $name was not found")
        }
}
