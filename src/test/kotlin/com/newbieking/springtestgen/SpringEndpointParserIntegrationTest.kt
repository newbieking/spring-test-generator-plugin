package com.newbieking.springtestgen

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.newbieking.springtestgen.generator.TestScriptGenerator
import com.newbieking.springtestgen.psi.SpringEndpointParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class SpringEndpointParserIntegrationTest : BasePlatformTestCase() {

    @BeforeEach
    fun initializePlatform() {
        setUp()
    }

    @AfterEach
    fun shutdownPlatform() {
        tearDown()
    }

    @Test
    fun `parses mapping metadata and generates matching MockMvc source`() {
        val file: PsiFile = myFixture.configureByText(
            "OrderController.java",
            """
            package example;

            @org.springframework.web.bind.annotation.RestController
            @org.springframework.web.bind.annotation.RequestMapping("/orders/")
            class OrderController {
                @org.springframework.web.bind.annotation.PostMapping("/{orderId}/receive")
                public String receive(
                    @org.springframework.web.bind.annotation.PathVariable("orderId") String orderId,
                    @org.springframework.web.bind.annotation.RequestParam(name = "page", required = true) int page,
                    @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid CreateOrderRequest request
                ) { return "ok"; }
            }

            class CreateOrderRequest {
                @jakarta.validation.constraints.NotBlank
                @jakarta.validation.constraints.Size(min = 3, max = 8)
                String username;

                @jakarta.validation.constraints.Min(1)
                @jakarta.validation.constraints.Max(10)
                int amount;
            }
            """.trimIndent()
        )
        val controller = ReadAction.compute<PsiClass, RuntimeException> {
            PsiTreeUtil.findChildOfType(file, PsiClass::class.java)
                ?: error("Controller PSI class was not found")
        }

        val endpoint = SpringEndpointParser(PsiManager.getInstance(project))
            .parseController(controller)
            .single()

        assertEquals("POST", endpoint.httpMethod.name)
        assertEquals("/orders/{orderId}/receive", endpoint.path)
        assertEquals(listOf("page"), endpoint.requestParams.map { it.name })
        assertEquals(listOf("orderId"), endpoint.pathVariables.map { it.name })
        assertTrue(endpoint.requestBodyValidated)
        assertEquals(setOf("username", "amount"), endpoint.requestBodySchema?.fields?.map { it.name }?.toSet())

        val source = runBlocking {
            TestScriptGenerator(project).generateTestClass(listOf(endpoint), "OrderControllerTest", useAI = false)
        }
        assertTrue(source.contains("package example.test;"))
        assertTrue(source.contains("import example.OrderController;"))
        assertTrue(source.contains("post(\"/orders/testValue/receive\")"))
        assertTrue(source.contains("testReceiveHappyPath"))
        assertTrue(source.contains("isBadRequest()"))
    }
}
