package com.newbieking.springtestgen.integration

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType

enum class IntegrationTestMode {
    JPA_SLICE,
    MYBATIS_SLICE,
    TESTCONTAINERS
}

/** Explicit user choices required before an infrastructure-backed template can be generated. */
data class IntegrationTestConfiguration(
    val enabled: Boolean,
    val mode: IntegrationTestMode,
    val testProfile: String,
    val testClassName: String,
    val containerImage: String = "postgres:16-alpine"
)

/** Dependency coordinates resolved by a project adapter from Gradle/Maven configuration. */
data class ProjectTestDependencies(
    val coordinates: Set<String>
) {
    fun contains(coordinate: String): Boolean = coordinates.any { it == coordinate || it.startsWith("$coordinate:") }
}

sealed interface IntegrationTemplateResult {
    data class Generated(
        val source: String,
        val mode: IntegrationTestMode,
        val requiredDependencies: Set<String>
    ) : IntegrationTemplateResult

    data class Rejected(
        val reason: String,
        val missingDependencies: Set<String> = emptySet()
    ) : IntegrationTemplateResult
}

/**
 * Generates opt-in integration-test scaffolds without creating files or connecting to infrastructure.
 */
class IntegrationTestTemplateGenerator {

    fun generate(
        metadata: ClassUnderTestMetadata,
        configuration: IntegrationTestConfiguration,
        dependencies: ProjectTestDependencies
    ): IntegrationTemplateResult {
        validate(metadata, configuration, dependencies)?.let { return it }

        val source = when (configuration.mode) {
            IntegrationTestMode.JPA_SLICE -> jpaSliceSource(metadata, configuration)
            IntegrationTestMode.MYBATIS_SLICE -> myBatisSliceSource(metadata, configuration)
            IntegrationTestMode.TESTCONTAINERS -> testcontainersSource(metadata, configuration)
        }
        return IntegrationTemplateResult.Generated(
            source = source,
            mode = configuration.mode,
            requiredDependencies = requiredDependencies(configuration.mode)
        )
    }

    private fun validate(
        metadata: ClassUnderTestMetadata,
        configuration: IntegrationTestConfiguration,
        dependencies: ProjectTestDependencies
    ): IntegrationTemplateResult.Rejected? {
        if (!configuration.enabled) {
            return IntegrationTemplateResult.Rejected(
                "Integration test generation is disabled. Enable it explicitly before generating infrastructure-backed templates."
            )
        }
        if (configuration.testClassName.isBlank()) {
            return IntegrationTemplateResult.Rejected("An integration test class name is required.")
        }
        if (configuration.testProfile.isBlank()) {
            return IntegrationTemplateResult.Rejected(
                "An explicit Spring test profile is required for integration templates."
            )
        }
        val modeTargetError = when (configuration.mode) {
            IntegrationTestMode.JPA_SLICE -> if (metadata.targetType != TargetType.REPOSITORY) {
                "JPA slice templates require a Repository target."
            } else null
            IntegrationTestMode.MYBATIS_SLICE -> if (metadata.targetType != TargetType.MAPPER) {
                "MyBatis slice templates require a Mapper target."
            } else null
            IntegrationTestMode.TESTCONTAINERS -> if (metadata.targetType !in DAO_TARGET_TYPES) {
                "Testcontainers templates require a Repository or Mapper target."
            } else null
        }
        if (modeTargetError != null) return IntegrationTemplateResult.Rejected(modeTargetError)

        val missingDependencies = requiredDependencies(configuration.mode)
            .filterNot(dependencies::contains)
            .toSet()
        if (missingDependencies.isNotEmpty()) {
            return IntegrationTemplateResult.Rejected(
                reason = "Required test dependencies are missing; no integration template was generated.",
                missingDependencies = missingDependencies
            )
        }
        if (configuration.mode == IntegrationTestMode.TESTCONTAINERS && configuration.containerImage.isBlank()) {
            return IntegrationTemplateResult.Rejected("A container image is required for Testcontainers templates.")
        }
        return null
    }

    private fun jpaSliceSource(
        metadata: ClassUnderTestMetadata,
        configuration: IntegrationTestConfiguration
    ): String = buildString {
        appendHeader(metadata)
        appendLine("import org.junit.jupiter.api.Test;")
        appendLine("import org.springframework.beans.factory.annotation.Autowired;")
        appendLine("import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;")
        appendLine("import org.springframework.test.context.ActiveProfiles;")
        appendLine("import static org.junit.jupiter.api.Assertions.assertNotNull;")
        appendLine()
        appendLine("@DataJpaTest")
        appendLine("@ActiveProfiles(\"${escapeJavaString(configuration.testProfile)}\")")
        appendLine("public class ${configuration.testClassName} {")
        appendLine()
        appendLine("    @Autowired")
        appendLine("    private ${metadata.simpleName} ${fieldName(metadata)};")
        appendLine()
        appendLine("    @Test")
        appendLine("    void persistenceSliceSmokeTest() {")
        appendLine("        // TODO: Add entity fixtures and persistence assertions for the selected profile.")
        appendLine("        assertNotNull(${fieldName(metadata)});")
        appendLine("    }")
        appendLine("}")
    }

    private fun myBatisSliceSource(
        metadata: ClassUnderTestMetadata,
        configuration: IntegrationTestConfiguration
    ): String = buildString {
        appendHeader(metadata)
        appendLine("import org.junit.jupiter.api.Test;")
        appendLine("import org.springframework.beans.factory.annotation.Autowired;")
        appendLine("import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;")
        appendLine("import org.springframework.test.context.ActiveProfiles;")
        appendLine("import static org.junit.jupiter.api.Assertions.assertNotNull;")
        appendLine()
        appendLine("@MybatisTest")
        appendLine("@ActiveProfiles(\"${escapeJavaString(configuration.testProfile)}\")")
        appendLine("public class ${configuration.testClassName} {")
        appendLine()
        appendLine("    @Autowired")
        appendLine("    private ${metadata.simpleName} ${fieldName(metadata)};")
        appendLine()
        appendLine("    @Test")
        appendLine("    void mapperSliceSmokeTest() {")
        appendLine("        // TODO: Add mapper fixtures and SQL assertions for the selected profile.")
        appendLine("        assertNotNull(${fieldName(metadata)});")
        appendLine("    }")
        appendLine("}")
    }

    private fun testcontainersSource(
        metadata: ClassUnderTestMetadata,
        configuration: IntegrationTestConfiguration
    ): String = buildString {
        appendHeader(metadata)
        appendLine("import org.junit.jupiter.api.Test;")
        appendLine("import org.springframework.beans.factory.annotation.Autowired;")
        appendLine("import org.springframework.test.context.ActiveProfiles;")
        appendLine("import org.testcontainers.containers.GenericContainer;")
        appendLine("import org.testcontainers.junit.jupiter.Container;")
        appendLine("import org.testcontainers.junit.jupiter.Testcontainers;")
        appendLine("import static org.junit.jupiter.api.Assertions.assertNotNull;")
        appendLine()
        appendLine("@Testcontainers")
        appendLine("@ActiveProfiles(\"${escapeJavaString(configuration.testProfile)}\")")
        appendLine("public class ${configuration.testClassName} {")
        appendLine()
        appendLine("    @Container")
        appendLine("    static final GenericContainer<?> testDatabase = new GenericContainer<>(\"${escapeJavaString(configuration.containerImage)}\");")
        appendLine()
        appendLine("    @Autowired")
        appendLine("    private ${metadata.simpleName} ${fieldName(metadata)};")
        appendLine()
        appendLine("    @Test")
        appendLine("    void infrastructureSmokeTest() {")
        appendLine("        // TODO: Bind container endpoint properties before adding persistence assertions.")
        appendLine("        assertNotNull(${fieldName(metadata)});")
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendHeader(
        metadata: ClassUnderTestMetadata
    ) {
        val packageName = if (metadata.packageName.isBlank()) "test" else "${metadata.packageName}.test"
        appendLine("package $packageName;")
        appendLine()
        metadata.qualifiedName?.let { appendLine("import $it;") }
        appendLine()
    }

    private fun fieldName(metadata: ClassUnderTestMetadata): String =
        metadata.simpleName.replaceFirstChar { it.lowercase() }

    private fun requiredDependencies(mode: IntegrationTestMode): Set<String> = when (mode) {
        IntegrationTestMode.JPA_SLICE -> setOf("org.springframework.boot:spring-boot-starter-data-jpa")
        IntegrationTestMode.MYBATIS_SLICE -> setOf("org.mybatis.spring.boot:mybatis-spring-boot-starter")
        IntegrationTestMode.TESTCONTAINERS -> setOf(
            "org.testcontainers:testcontainers",
            "org.testcontainers:junit-jupiter"
        )
    }

    private fun escapeJavaString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private companion object {
        val DAO_TARGET_TYPES = setOf(TargetType.REPOSITORY, TargetType.MAPPER)
    }
}
