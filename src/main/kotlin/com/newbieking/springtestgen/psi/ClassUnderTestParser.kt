package com.newbieking.springtestgen.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.ConstructorMetadata
import com.newbieking.springtestgen.model.DependencyKind
import com.newbieking.springtestgen.model.DependencyMetadata
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.ParameterMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility

/** Converts a selected Java class into a PSI-free test-generation description. */
class ClassUnderTestParser {

    fun parse(psiClass: PsiClass): ClassUnderTestMetadata =
        ReadAction.compute<ClassUnderTestMetadata, RuntimeException> {
            parseInReadAction(psiClass)
        }

    private fun parseInReadAction(psiClass: PsiClass): ClassUnderTestMetadata {
        val qualifiedName = psiClass.qualifiedName
        val targetType = detectTargetType(psiClass)
        val annotations = annotationNames(psiClass)
        val constructors = psiClass.constructors.map(::toConstructorMetadata)
        val fieldDependencies = psiClass.fields
            .filterNot { it.hasModifierProperty(PsiModifier.STATIC) }
            .map { toFieldDependency(it) }
        val constructorDependencies = constructors.flatMap { constructor ->
            constructor.parameters.map { parameter ->
                DependencyMetadata(
                    name = parameter.name,
                    type = parameter.type,
                    qualifiedType = parameter.qualifiedType,
                    kind = DependencyKind.CONSTRUCTOR_PARAMETER,
                    required = true
                )
            }
        }

        return ClassUnderTestMetadata(
            simpleName = psiClass.name ?: "<anonymous>",
            qualifiedName = qualifiedName,
            packageName = qualifiedName?.substringBeforeLast('.', "") ?: "",
            targetType = targetType,
            isInterface = psiClass.isInterface,
            annotations = annotations,
            constructors = constructors,
            dependencies = constructorDependencies + fieldDependencies,
            methods = psiClass.methods.filterNot { it.isConstructor }.map(::toMethodMetadata),
            unsupportedReason = unsupportedReason(targetType, qualifiedName)
        )
    }

    private fun toConstructorMetadata(method: PsiMethod): ConstructorMetadata =
        ConstructorMetadata(
            parameters = method.parameterList.parameters.mapIndexed(::toParameterMetadata),
            visibility = visibility(method)
        )

    private fun toFieldDependency(field: PsiField): DependencyMetadata {
        val annotations = annotationNames(field)
        return DependencyMetadata(
            name = field.name,
            type = field.type.canonicalText,
            qualifiedType = qualifiedType(field.type),
            kind = DependencyKind.FIELD,
            required = annotations.none { it in NULLABLE_ANNOTATIONS }
        )
    }

    private fun toMethodMetadata(method: PsiMethod): MethodMetadata =
        MethodMetadata(
            name = method.name,
            returnType = method.returnType?.canonicalText ?: "void",
            parameters = method.parameterList.parameters.mapIndexed(::toParameterMetadata),
            declaredExceptions = method.throwsList.referencedTypes.map { it.canonicalText },
            visibility = visibility(method),
            isStatic = method.hasModifierProperty(PsiModifier.STATIC),
            annotations = annotationNames(method)
        )

    private fun toParameterMetadata(index: Int, parameter: PsiParameter): ParameterMetadata =
        ParameterMetadata(
            name = parameter.name.takeIf(String::isNotBlank) ?: "arg$index",
            type = parameter.type.canonicalText,
            qualifiedType = qualifiedType(parameter.type)
        )

    private fun qualifiedType(type: PsiType): String? =
        (type as? PsiClassType)?.resolve()?.qualifiedName

    private fun visibility(element: PsiMethod): Visibility = when {
        element.hasModifierProperty(PsiModifier.PUBLIC) -> Visibility.PUBLIC
        element.hasModifierProperty(PsiModifier.PROTECTED) -> Visibility.PROTECTED
        element.hasModifierProperty(PsiModifier.PRIVATE) -> Visibility.PRIVATE
        else -> Visibility.PACKAGE_PRIVATE
    }

    private fun detectTargetType(psiClass: PsiClass): TargetType {
        val annotations = annotationNames(psiClass)
        return when {
            annotations.any { it in CONTROLLER_ANNOTATIONS } -> TargetType.CONTROLLER
            annotations.any { it in SERVICE_ANNOTATIONS } -> TargetType.SERVICE
            annotations.any { it in COMPONENT_ANNOTATIONS } -> TargetType.COMPONENT
            annotations.any { it in MAPPER_ANNOTATIONS } -> TargetType.MAPPER
            annotations.any { it in REPOSITORY_ANNOTATIONS } || isRepositoryInterface(psiClass) -> TargetType.REPOSITORY
            annotations.any { it in KNOWN_UNSUPPORTED_ANNOTATIONS } -> TargetType.UNSUPPORTED
            psiClass.isInterface -> TargetType.UNSUPPORTED
            else -> TargetType.UTILITY
        }
    }

    private fun isRepositoryInterface(psiClass: PsiClass, visited: Set<String> = emptySet()): Boolean {
        if (!psiClass.isInterface) return false
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null && qualifiedName in visited) return false
        val nextVisited = if (qualifiedName == null) visited else visited + qualifiedName
        val directSuperTypeNames = psiClass.superTypes.map { it.canonicalText.substringBefore('<') }
        if (directSuperTypeNames.any(::isRepositorySuperType)) return true
        return psiClass.supers.any { superClass ->
            val superName = superClass.qualifiedName
            isRepositorySuperType(superName) ||
                isRepositoryInterface(superClass, nextVisited)
        }
    }

    private fun isRepositorySuperType(superName: String?): Boolean =
        superName in REPOSITORY_SUPER_TYPES ||
            (superName != null && superName.startsWith("org.springframework.data.repository."))

    private fun unsupportedReason(targetType: TargetType, qualifiedName: String?): String? = when (targetType) {
        TargetType.CONTROLLER -> "Controller targets use the existing endpoint/MockMvc generation strategy."
        TargetType.UNSUPPORTED -> "No non-Controller test strategy is registered for ${qualifiedName ?: "this class"}."
        else -> null
    }

    private fun annotationNames(element: PsiClass): Set<String> = annotationNames(element.annotations)

    private fun annotationNames(element: PsiMethod): Set<String> = annotationNames(element.annotations)

    private fun annotationNames(element: PsiField): Set<String> = annotationNames(element.annotations)

    private fun annotationNames(annotations: Array<PsiAnnotation>): Set<String> =
        annotations.mapNotNull { it.qualifiedName }.toSet()

    private companion object {
        val CONTROLLER_ANNOTATIONS = setOf(
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.Controller"
        )
        val SERVICE_ANNOTATIONS = setOf("org.springframework.stereotype.Service")
        val COMPONENT_ANNOTATIONS = setOf("org.springframework.stereotype.Component")
        val REPOSITORY_ANNOTATIONS = setOf("org.springframework.stereotype.Repository")
        val MAPPER_ANNOTATIONS = setOf(
            "org.apache.ibatis.annotations.Mapper",
            "org.mapstruct.Mapper"
        )
        val REPOSITORY_SUPER_TYPES = setOf(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository"
        )
        val KNOWN_UNSUPPORTED_ANNOTATIONS = setOf(
            "org.springframework.context.annotation.Configuration",
            "org.springframework.stereotype.ControllerAdvice",
            "org.springframework.web.bind.annotation.RestControllerAdvice",
            "org.aspectj.lang.annotation.Aspect"
        )
        val NULLABLE_ANNOTATIONS = setOf(
            "org.jetbrains.annotations.Nullable",
            "javax.annotation.Nullable",
            "jakarta.annotation.Nullable"
        )
    }
}
