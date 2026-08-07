package com.newbieking.springtestgen.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AIResponseValidatorTest {

    private val validator = AIResponseValidator()

    // --- Valid response tests ---

    @Test
    fun `valid Java test class passes validation`() {
        val code = """
            package com.example.test;

            import com.example.UserController;
            import org.junit.jupiter.api.Test;
            import org.springframework.test.web.servlet.MockMvc;

            public class UserControllerTest {
                @Test
                void testGetUser() throws Exception {
                    mockMvc.perform(get("/users/1"))
                        .andExpect(status().isOk());
                }
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "UserControllerTest",
            requiredImports = listOf("import com.example.UserController", "import org.junit.jupiter.api.Test")
        )
        assertTrue(result.valid, "Expected valid but got errors: ${result.errorSummary()}")
    }

    // --- Empty response tests ---

    @Test
    fun `null response fails with EMPTY_RESPONSE`() {
        val result = validator.validate(
            code = null,
            expectedPackage = "com.example.test",
            expectedClassName = "Test"
        )
        assertFalse(result.valid)
        assertEquals(1, result.errors.size)
        assertEquals(AIResponseValidator.ValidationErrorKind.EMPTY_RESPONSE, result.errors[0].kind)
    }

    @Test
    fun `blank response fails with EMPTY_RESPONSE`() {
        val result = validator.validate(
            code = "   ",
            expectedPackage = "com.example.test",
            expectedClassName = "Test"
        )
        assertFalse(result.valid)
        assertEquals(1, result.errors.size)
        assertEquals(AIResponseValidator.ValidationErrorKind.EMPTY_RESPONSE, result.errors[0].kind)
    }

    // --- Package mismatch tests ---

    @Test
    fun `wrong package fails with PACKAGE_MISMATCH`() {
        val code = """
            package com.wrong.package;

            public class MyTest {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.PACKAGE_MISMATCH })
    }

    @Test
    fun `missing package declaration fails`() {
        val code = """
            public class MyTest {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.PACKAGE_MISMATCH })
    }

    // --- Class name tests ---

    @Test
    fun `wrong class name fails with CLASS_NAME_INVALID`() {
        val code = """
            package com.example.test;

            public class WrongName {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.CLASS_NAME_INVALID })
    }

    @Test
    fun `no public class fails with CLASS_NAME_INVALID`() {
        val code = """
            package com.example.test;

            class MyTest {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.CLASS_NAME_INVALID })
    }

    // --- Missing import tests ---

    @Test
    fun `missing required import fails with MISSING_IMPORT`() {
        val code = """
            package com.example.test;

            public class MyTest {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest",
            requiredImports = listOf("import org.junit.jupiter.api.Test")
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.MISSING_IMPORT })
    }

    // --- Syntax tests ---

    @Test
    fun `unbalanced braces fails with SYNTAX_ERROR`() {
        val code = """
            package com.example.test;

            public class MyTest {
                @Test
                void test() {
            }
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.SYNTAX_ERROR })
    }

    // --- Markdown fence tests ---

    @Test
    fun `markdown fences detected as CONTAINS_MARKDOWN`() {
        val code = """
            ```java
            package com.example.test;

            public class MyTest {
            }
            ```
        """.trimIndent()

        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest"
        )
        // Markdown fences are a warning, but the code inside might still be valid
        assertTrue(result.errors.any { it.kind == AIResponseValidator.ValidationErrorKind.CONTAINS_MARKDOWN })
    }

    // --- stripMarkdownFences tests ---

    @Test
    fun `stripMarkdownFences removes java fences`() {
        val input = "```java\npackage test;\npublic class T {}\n```"
        val result = validator.stripMarkdownFences(input)
        assertEquals("package test;\npublic class T {}", result)
    }

    @Test
    fun `stripMarkdownFences removes generic fences`() {
        val input = "```\npackage test;\npublic class T {}\n```"
        val result = validator.stripMarkdownFences(input)
        assertEquals("package test;\npublic class T {}", result)
    }

    @Test
    fun `stripMarkdownFences handles no fences`() {
        val input = "package test;\npublic class T {}"
        val result = validator.stripMarkdownFences(input)
        assertEquals("package test;\npublic class T {}", result)
    }

    // --- Multiple errors test ---

    @Test
    fun `multiple validation errors are all reported`() {
        val code = "some random text without java structure"
        val result = validator.validate(
            code = code,
            expectedPackage = "com.example.test",
            expectedClassName = "MyTest",
            requiredImports = listOf("import org.junit.jupiter.api.Test")
        )
        assertFalse(result.valid)
        assertTrue(result.errors.size >= 2, "Expected at least 2 errors, got ${result.errors.size}")
    }
}