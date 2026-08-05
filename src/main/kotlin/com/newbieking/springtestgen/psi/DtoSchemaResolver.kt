package com.newbieking.springtestgen.psi

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType

/** Extracts stable DTO field and Bean Validation snapshots from Java PSI. */
class DtoSchemaResolver(private val psiManager: PsiManager) {

    fun resolve(type: PsiType): RequestBodySchema? = resolve(type, emptySet())

    private fun resolve(type: PsiType, visitedTypes: Set<String>): RequestBodySchema? {
        val psiClass = (type as? PsiClassType)?.resolve() ?: return null
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName?.startsWith("java.") == true ||
            (qualifiedName != null && qualifiedName in visitedTypes)
        ) {
            return null
        }
        val nextVisitedTypes = if (qualifiedName == null) visitedTypes else visitedTypes + qualifiedName

        return RequestBodySchema(
            typeName = type.presentableText,
            qualifiedName = qualifiedName,
            fields = psiClass.allFields
                .filterNot { it.hasModifierProperty("static") }
                .map { toFieldSchema(it, nextVisitedTypes) }
        )
    }

    private fun toFieldSchema(field: PsiField, visitedTypes: Set<String>): RequestFieldSchema {
        val annotations = field.annotations.associateBy { it.qualifiedName }
        val size = annotations["jakarta.validation.constraints.Size"]
            ?: annotations["javax.validation.constraints.Size"]
        val min = annotations["jakarta.validation.constraints.Min"]
            ?: annotations["javax.validation.constraints.Min"]
            ?: annotations["jakarta.validation.constraints.DecimalMin"]
            ?: annotations["javax.validation.constraints.DecimalMin"]
        val max = annotations["jakarta.validation.constraints.Max"]
            ?: annotations["javax.validation.constraints.Max"]
            ?: annotations["jakarta.validation.constraints.DecimalMax"]
            ?: annotations["javax.validation.constraints.DecimalMax"]
        val pattern = annotations["jakarta.validation.constraints.Pattern"]
            ?: annotations["javax.validation.constraints.Pattern"]
        val schema = annotations["io.swagger.v3.oas.annotations.media.Schema"]

        val required = annotations.containsKey("jakarta.validation.constraints.NotNull") ||
            annotations.containsKey("javax.validation.constraints.NotNull") ||
            annotations.containsKey("jakarta.validation.constraints.NotBlank") ||
            annotations.containsKey("javax.validation.constraints.NotBlank") ||
            annotations.containsKey("jakarta.validation.constraints.NotEmpty") ||
            annotations.containsKey("javax.validation.constraints.NotEmpty")

        return RequestFieldSchema(
            name = field.name,
            type = field.type.canonicalText,
            constraints = ValidationConstraints(
                required = required,
                notBlank = annotations.containsKey("jakarta.validation.constraints.NotBlank") ||
                    annotations.containsKey("javax.validation.constraints.NotBlank"),
                minLength = attributeValue(size, "min")?.toIntOrNull(),
                maxLength = attributeValue(size, "max")?.toIntOrNull(),
                minimum = attributeValue(min, "value"),
                maximum = attributeValue(max, "value"),
                pattern = attributeValue(pattern, "regexp")
            ),
            defaultValue = attributeValue(schema, "defaultValue"),
            nestedSchema = resolve(field.type, visitedTypes)
        )
    }

    private fun attributeValue(annotation: PsiAnnotation?, name: String): String? {
        val expression = annotation?.findDeclaredAttributeValue(name) as? PsiExpression ?: return null
        return JavaPsiFacade.getInstance(psiManager.project)
            .constantEvaluationHelper
            .computeConstantExpression(expression)
            ?.toString()
    }
}
