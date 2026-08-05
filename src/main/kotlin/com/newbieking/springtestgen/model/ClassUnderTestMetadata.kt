package com.newbieking.springtestgen.model

/** PSI-free description of a class selected for test generation. */
data class ClassUnderTestMetadata(
    val simpleName: String,
    val qualifiedName: String?,
    val packageName: String,
    val targetType: TargetType,
    val annotations: Set<String> = emptySet(),
    val constructors: List<ConstructorMetadata> = emptyList(),
    val dependencies: List<DependencyMetadata> = emptyList(),
    val methods: List<MethodMetadata> = emptyList(),
    val unsupportedReason: String? = null
)

enum class TargetType {
    CONTROLLER,
    SERVICE,
    COMPONENT,
    REPOSITORY,
    MAPPER,
    UTILITY,
    UNSUPPORTED
}

data class ConstructorMetadata(
    val parameters: List<ParameterMetadata>,
    val visibility: Visibility
)

data class DependencyMetadata(
    val name: String,
    val type: String,
    val qualifiedType: String?,
    val kind: DependencyKind,
    val required: Boolean
)

enum class DependencyKind {
    CONSTRUCTOR_PARAMETER,
    FIELD
}

data class MethodMetadata(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterMetadata>,
    val declaredExceptions: List<String>,
    val visibility: Visibility,
    val isStatic: Boolean,
    val annotations: Set<String> = emptySet()
)

data class ParameterMetadata(
    val name: String,
    val type: String,
    val qualifiedType: String?
)

enum class Visibility {
    PUBLIC,
    PROTECTED,
    PACKAGE_PRIVATE,
    PRIVATE
}
