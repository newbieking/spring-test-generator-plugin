package com.newbieking.springtestgen.services

import com.intellij.openapi.diagnostic.Logger

/**
 * Validates AI-generated Java test code before it is written to disk.
 *
 * In AI-full generation mode, the AI produces complete Java test classes.
 * This validator ensures the output meets structural requirements:
 * - Valid Java syntax (basic structural checks)
 * - Correct package declaration
 * - Valid test class name
 * - Required imports present (controller under test, test framework)
 *
 * If validation fails, the code is rejected and an actionable error is returned.
 *
 * This class is PSI-free and safe to call from any thread.
 */
class AIResponseValidator {

    private val log = Logger.getInstance(AIResponseValidator::class.java)

    /**
     * Validation result with detailed error information.
     */
    data class ValidationResult(
        val valid: Boolean,
        val errors: List<ValidationError> = emptyList()
    ) {
        /** Get a human-readable summary of all validation errors. */
        fun errorSummary(): String = errors.joinToString("; ") { it.message }
    }

    /**
     * A single validation error.
     */
    data class ValidationError(
        val kind: ValidationErrorKind,
        val message: String,
        val detail: String? = null
    )

    enum class ValidationErrorKind {
        /** The response is empty or blank. */
        EMPTY_RESPONSE,
        /** No package declaration found or it doesn't match expected. */
        PACKAGE_MISMATCH,
        /** No class declaration found or name is invalid. */
        CLASS_NAME_INVALID,
        /** Missing required import statement. */
        MISSING_IMPORT,
        /** Basic Java syntax structure is broken. */
        SYNTAX_ERROR,
        /** The response contains markdown fences or other non-Java content. */
        CONTAINS_MARKDOWN
    }

    /**
     * Validate AI-generated Java test code.
     *
     * @param code the AI-generated Java code
     * @param expectedPackage the expected package name (e.g., "com.example.test")
     * @param expectedClassName the expected test class name (e.g., "UserControllerTest")
     * @param requiredImports list of import patterns that must be present
     * @return validation result with any errors
     */
    fun validate(
        code: String?,
        expectedPackage: String,
        expectedClassName: String,
        requiredImports: List<String> = emptyList()
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // 1. Check empty response
        if (code.isNullOrBlank()) {
            return ValidationResult(
                valid = false,
                errors = listOf(ValidationError(
                    kind = ValidationErrorKind.EMPTY_RESPONSE,
                    message = "AI returned empty or blank response"
                ))
            )
        }

        val trimmedCode = code.trim()

        // 2. Check for markdown fences (common AI artifact)
        if (trimmedCode.startsWith("```") || trimmedCode.endsWith("```")) {
            errors.add(ValidationError(
                kind = ValidationErrorKind.CONTAINS_MARKDOWN,
                message = "AI response contains markdown code fences",
                detail = "Response should be raw Java code without ``` markers"
            ))
        }

        // 3. Check package declaration
        val packageMatch = PACKAGE_REGEX.find(trimmedCode)
        if (packageMatch == null) {
            errors.add(ValidationError(
                kind = ValidationErrorKind.PACKAGE_MISMATCH,
                message = "No package declaration found",
                detail = "Expected: package $expectedPackage"
            ))
        } else {
            val declaredPackage = packageMatch.groupValues[1]
            if (declaredPackage != expectedPackage) {
                errors.add(ValidationError(
                    kind = ValidationErrorKind.PACKAGE_MISMATCH,
                    message = "Package mismatch: declared '$declaredPackage', expected '$expectedPackage'"
                ))
            }
        }

        // 4. Check class declaration
        val classMatch = CLASS_REGEX.find(trimmedCode)
        if (classMatch == null) {
            errors.add(ValidationError(
                kind = ValidationErrorKind.CLASS_NAME_INVALID,
                message = "No public class declaration found in AI response"
            ))
        } else {
            val declaredClassName = classMatch.groupValues[1]
            if (declaredClassName != expectedClassName) {
                errors.add(ValidationError(
                    kind = ValidationErrorKind.CLASS_NAME_INVALID,
                    message = "Class name mismatch: declared '$declaredClassName', expected '$expectedClassName'"
                ))
            }
        }

        // 5. Check required imports
        for (importPattern in requiredImports) {
            if (!trimmedCode.contains(importPattern)) {
                errors.add(ValidationError(
                    kind = ValidationErrorKind.MISSING_IMPORT,
                    message = "Missing required import: $importPattern"
                ))
            }
        }

        // 6. Basic syntax structure check
        if (!trimmedCode.contains("{") || !trimmedCode.contains("}")) {
            errors.add(ValidationError(
                kind = ValidationErrorKind.SYNTAX_ERROR,
                message = "Response lacks basic Java structure (missing braces)"
            ))
        }

        // Check for balanced braces (simple heuristic)
        val openBraces = trimmedCode.count { it == '{' }
        val closeBraces = trimmedCode.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add(ValidationError(
                kind = ValidationErrorKind.SYNTAX_ERROR,
                message = "Unbalanced braces: $openBraces opening vs $closeBraces closing"
            ))
        }

        val result = ValidationResult(valid = errors.isEmpty(), errors = errors)
        if (errors.isNotEmpty()) {
            log.warn("AI response validation failed: ${result.errorSummary()}")
        } else {
            log.info("AI response validation passed for class $expectedClassName")
        }

        return result
    }

    /**
     * Strip markdown code fences from AI response if present.
     * Returns the cleaned Java code.
     */
    fun stripMarkdownFences(code: String): String {
        var cleaned = code.trim()
        if (cleaned.startsWith("```java")) {
            cleaned = cleaned.removePrefix("```java")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    companion object {
        /** Regex to extract package declaration. */
        private val PACKAGE_REGEX = Regex("""package\s+([\w.]+)\s*;""")

        /** Regex to extract public class declaration. */
        private val CLASS_REGEX = Regex("""public\s+class\s+(\w+)""")
    }
}