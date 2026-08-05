package com.newbieking.springtestgen.generator

import com.intellij.openapi.application.ReadAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.newbieking.springtestgen.psi.ClassUnderTestParser
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ClassTestScriptGeneratorIntegrationTest : BasePlatformTestCase() {

    @BeforeEach
    fun initializePlatform() {
        setUp()
    }

    @AfterEach
    fun shutdownPlatform() {
        tearDown()
    }

    @Test
    fun `generates utility and service sources with valid syntax`() {
        val file: PsiFile = myFixture.configureByText(
            "Targets.java",
            """
            package sample;

            @org.springframework.stereotype.Service
            class PaymentService {
                private final PaymentRepository repository;

                PaymentService(PaymentRepository repository) {
                    this.repository = repository;
                }

                public Receipt charge(String orderId, int amount) { return null; }
            }

            @org.springframework.stereotype.Repository
            interface PaymentRepository { }

            class StringUtils {
                public static String normalize(String input) { return input.trim(); }
            }

            class Receipt { }
            """.trimIndent()
        )
        val parser = ClassUnderTestParser()
        val service = parser.parse(findClass(file, "PaymentService"))
        val utility = parser.parse(findClass(file, "StringUtils"))
        val generator = ClassTestScriptGenerator()

        val serviceSource = generator.generateTestClass(service, "PaymentServiceTest")
        val utilitySource = generator.generateTestClass(utility, "StringUtilsTest")

        assertTrue(serviceSource.contains("package sample.test;"))
        assertTrue(serviceSource.contains("import sample.PaymentService;"))
        assertTrue(serviceSource.contains("import sample.PaymentRepository;"))
        assertTrue(serviceSource.contains("@ExtendWith(MockitoExtension.class)"))
        assertTrue(serviceSource.contains("@Mock"))
        assertTrue(serviceSource.contains("@InjectMocks"))
        assertTrue(serviceSource.contains("testChargeHappyPath"))
        assertTrue(serviceSource.contains("testChargeDependencyException"))
        assertTrue(utilitySource.contains("package sample.test;"))
        assertTrue(utilitySource.contains("import sample.StringUtils;"))
        assertTrue(utilitySource.contains("testNormalizeHappyPath"))
        assertTrue(!utilitySource.contains("MockitoExtension"))

        assertNoSyntaxErrors("PaymentServiceTest.java", serviceSource)
        assertNoSyntaxErrors("StringUtilsTest.java", utilitySource)
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

    private fun findClass(file: PsiFile, name: String): PsiClass =
        ReadAction.compute<PsiClass, RuntimeException> {
            PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java)
                .firstOrNull { it.name == name }
                ?: error("Class $name was not found")
        }
}
