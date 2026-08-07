package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.DependencyMetadata
import com.newbieking.springtestgen.model.DependencyKind
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.psi.HttpMethod
import com.newbieking.springtestgen.psi.RequestParam
import com.newbieking.springtestgen.psi.PathVariable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskScenarioGeneratorTest {

    // --- SecurityScenarioGenerator ---

    @Test
    fun `SecurityScenarioGenerator generates injection scenarios for request params`() {
        val endpoint = mockEndpoint(
            methodName = "search",
            httpMethod = HttpMethod.GET,
            requestParams = listOf(RequestParam("keyword", "java.lang.String", true))
        )
        val generator = SecurityScenarioGenerator()
        val scenarios = generator.generate(endpoint)

        val injectionScenarios = scenarios.filter { it.scenarioType == TestScenarioType.SECURITY_INJECTION }
        assertEquals(1, injectionScenarios.size)
        assertTrue(injectionScenarios.all { it.riskLabel == RiskScenarioLabel.SECURITY })
        assertTrue(injectionScenarios.all { it.expectedStatus == ExpectedHttpStatus.BAD_REQUEST })
        assertTrue(injectionScenarios.any { "keyword" in it.displayName })
    }

    @Test
    fun `SecurityScenarioGenerator generates XSS scenario for endpoints with request body`() {
        val endpoint = mockEndpoint(
            methodName = "create",
            httpMethod = HttpMethod.POST,
            requestBodyType = "CreateRequest"
        )
        val generator = SecurityScenarioGenerator()
        val scenarios = generator.generate(endpoint)

        val xssScenarios = scenarios.filter {
            it.scenarioType == TestScenarioType.SECURITY_INJECTION && "XSS" in it.displayName
        }
        assertEquals(1, xssScenarios.size)
    }

    @Test
    fun `SecurityScenarioGenerator generates path traversal scenarios for path variables`() {
        val endpoint = mockEndpoint(
            methodName = "getById",
            httpMethod = HttpMethod.GET,
            pathVariables = listOf(PathVariable("id", "java.lang.Long"))
        )
        val generator = SecurityScenarioGenerator()
        val scenarios = generator.generate(endpoint)

        val pathTraversalScenarios = scenarios.filter {
            it.scenarioType == TestScenarioType.SECURITY_INJECTION && "path traversal" in it.displayName.lowercase()
        }
        assertEquals(1, pathTraversalScenarios.size)
    }

    @Test
    fun `SecurityScenarioGenerator generates auth bypass scenarios`() {
        val endpoint = mockEndpoint(methodName = "adminAction", httpMethod = HttpMethod.POST)
        val generator = SecurityScenarioGenerator()
        val scenarios = generator.generate(endpoint)

        val authScenarios = scenarios.filter { it.scenarioType == TestScenarioType.SECURITY_AUTH_BYPASS }
        assertEquals(2, authScenarios.size)
        assertTrue(authScenarios.any { it.expectedStatus == ExpectedHttpStatus.UNAUTHORIZED })
        assertTrue(authScenarios.any { it.expectedStatus == ExpectedHttpStatus.FORBIDDEN })
    }

    @Test
    fun `SecurityScenarioGenerator generates sensitive data scenario only for GET`() {
        val getEndpoint = mockEndpoint(methodName = "getUser", httpMethod = HttpMethod.GET)
        val postEndpoint = mockEndpoint(methodName = "createUser", httpMethod = HttpMethod.POST)

        val generator = SecurityScenarioGenerator()
        val getScenarios = generator.generate(getEndpoint).filter {
            it.scenarioType == TestScenarioType.SECURITY_SENSITIVE_DATA
        }
        val postScenarios = generator.generate(postEndpoint).filter {
            it.scenarioType == TestScenarioType.SECURITY_SENSITIVE_DATA
        }

        assertEquals(1, getScenarios.size)
        assertEquals(0, postScenarios.size)
    }

    // --- IdempotencyScenarioGenerator ---

    @Test
    fun `IdempotencyScenarioGenerator generates scenarios for POST`() {
        val endpoint = mockEndpoint(methodName = "createOrder", httpMethod = HttpMethod.POST)
        val generator = IdempotencyScenarioGenerator()
        val scenarios = generator.generate(endpoint)

        assertEquals(2, scenarios.size)
        assertTrue(scenarios.all { it.riskLabel == RiskScenarioLabel.IDEMPOTENCY })
        assertTrue(scenarios.any { it.scenarioType == TestScenarioType.IDEMPOTENCY_DUPLICATE_REQUEST })
        assertTrue(scenarios.any { it.scenarioType == TestScenarioType.IDEMPOTENCY_CONCURRENT_DUPLICATE })
        assertTrue(scenarios.all { it.expectedStatus == ExpectedHttpStatus.CONFLICT })
    }

    @Test
    fun `IdempotencyScenarioGenerator generates scenarios for PUT and PATCH`() {
        val putEndpoint = mockEndpoint(methodName = "updateOrder", httpMethod = HttpMethod.PUT)
        val patchEndpoint = mockEndpoint(methodName = "patchOrder", httpMethod = HttpMethod.PATCH)
        val generator = IdempotencyScenarioGenerator()

        assertEquals(2, generator.generate(putEndpoint).size)
        assertEquals(2, generator.generate(patchEndpoint).size)
    }

    @Test
    fun `IdempotencyScenarioGenerator skips GET and DELETE`() {
        val getEndpoint = mockEndpoint(methodName = "getOrder", httpMethod = HttpMethod.GET)
        val deleteEndpoint = mockEndpoint(methodName = "deleteOrder", httpMethod = HttpMethod.DELETE)
        val generator = IdempotencyScenarioGenerator()

        assertEquals(0, generator.generate(getEndpoint).size)
        assertEquals(0, generator.generate(deleteEndpoint).size)
    }

    // --- ConcurrencyScenarioGenerator ---

    @Test
    fun `ConcurrencyScenarioGenerator generates optimistic lock for update methods`() {
        val metadata = serviceMetadata(
            methods = listOf(
                MethodMetadata("saveOrder", "void", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = ConcurrencyScenarioGenerator()
        val scenarios = generator.generate(metadata)

        val optimisticLockScenarios = scenarios.filter {
            it.scenarioType == ClassTestScenarioType.CONCURRENCY_OPTIMISTIC_LOCK
        }
        assertTrue(optimisticLockScenarios.size >= 1)
        assertTrue(optimisticLockScenarios.all { it.riskLabel == RiskScenarioLabel.CONCURRENCY })
        assertTrue(optimisticLockScenarios.all { it.disabledReason != null })
    }

    @Test
    fun `ConcurrencyScenarioGenerator generates read-write conflict for read methods`() {
        val metadata = serviceMetadata(
            methods = listOf(
                MethodMetadata("findById", "Order", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = ConcurrencyScenarioGenerator()
        val scenarios = generator.generate(metadata)

        val rwConflictScenarios = scenarios.filter {
            it.scenarioType == ClassTestScenarioType.CONCURRENCY_READ_WRITE_CONFLICT
        }
        assertTrue(rwConflictScenarios.size >= 1)
        assertTrue(rwConflictScenarios.all { it.riskLabel == RiskScenarioLabel.CONCURRENCY })
    }

    @Test
    fun `ConcurrencyScenarioGenerator skips UTILITY targets`() {
        val metadata = ClassUnderTestMetadata(
            simpleName = "StringUtils",
            qualifiedName = "com.example.StringUtils",
            packageName = "com.example",
            targetType = TargetType.UTILITY,
            methods = listOf(
                MethodMetadata("process", "String", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = ConcurrencyScenarioGenerator()
        assertEquals(0, generator.generate(metadata).size)
    }

    // --- StateMachineScenarioGenerator ---

    @Test
    fun `StateMachineScenarioGenerator generates scenarios for transition-like methods`() {
        val metadata = serviceMetadata(
            methods = listOf(
                MethodMetadata("approveOrder", "void", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = StateMachineScenarioGenerator()
        val scenarios = generator.generate(metadata)

        assertEquals(2, scenarios.size)
        assertTrue(scenarios.any { it.scenarioType == ClassTestScenarioType.STATE_MACHINE_VALID_TRANSITION })
        assertTrue(scenarios.any { it.scenarioType == ClassTestScenarioType.STATE_MACHINE_INVALID_TRANSITION })
        assertTrue(scenarios.all { it.riskLabel == RiskScenarioLabel.STATE_MACHINE })
        assertTrue(scenarios.all { it.disabledReason != null })
    }

    @Test
    fun `StateMachineScenarioGenerator skips non-transition methods`() {
        val metadata = serviceMetadata(
            methods = listOf(
                MethodMetadata("calculateTotal", "BigDecimal", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = StateMachineScenarioGenerator()
        assertEquals(0, generator.generate(metadata).size)
    }

    @Test
    fun `StateMachineScenarioGenerator skips REPOSITORY targets`() {
        val metadata = ClassUnderTestMetadata(
            simpleName = "OrderRepository",
            qualifiedName = "com.example.OrderRepository",
            packageName = "com.example",
            targetType = TargetType.REPOSITORY,
            methods = listOf(
                MethodMetadata("approve", "void", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )
        val generator = StateMachineScenarioGenerator()
        assertEquals(0, generator.generate(metadata).size)
    }

    // --- RiskScenarioRegistry ---

    @Test
    fun `RiskScenarioRegistry filters by enabled labels`() {
        val registry = RiskScenarioRegistry()
        val endpoint = mockEndpoint(methodName = "create", httpMethod = HttpMethod.POST)

        val securityOnly = registry.generateForEndpoint(endpoint, setOf(RiskScenarioLabel.SECURITY))
        assertTrue(securityOnly.all { it.riskLabel == RiskScenarioLabel.SECURITY })

        val idempotencyOnly = registry.generateForEndpoint(endpoint, setOf(RiskScenarioLabel.IDEMPOTENCY))
        assertTrue(idempotencyOnly.all { it.riskLabel == RiskScenarioLabel.IDEMPOTENCY })

        val none = registry.generateForEndpoint(endpoint, emptySet())
        assertEquals(0, none.size)
    }

    @Test
    fun `RiskScenarioRegistry generates class scenarios for enabled labels`() {
        val registry = RiskScenarioRegistry()
        val metadata = serviceMetadata(
            methods = listOf(
                MethodMetadata("save", "void", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false),
                MethodMetadata("approve", "void", emptyList(), emptyList(), Visibility.PUBLIC, isStatic = false)
            )
        )

        val concurrencyOnly = registry.generateForClass(metadata, setOf(RiskScenarioLabel.CONCURRENCY))
        assertTrue(concurrencyOnly.all { it.riskLabel == RiskScenarioLabel.CONCURRENCY })

        val stateMachineOnly = registry.generateForClass(metadata, setOf(RiskScenarioLabel.STATE_MACHINE))
        assertTrue(stateMachineOnly.all { it.riskLabel == RiskScenarioLabel.STATE_MACHINE })
    }

    // --- Helper methods ---

    private fun mockEndpoint(
        methodName: String,
        httpMethod: HttpMethod,
        requestParams: List<RequestParam> = emptyList(),
        pathVariables: List<PathVariable> = emptyList(),
        requestBodyType: String? = null
    ): EndpointMetadata = EndpointMetadata(
        controllerClass = mockk<PsiClass>(relaxed = true),
        method = mockk<PsiMethod>(relaxed = true),
        controllerName = "TestController",
        controllerQualifiedName = "com.example.TestController",
        methodName = methodName,
        httpMethod = httpMethod,
        path = "/api/test",
        requestBodyType = requestBodyType,
        requestBodySchema = null,
        requestBodyValidated = false,
        requestParams = requestParams,
        pathVariables = pathVariables
    )

    private fun serviceMetadata(
        methods: List<MethodMetadata>
    ): ClassUnderTestMetadata = ClassUnderTestMetadata(
        simpleName = "OrderService",
        qualifiedName = "com.example.OrderService",
        packageName = "com.example",
        targetType = TargetType.SERVICE,
        dependencies = listOf(
            DependencyMetadata("orderRepo", "OrderRepository", "com.example.OrderRepository", DependencyKind.CONSTRUCTOR_PARAMETER, true)
        ),
        methods = methods
    )
}