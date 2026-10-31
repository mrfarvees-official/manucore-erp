package com.manucore.policy.service;

import com.manucore.policy.Policy;
import com.manucore.policy.Action;
import com.manucore.policy.PolicyCondition;
import com.manucore.policy.PolicyConditionGroup;
import com.manucore.policy.PolicySubject;
import com.manucore.policy.Resource;

import com.manucore.policy.enums.ConditionAttribute;
import com.manucore.policy.enums.ConditionOperator;
import com.manucore.policy.enums.Effect;
import com.manucore.policy.enums.LogicalOperator;
import com.manucore.policy.enums.PolicyPriority;
import com.manucore.policy.enums.SubjectType;

import com.manucore.policy.repository.PolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private PolicyEngine policyEngine;

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";
    private static final String ACTION = "UPDATE";
    private static final String RESOURCE = "INVENTORY";

    @BeforeEach
    void setUp() {
        lenient().doReturn(List.of())
                .when(policyRepository)
                .findCandidates(eq(TENANT_ID), any(), any());
    }

    // --------------------------------------------------
    // DEFAULT DENY
    // --------------------------------------------------

    @Test
    void shouldDenyWhenNoPoliciesExist() {

        var decision = authorize(Map.of());

        assertFalse(decision.allowed());
        assertEquals(Effect.DENY, decision.effect());
        assertNull(decision.priority());
        assertTrue(decision.policyIds().isEmpty());
    }

    @Test
    void shouldQueryRepositoryWithTenantActionAndResource() {

        authorize(Map.of());

        verify(policyRepository).findCandidates(TENANT_ID, ACTION, RESOURCE);
    }

    @Test
    void shouldDenyWhenNoPolicyMatchesAction() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.getAction().setName("DELETE");

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
        assertEquals(Effect.DENY, decision.effect());
    }

    @Test
    void shouldDenyWhenNoPolicyMatchesResource() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.getResource().setName("CUSTOMERS");

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    @Test
    void shouldMatchActionAndResourceCaseInsensitively() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.getAction().setName("update");
        policy.getResource().setName("inventory");

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertTrue(decision.allowed());
    }

    @Test
    void shouldIgnorePolicyFromAnotherTenantReturnedByRepository() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.setTenantId("another-tenant");

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    // --------------------------------------------------
    // POLICY EFFECT
    // --------------------------------------------------

    @Test
    void shouldAllowWhenMatchingAllowPolicyExists() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.MEDIUM);

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertTrue(decision.allowed());
        assertEquals(Effect.ALLOW, decision.effect());
        assertEquals(PolicyPriority.MEDIUM, decision.priority());
    }

    @Test
    void shouldDenyWhenMatchingDenyPolicyExists() {

        Policy policy = createPolicy(Effect.DENY, PolicyPriority.HIGH);

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
        assertEquals(Effect.DENY, decision.effect());
    }

    // --------------------------------------------------
    // PRIORITY RESOLUTION
    // --------------------------------------------------

    @Test
    void shouldSelectHighestPriorityPolicy() {

        Policy lowPolicy = createPolicy(Effect.ALLOW, PolicyPriority.LOW);
        Policy criticalPolicy = createPolicy(Effect.DENY, PolicyPriority.CRITICAL);

        var decision = authorizeWithPolicies(
                List.of(lowPolicy, criticalPolicy),
                Map.of()
        );

        assertFalse(decision.allowed());
        assertEquals(PolicyPriority.CRITICAL, decision.priority());
        assertEquals(List.of(criticalPolicy.getId()), decision.policyIds());
    }

    @Test
    void shouldAllowWhenHigherPriorityAllowOverridesLowerDeny() {

        Policy denyPolicy = createPolicy(Effect.DENY, PolicyPriority.LOW);
        Policy allowPolicy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        var decision = authorizeWithPolicies(
                List.of(denyPolicy, allowPolicy),
                Map.of()
        );

        assertTrue(decision.allowed());
        assertEquals(PolicyPriority.HIGH, decision.priority());
    }

    @Test
    void shouldDenyWhenHigherPriorityDenyOverridesLowerAllow() {

        Policy allowPolicy = createPolicy(Effect.ALLOW, PolicyPriority.MEDIUM);
        Policy denyPolicy = createPolicy(Effect.DENY, PolicyPriority.CRITICAL);

        var decision = authorizeWithPolicies(
                List.of(allowPolicy, denyPolicy),
                Map.of()
        );

        assertFalse(decision.allowed());
        assertEquals(PolicyPriority.CRITICAL, decision.priority());
    }

    @Test
    void shouldDenyWhenAllowAndDenyHaveSamePriority() {

        Policy allowPolicy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        Policy denyPolicy = createPolicy(Effect.DENY, PolicyPriority.HIGH);

        var decision = authorizeWithPolicies(
                List.of(allowPolicy, denyPolicy),
                Map.of()
        );

        assertFalse(decision.allowed());
        assertEquals(Effect.DENY, decision.effect());
        assertEquals(List.of(denyPolicy.getId()), decision.policyIds());
    }

    @Test
    void shouldReturnAllMatchingAllowPolicyIdsAtHighestPriority() {

        Policy first = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        Policy second = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        var decision = authorizeWithPolicies(List.of(first, second), Map.of());

        assertTrue(decision.allowed());
        assertEquals(2, decision.policyIds().size());
        assertTrue(decision.policyIds().contains(first.getId()));
        assertTrue(decision.policyIds().contains(second.getId()));
    }

    @Test
    void shouldRankMediumAboveModerate() {

        Policy moderateDeny = createPolicy(Effect.DENY, PolicyPriority.MODERATE);
        Policy mediumAllow = createPolicy(Effect.ALLOW, PolicyPriority.MEDIUM);

        var decision = authorizeWithPolicies(
                List.of(moderateDeny, mediumAllow),
                Map.of()
        );

        assertTrue(decision.allowed());
        assertEquals(PolicyPriority.MEDIUM, decision.priority());
    }

    @Test
    void shouldRankModerateAboveLow() {

        Policy lowAllow = createPolicy(Effect.ALLOW, PolicyPriority.LOW);
        Policy moderateDeny = createPolicy(Effect.DENY, PolicyPriority.MODERATE);

        var decision = authorizeWithPolicies(
                List.of(lowAllow, moderateDeny),
                Map.of()
        );

        assertFalse(decision.allowed());
        assertEquals(PolicyPriority.MODERATE, decision.priority());
    }

    // --------------------------------------------------
    // NULL PRIORITY (treated as LOW, never silently dropped)
    // --------------------------------------------------

    @Test
    void shouldNotDropDenyPolicyWithNullPriority() {

        Policy deny = createPolicy(Effect.DENY, null);

        var decision = authorizeWithPolicies(List.of(deny), Map.of());

        assertFalse(decision.allowed());
        assertEquals(List.of(deny.getId()), decision.policyIds());
    }

    @Test
    void shouldLetNullPriorityDenyWinTieAgainstLowAllow() {

        Policy deny = createPolicy(Effect.DENY, null);
        Policy allow = createPolicy(Effect.ALLOW, PolicyPriority.LOW);

        var decision = authorizeWithPolicies(List.of(allow, deny), Map.of());

        assertFalse(decision.allowed());
        assertEquals(PolicyPriority.LOW, decision.priority());
    }

    @Test
    void shouldLetHigherPriorityAllowOverrideNullPriorityDeny() {

        Policy deny = createPolicy(Effect.DENY, null);
        Policy allow = createPolicy(Effect.ALLOW, PolicyPriority.MEDIUM);

        var decision = authorizeWithPolicies(List.of(deny, allow), Map.of());

        assertTrue(decision.allowed());
        assertEquals(PolicyPriority.MEDIUM, decision.priority());
    }

    // --------------------------------------------------
    // SUBJECT MATCHING
    // --------------------------------------------------

    @Test
    void shouldMatchUserSubject() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertTrue(decision.allowed());
    }

    @Test
    void shouldNotMatchUserSubjectWithDifferentCase() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.setSubjects(new HashSet<>(Set.of(
                createSubject(SubjectType.USER, "USER-1"))));

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    @Test
    void shouldMatchRoleSubject() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.ROLE, "WAREHOUSE_MANAGER")
        ));

        stub(List.of(policy));

        var decision = policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                List.of("EMPLOYEE", "WAREHOUSE_MANAGER"),
                null,
                null,
                ACTION,
                RESOURCE,
                Map.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldMatchRoleSubjectCaseInsensitively() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.ROLE, "warehouse_manager")
        ));

        stub(List.of(policy));

        var decision = policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                List.of("WAREHOUSE_MANAGER"),
                null,
                null,
                ACTION,
                RESOURCE,
                Map.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldNotMatchRoleSubjectWhenRolesAreNull() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.ROLE, "WAREHOUSE_MANAGER")
        ));

        stub(List.of(policy));

        var decision = policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                null,
                null,
                null,
                ACTION,
                RESOURCE,
                Map.of()
        );

        assertFalse(decision.allowed());
    }

    @Test
    void shouldStillMatchUserSubjectWhenRolesAreNull() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        stub(List.of(policy));

        var decision = policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                null,
                null,
                null,
                ACTION,
                RESOURCE,
                Map.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldMatchDepartmentSubject() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.DEPARTMENT, "department-1")
        ));

        var decision = authorizeWithDepartment(policy);

        assertTrue(decision.allowed());
    }

    @Test
    void shouldNotMatchDepartmentSubjectWithDifferentCase() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.DEPARTMENT, "DEPARTMENT-1")
        ));

        var decision = authorizeWithDepartment(policy);

        assertFalse(decision.allowed());
    }

    @Test
    void shouldMatchServiceSubject() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(Set.of(
                createSubject(SubjectType.SERVICE, "inventory-service")
        ));

        var decision = authorizeWithService(policy);

        assertTrue(decision.allowed());
    }

    @Test
    void shouldNotMatchSubjectFromAnotherTenant() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.getSubjects().iterator().next()
                .setTenantId("another-tenant");

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    @Test
    void shouldNotMatchPolicyWithoutSubjects() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setSubjects(new HashSet<>());

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    // --------------------------------------------------
    // TRUSTED CONTEXT PRECEDENCE
    // --------------------------------------------------

    @Test
    void shouldNotAllowCallerToOverrideTrustedUserId() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup group = createGroup(LogicalOperator.AND);
        group.getConditions().add(createCondition(
                ConditionAttribute.USER_ID,
                ConditionOperator.EQUALS,
                "admin"
        ));
        policy.setConditionGroups(new ArrayList<>(List.of(group)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(ConditionAttribute.USER_ID, "admin")
        );

        assertFalse(decision.allowed());
    }

    @Test
    void shouldUseTrustedUserIdInConditions() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup group = createGroup(LogicalOperator.AND);
        group.getConditions().add(createCondition(
                ConditionAttribute.USER_ID,
                ConditionOperator.EQUALS,
                USER_ID
        ));
        policy.setConditionGroups(new ArrayList<>(List.of(group)));

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertTrue(decision.allowed());
    }

    // --------------------------------------------------
    // CONDITION GROUPS
    // --------------------------------------------------

    @Test
    void shouldAllowWhenPolicyHasNoConditionGroups() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        policy.setConditionGroups(new ArrayList<>());

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertTrue(decision.allowed());
    }

    @Test
    void shouldEvaluateRootAndGroup() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup root = createGroup(LogicalOperator.AND);

        root.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK"));

        root.getConditions().add(createCondition(
                ConditionAttribute.DEVICE_TYPE, ConditionOperator.EQUALS, "DESKTOP"));

        policy.setConditionGroups(new ArrayList<>(List.of(root)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(
                        ConditionAttribute.COUNTRY, "LK",
                        ConditionAttribute.DEVICE_TYPE, "DESKTOP"
                )
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldRejectRootAndGroupWhenOneConditionFails() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup root = createGroup(LogicalOperator.AND);

        root.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK"));

        root.getConditions().add(createCondition(
                ConditionAttribute.DEVICE_TYPE, ConditionOperator.EQUALS, "DESKTOP"));

        policy.setConditionGroups(new ArrayList<>(List.of(root)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(
                        ConditionAttribute.COUNTRY, "LK",
                        ConditionAttribute.DEVICE_TYPE, "MOBILE"
                )
        );

        assertFalse(decision.allowed());
    }

    @Test
    void shouldEvaluateOrGroup() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup root = createGroup(LogicalOperator.OR);

        root.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "US"));

        root.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK"));

        policy.setConditionGroups(new ArrayList<>(List.of(root)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(ConditionAttribute.COUNTRY, "LK")
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldEvaluateNestedConditionGroups() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup root = createGroup(LogicalOperator.AND);

        root.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK"));

        PolicyConditionGroup nested = createGroup(LogicalOperator.OR);

        nested.getConditions().add(createCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR, "192.168.1.0/24"));

        nested.getConditions().add(createCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR, "10.0.0.0/8"));

        root.getChildGroups().add(nested);

        policy.setConditionGroups(new ArrayList<>(List.of(root)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(
                        ConditionAttribute.COUNTRY, "LK",
                        ConditionAttribute.IP_ADDRESS, "10.0.0.5"
                )
        );

        assertTrue(decision.allowed());
    }

    @Test
    void shouldRejectEmptyConditionGroup() {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup root = createGroup(LogicalOperator.AND);

        policy.setConditionGroups(new ArrayList<>(List.of(root)));

        var decision = authorizeWithPolicies(List.of(policy), Map.of());

        assertFalse(decision.allowed());
    }

    // --------------------------------------------------
    // CONDITION OPERATORS
    // --------------------------------------------------

    @Test
    void shouldEvaluateEquals() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.EQUALS,
                "LK", "LK", true);
    }

    @Test
    void shouldEvaluateNotEquals() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.NOT_EQUALS,
                "US", "LK", true);
    }

    @Test
    void shouldEvaluateIn() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.IN,
                "LK", "US,LK,IN", true);
    }

    @Test
    void shouldEvaluateNotIn() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.NOT_IN,
                "LK", "US,IN,UK", true);
    }

    @Test
    void shouldEvaluateContains() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.CONTAINS,
                "SRI-LANKA", "LANK", true);
    }

    @Test
    void shouldEvaluateGreaterThan() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.GREATER_THAN,
                LocalTime.of(15, 30), "14:00", true);
    }

    @Test
    void shouldEvaluateLessThan() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.LESS_THAN,
                LocalTime.of(9, 0), "14:00", true);
    }

    @Test
    void shouldEvaluateBetween() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(10, 0), "09:00,17:00", true);
    }

    @Test
    void shouldTreatBetweenBoundsAsInclusive() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(9, 0), "09:00,17:00", true);
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(17, 0), "09:00,17:00", true);
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(17, 1), "09:00,17:00", false);
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(8, 59), "09:00,17:00", false);
    }

    @Test
    void shouldEvaluateExists() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.EXISTS,
                "LK", null, true);
    }

    @Test
    void shouldEvaluateNotExists() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.NOT_EXISTS,
                null, null, true);
    }

    @Test
    void shouldRejectEqualsWhenAttributeIsMissing() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.EQUALS,
                null, "LK", false);
    }

    @Test
    void shouldRejectNotEqualsWhenAttributeIsMissing() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.NOT_EQUALS,
                null, "LK", false);
    }

    @Test
    void shouldRejectNotInWhenAttributeIsMissing() {
        assertCondition(ConditionAttribute.COUNTRY, ConditionOperator.NOT_IN,
                null, "US,UK", false);
    }

    @Test
    void shouldRejectInvalidBetweenExpression() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.BETWEEN,
                LocalTime.of(10, 0), "09:00", false);
    }

    @Test
    void shouldRejectUnparsableComparisonValue() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.GREATER_THAN,
                LocalTime.of(10, 0), "abc", false);
    }

    // --------------------------------------------------
    // DAY OF WEEK
    // --------------------------------------------------

    @Test
    void shouldCompareDayOfWeek() {
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.GREATER_THAN,
                DayOfWeek.WEDNESDAY, "TUESDAY", true);
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.LESS_THAN,
                DayOfWeek.WEDNESDAY, "tuesday", false);
    }

    @Test
    void shouldEvaluateDayOfWeekBetween() {
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.BETWEEN,
                DayOfWeek.WEDNESDAY, "MONDAY,FRIDAY", true);
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.BETWEEN,
                DayOfWeek.SUNDAY, "MONDAY,FRIDAY", false);
    }

    @Test
    void shouldEvaluateDayOfWeekEqualsAndIn() {
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.EQUALS,
                DayOfWeek.MONDAY, "monday", true);
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.IN,
                DayOfWeek.SATURDAY, "SATURDAY,SUNDAY", true);
    }

    @Test
    void shouldRejectInvalidDayOfWeekExpression() {
        assertCondition(ConditionAttribute.DAY_OF_WEEK, ConditionOperator.GREATER_THAN,
                DayOfWeek.WEDNESDAY, "FUNDAY", false);
    }

    // --------------------------------------------------
    // CIDR
    // --------------------------------------------------

    @Test
    void shouldMatchIpv4Cidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.25", "192.168.1.0/24", true);
    }

    @Test
    void shouldRejectIpv4OutsideCidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.2.25", "192.168.1.0/24", false);
    }

    @Test
    void shouldMatchIpv6Cidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "2001:db8::1", "2001:db8::/32", true);
    }

    @Test
    void shouldRejectIpv6OutsideCidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "2001:db9::1", "2001:db8::/32", false);
    }

    @Test
    void shouldRejectInvalidCidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.25", "192.168.1.0/99", false);
    }

    @Test
    void shouldRejectIpv4AddressAgainstIpv6Cidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.25", "2001:db8::/32", false);
    }

    @Test
    void shouldRejectIpv6AddressAgainstIpv4Cidr() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "2001:db8::1", "192.168.1.0/24", false);
    }

    @Test
    void shouldMatchNonByteAlignedCidrPrefix() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.130", "192.168.1.128/25", true);
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.100", "192.168.1.128/25", false);
    }

    @Test
    void shouldTreatIpv4MappedIpv6AsIpv4() {
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "::ffff:10.0.0.5", "10.0.0.0/8", true);
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "::ffff:192.168.1.5", "10.0.0.0/8", false);
        assertCondition(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "::ffff:10.0.0.5", "2001:db8::/32", false);
    }

    // --------------------------------------------------
    // TIME COMPARISON
    // --------------------------------------------------

    @Test
    void shouldCompareLocalTimeBoundaries() {
        assertCondition(ConditionAttribute.TIME, ConditionOperator.GREATER_THAN,
                LocalTime.of(14, 0), "14:00", false);
        assertCondition(ConditionAttribute.TIME, ConditionOperator.LESS_THAN,
                LocalTime.of(14, 0), "14:00", false);
    }

    // --------------------------------------------------
    // TEST HELPERS
    // --------------------------------------------------

    private void stub(List<Policy> policies) {
        doReturn(policies)
                .when(policyRepository)
                .findCandidates(eq(TENANT_ID), any(), any());
    }

    private void assertCondition(
            ConditionAttribute attribute,
            ConditionOperator operator,
            Object actualValue,
            String expectedValue,
            boolean expectedResult
    ) {

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);

        PolicyConditionGroup group = createGroup(LogicalOperator.AND);

        group.getConditions().add(
                createCondition(attribute, operator, expectedValue)
        );

        policy.setConditionGroups(new ArrayList<>(List.of(group)));

        Map<ConditionAttribute, Object> attributes =
                actualValue == null
                        ? Map.of()
                        : Map.of(attribute, actualValue);

        var decision = authorizeWithPolicies(List.of(policy), attributes);

        assertEquals(
                expectedResult,
                decision.allowed(),
                "Unexpected result for operator: " + operator
        );
    }

    private Policy createPolicy(
            Effect effect,
            PolicyPriority priority
    ) {

        Policy policy = new Policy();

        policy.setId(UUID.randomUUID());
        policy.setTenantId(TENANT_ID);
        policy.setName("Test Policy");
        policy.setDescription("Policy Engine test");

        Action action = new Action();
        action.setName(ACTION);

        Resource resource = new Resource();
        resource.setName(RESOURCE);

        policy.setAction(action);
        policy.setResource(resource);
        policy.setEffect(effect);
        policy.setPriority(priority);

        policy.setSubjects(new HashSet<>(
                Set.of(createSubject(SubjectType.USER, USER_ID))
        ));

        policy.setConditionGroups(new ArrayList<>());

        return policy;
    }

    private PolicySubject createSubject(
            SubjectType type,
            String subjectId
    ) {

        PolicySubject subject = new PolicySubject();

        subject.setTenantId(TENANT_ID);
        subject.setSubjectType(type);
        subject.setSubjectId(subjectId);

        return subject;
    }

    private PolicyConditionGroup createGroup(
            LogicalOperator operator
    ) {

        PolicyConditionGroup group = new PolicyConditionGroup();

        group.setLogicalOperator(operator);
        group.setConditions(new ArrayList<>());
        group.setChildGroups(new ArrayList<>());

        return group;
    }

    private PolicyCondition createCondition(
            ConditionAttribute attribute,
            ConditionOperator operator,
            String expectedValue
    ) {

        PolicyCondition condition = new PolicyCondition();

        condition.setAttribute(attribute);
        condition.setOperator(operator);
        condition.setExpectedValue(expectedValue);

        return condition;
    }

    private com.manucore.policy.dto.AuthorizationDecision
    authorize(Map<ConditionAttribute, ?> attributes) {

        return policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                List.of(),
                null,
                null,
                ACTION,
                RESOURCE,
                attributes
        );
    }

    private com.manucore.policy.dto.AuthorizationDecision
    authorizeWithPolicies(
            List<Policy> policies,
            Map<ConditionAttribute, ?> attributes
    ) {

        stub(policies);

        return authorize(attributes);
    }

    private com.manucore.policy.dto.AuthorizationDecision
    authorizeWithDepartment(Policy policy) {

        stub(List.of(policy));

        return policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                List.of(),
                "department-1",
                null,
                ACTION,
                RESOURCE,
                Map.of()
        );
    }

    private com.manucore.policy.dto.AuthorizationDecision
    authorizeWithService(Policy policy) {

        stub(List.of(policy));

        return policyEngine.authorize(
                TENANT_ID,
                USER_ID,
                List.of(),
                null,
                "inventory-service",
                ACTION,
                RESOURCE,
                Map.of()
        );
    }

    // --------------------------------------------------
    // CONDITION GROUP TRUTH-TABLE HELPERS
    // --------------------------------------------------

    private PolicyCondition cond(boolean result) {
        return createCondition(
                ConditionAttribute.COUNTRY,
                ConditionOperator.EQUALS,
                result ? "LK" : "XX");
    }

    private PolicyConditionGroup group(LogicalOperator op, boolean... results) {
        PolicyConditionGroup g = createGroup(op);
        for (boolean r : results) {
            g.getConditions().add(cond(r));
        }
        return g;
    }

    private boolean evaluateRoots(List<PolicyConditionGroup> roots) {
        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.setConditionGroups(new ArrayList<>(roots));

        return authorizeWithPolicies(
                List.of(policy),
                Map.of(ConditionAttribute.COUNTRY, "LK")
        ).allowed();
    }

    private static boolean combine(LogicalOperator op, boolean... values) {
        boolean all = true;
        boolean any = false;
        for (boolean v : values) {
            all &= v;
            any |= v;
        }
        return op == LogicalOperator.AND ? all : any;
    }

    private static boolean bit(int bits, int index) {
        return ((bits >> index) & 1) == 1;
    }

    // --------------------------------------------------
    // MULTIPLE ROOT GROUPS (exhaustive)
    // --------------------------------------------------

    static Stream<Arguments> twoRootCases() {
        List<Arguments> cases = new ArrayList<>();
        for (LogicalOperator op1 : LogicalOperator.values())
            for (LogicalOperator op2 : LogicalOperator.values())
                for (int bits = 0; bits < 16; bits++)
                    cases.add(Arguments.of(op1, op2,
                            bit(bits, 0), bit(bits, 1), bit(bits, 2), bit(bits, 3)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("twoRootCases")
    void twoRootGroups_areCombinedWithAnd(
            LogicalOperator op1, LogicalOperator op2,
            boolean a, boolean b, boolean c, boolean d) {

        boolean expected = combine(op1, a, b) && combine(op2, c, d);

        assertEquals(expected, evaluateRoots(List.of(
                group(op1, a, b),
                group(op2, c, d))));
    }

    static Stream<Arguments> threeRootCases() {
        List<Arguments> cases = new ArrayList<>();
        for (LogicalOperator op1 : LogicalOperator.values())
            for (LogicalOperator op2 : LogicalOperator.values())
                for (LogicalOperator op3 : LogicalOperator.values())
                    for (int bits = 0; bits < 64; bits++)
                        cases.add(Arguments.of(op1, op2, op3,
                                bit(bits, 0), bit(bits, 1), bit(bits, 2),
                                bit(bits, 3), bit(bits, 4), bit(bits, 5)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("threeRootCases")
    void threeRootGroups_areCombinedWithAnd(
            LogicalOperator op1, LogicalOperator op2, LogicalOperator op3,
            boolean a, boolean b, boolean c, boolean d, boolean e, boolean f) {

        boolean expected = combine(op1, a, b)
                && combine(op2, c, d)
                && combine(op3, e, f);

        assertEquals(expected, evaluateRoots(List.of(
                group(op1, a, b),
                group(op2, c, d),
                group(op3, e, f))));
    }

    // --------------------------------------------------
    // NESTED / CHAINED GROUPS (exhaustive)
    // --------------------------------------------------

    static Stream<Arguments> directPlusChildCases() {
        List<Arguments> cases = new ArrayList<>();
        for (LogicalOperator parent : LogicalOperator.values())
            for (LogicalOperator child : LogicalOperator.values())
                for (int bits = 0; bits < 16; bits++)
                    cases.add(Arguments.of(parent, child,
                            bit(bits, 0), bit(bits, 1), bit(bits, 2), bit(bits, 3)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("directPlusChildCases")
    void parentWithDirectConditionsAndChildGroup(
            LogicalOperator parentOp, LogicalOperator childOp,
            boolean a, boolean b, boolean c, boolean d) {

        PolicyConditionGroup root = group(parentOp, a, b);
        root.getChildGroups().add(group(childOp, c, d));

        boolean expected = combine(parentOp, a, b, combine(childOp, c, d));

        assertEquals(expected, evaluateRoots(List.of(root)));
    }

    static Stream<Arguments> twoChildrenNoDirectCases() {
        List<Arguments> cases = new ArrayList<>();
        for (LogicalOperator parent : LogicalOperator.values())
            for (LogicalOperator c1 : LogicalOperator.values())
                for (LogicalOperator c2 : LogicalOperator.values())
                    for (int bits = 0; bits < 16; bits++)
                        cases.add(Arguments.of(parent, c1, c2,
                                bit(bits, 0), bit(bits, 1), bit(bits, 2), bit(bits, 3)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("twoChildrenNoDirectCases")
    void parentWithOnlyChildGroups(
            LogicalOperator parentOp, LogicalOperator op1, LogicalOperator op2,
            boolean a, boolean b, boolean c, boolean d) {

        PolicyConditionGroup root = createGroup(parentOp);
        root.getChildGroups().add(group(op1, a, b));
        root.getChildGroups().add(group(op2, c, d));

        boolean expected = combine(parentOp,
                combine(op1, a, b),
                combine(op2, c, d));

        assertEquals(expected, evaluateRoots(List.of(root)));
    }

    static Stream<Arguments> deepChainCases() {
        List<Arguments> cases = new ArrayList<>();
        for (LogicalOperator op1 : LogicalOperator.values())
            for (LogicalOperator op2 : LogicalOperator.values())
                for (LogicalOperator op3 : LogicalOperator.values())
                    for (int bits = 0; bits < 16; bits++)
                        cases.add(Arguments.of(op1, op2, op3,
                                bit(bits, 0), bit(bits, 1), bit(bits, 2), bit(bits, 3)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("deepChainCases")
    void threeLevelChain(
            LogicalOperator op1, LogicalOperator op2, LogicalOperator op3,
            boolean a, boolean b, boolean c, boolean d) {

        // root(op1: a, child(op2: b, grandchild(op3: c, d)))
        PolicyConditionGroup grandchild = group(op3, c, d);
        PolicyConditionGroup child = group(op2, b);
        child.getChildGroups().add(grandchild);
        PolicyConditionGroup root = group(op1, a);
        root.getChildGroups().add(child);

        boolean expected = combine(op1, a,
                combine(op2, b, combine(op3, c, d)));

        assertEquals(expected, evaluateRoots(List.of(root)));
    }

    static Stream<Arguments> multiRootNestedCases() {
        List<Arguments> cases = new ArrayList<>();
        for (int ops = 0; ops < 16; ops++)
            for (int bits = 0; bits < 16; bits++)
                cases.add(Arguments.of(
                        bit(ops, 0) ? LogicalOperator.AND : LogicalOperator.OR,
                        bit(ops, 1) ? LogicalOperator.AND : LogicalOperator.OR,
                        bit(ops, 2) ? LogicalOperator.AND : LogicalOperator.OR,
                        bit(ops, 3) ? LogicalOperator.AND : LogicalOperator.OR,
                        bit(bits, 0), bit(bits, 1), bit(bits, 2), bit(bits, 3)));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("multiRootNestedCases")
    void multipleRootsEachWithNestedChild(
            LogicalOperator rootOp1, LogicalOperator childOp1,
            LogicalOperator rootOp2, LogicalOperator childOp2,
            boolean a, boolean b, boolean c, boolean d) {

        PolicyConditionGroup root1 = group(rootOp1, a);
        root1.getChildGroups().add(group(childOp1, b));

        PolicyConditionGroup root2 = group(rootOp2, c);
        root2.getChildGroups().add(group(childOp2, d));

        boolean expected = combine(rootOp1, a, combine(childOp1, b))
                && combine(rootOp2, c, combine(childOp2, d));

        assertEquals(expected, evaluateRoots(List.of(root1, root2)));
    }

    // --------------------------------------------------
    // EDGE CASES IN GROUP TREES
    // --------------------------------------------------

    @Test
    void emptyChildGroupBreaksAndParent() {
        PolicyConditionGroup root = group(LogicalOperator.AND, true);
        root.getChildGroups().add(createGroup(LogicalOperator.AND)); // empty -> false

        assertFalse(evaluateRoots(List.of(root)));
    }

    @Test
    void emptyChildGroupIsIgnoredByOrParentWhenSiblingMatches() {
        PolicyConditionGroup root = group(LogicalOperator.OR, true);
        root.getChildGroups().add(createGroup(LogicalOperator.AND));

        assertTrue(evaluateRoots(List.of(root)));
    }

    @Test
    void orParentWithOnlyEmptyChildDoesNotMatch() {
        PolicyConditionGroup root = createGroup(LogicalOperator.OR);
        root.getChildGroups().add(createGroup(LogicalOperator.OR));

        assertFalse(evaluateRoots(List.of(root)));
    }

    @Test
    void secondEmptyRootGroupDeniesEvenWhenFirstMatches() {
        assertFalse(evaluateRoots(List.of(
                group(LogicalOperator.AND, true),
                createGroup(LogicalOperator.AND))));
    }

    @Test
    void childGroupWithNullOperatorFailsClosed() {
        PolicyConditionGroup bad = group(LogicalOperator.AND, true);
        bad.setLogicalOperator(null);

        PolicyConditionGroup andRoot = group(LogicalOperator.AND, true);
        andRoot.getChildGroups().add(bad);
        assertFalse(evaluateRoots(List.of(andRoot)));

        PolicyConditionGroup orRoot = group(LogicalOperator.OR, true);
        orRoot.getChildGroups().add(bad);
        assertTrue(evaluateRoots(List.of(orRoot))); // sibling still matches
    }

    @Test
    void nullRootGroupInListFailsClosed() {
        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.setConditionGroups(new ArrayList<>(
                Arrays.asList(group(LogicalOperator.AND, true), null)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(ConditionAttribute.COUNTRY, "LK"));

        assertFalse(decision.allowed());
    }

    @Test
    void missingAttributeInsideChainOnlyFailsItsOwnBranch() {
        // OR( NOT_EQUALS on missing attribute -> false, COUNTRY EQUALS LK -> true )
        PolicyConditionGroup root = createGroup(LogicalOperator.OR);
        root.getConditions().add(createCondition(
                ConditionAttribute.DEVICE_TYPE, ConditionOperator.NOT_EQUALS, "MOBILE"));
        root.getConditions().add(cond(true));

        assertTrue(evaluateRoots(List.of(root)));

        // same condition under AND -> whole group fails
        PolicyConditionGroup andRoot = createGroup(LogicalOperator.AND);
        andRoot.getConditions().add(createCondition(
                ConditionAttribute.DEVICE_TYPE, ConditionOperator.NOT_EQUALS, "MOBILE"));
        andRoot.getConditions().add(cond(true));

        assertFalse(evaluateRoots(List.of(andRoot)));
    }

    @Test
    void invalidConditionInsideChainFailsClosed() {
        PolicyCondition broken = createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK");
        broken.setOperator(null);

        PolicyConditionGroup andRoot = createGroup(LogicalOperator.AND);
        andRoot.getConditions().add(broken);
        andRoot.getConditions().add(cond(true));
        assertFalse(evaluateRoots(List.of(andRoot)));

        PolicyConditionGroup orRoot = createGroup(LogicalOperator.OR);
        orRoot.getConditions().add(broken);
        orRoot.getConditions().add(cond(true));
        assertTrue(evaluateRoots(List.of(orRoot)));
    }

    // --------------------------------------------------
    // DEPTH GUARD
    // --------------------------------------------------

    private PolicyConditionGroup chain(LogicalOperator op, int levels) {
        PolicyConditionGroup root = group(op, true);
        PolicyConditionGroup current = root;
        for (int i = 0; i < levels; i++) {
            PolicyConditionGroup next = group(op, true);
            current.getChildGroups().add(next);
            current = next;
        }
        return root;
    }

    @Test
    void shouldAllowReasonablyDeepGroupNesting() {
        assertTrue(evaluateRoots(List.of(chain(LogicalOperator.AND, 10))));
    }

    @Test
    void shouldFailClosedWhenAllowPolicyIsTooDeep() {
        assertFalse(evaluateRoots(List.of(chain(LogicalOperator.AND, 100))));
        assertFalse(evaluateRoots(List.of(chain(LogicalOperator.OR, 100))));
    }

    @Test
    void shouldFailClosedWhenDenyPolicyIsTooDeep() {
        assertTrue(denyMatches(
                List.of(chain(LogicalOperator.AND, 100)),
                Map.of(ConditionAttribute.COUNTRY, "LK")));
    }

    @Test
    void shouldFailClosedOnCyclicGroupForAllowAndDeny() {
        // OR(true, <itself>) would evaluate to true at every level without the guard
        PolicyConditionGroup cyclic = group(LogicalOperator.OR, true);
        cyclic.getChildGroups().add(cyclic);

        assertFalse(evaluateRoots(List.of(cyclic)));
        assertTrue(denyMatches(
                List.of(cyclic),
                Map.of(ConditionAttribute.COUNTRY, "LK")));
    }

    // --------------------------------------------------
    // REALISTIC MIXED-OPERATOR SCENARIO
    // --------------------------------------------------

    static Stream<Arguments> officeAccessCases() {
        //            time              ip            device       country  expected
        return Stream.of(
                Arguments.of(LocalTime.of(10, 0), "10.1.2.3", "MOBILE",    "LK", true),  // internal IP
                Arguments.of(LocalTime.of(10, 0), "8.8.8.8",  "CORPORATE", "LK", true),  // corp device
                Arguments.of(LocalTime.of(10, 0), "8.8.8.8",  "MOBILE",    "LK", false), // neither
                Arguments.of(LocalTime.of(20, 0), "10.1.2.3", "CORPORATE", "LK", false), // after hours
                Arguments.of(LocalTime.of(9, 0),  "10.1.2.3", "CORPORATE", "IN", true),  // lower bound
                Arguments.of(LocalTime.of(17, 0), "10.1.2.3", "CORPORATE", "LK", true),  // upper bound
                Arguments.of(LocalTime.of(10, 0), "10.1.2.3", "CORPORATE", "US", false)  // country
        );
    }

    @ParameterizedTest
    @MethodSource("officeAccessCases")
    void officeAccessPolicy(
            LocalTime time, String ip, String device, String country, boolean expected) {

        // (TIME BETWEEN 09:00-17:00)
        // AND (IP IN_CIDR 10.0.0.0/8 OR DEVICE_TYPE = CORPORATE)
        // AND (COUNTRY IN LK,IN)
        PolicyConditionGroup hours = createGroup(LogicalOperator.AND);
        hours.getConditions().add(createCondition(
                ConditionAttribute.TIME, ConditionOperator.BETWEEN, "09:00,17:00"));

        PolicyConditionGroup network = createGroup(LogicalOperator.OR);
        network.getConditions().add(createCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR, "10.0.0.0/8"));
        network.getConditions().add(createCondition(
                ConditionAttribute.DEVICE_TYPE, ConditionOperator.EQUALS, "CORPORATE"));

        PolicyConditionGroup geo = createGroup(LogicalOperator.AND);
        geo.getConditions().add(createCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.IN, "LK,IN"));

        Policy policy = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        policy.setConditionGroups(new ArrayList<>(List.of(hours, network, geo)));

        var decision = authorizeWithPolicies(
                List.of(policy),
                Map.of(
                        ConditionAttribute.TIME, time,
                        ConditionAttribute.IP_ADDRESS, ip,
                        ConditionAttribute.DEVICE_TYPE, device,
                        ConditionAttribute.COUNTRY, country));

        assertEquals(expected, decision.allowed());
    }

    // --------------------------------------------------
    // MULTIPLE POLICIES WITH CONDITION GROUPS
    // --------------------------------------------------

    @Test
    void policyWithFailingConditionsDoesNotParticipateInPriorityResolution() {
        Policy criticalDeny = createPolicy(Effect.DENY, PolicyPriority.CRITICAL);
        criticalDeny.setConditionGroups(new ArrayList<>(List.of(
                group(LogicalOperator.AND, false))));

        Policy lowAllow = createPolicy(Effect.ALLOW, PolicyPriority.LOW);
        lowAllow.setConditionGroups(new ArrayList<>(List.of(
                group(LogicalOperator.AND, true))));

        var decision = authorizeWithPolicies(
                List.of(criticalDeny, lowAllow),
                Map.of(ConditionAttribute.COUNTRY, "LK"));

        assertTrue(decision.allowed());
        assertEquals(PolicyPriority.LOW, decision.priority());
        assertEquals(List.of(lowAllow.getId()), decision.policyIds());
    }

    @Test
    void denyWinsAtSamePriorityWhenBothHaveMatchingGroups() {
        Policy allow = createPolicy(Effect.ALLOW, PolicyPriority.HIGH);
        allow.setConditionGroups(new ArrayList<>(List.of(
                group(LogicalOperator.OR, false, true))));

        Policy deny = createPolicy(Effect.DENY, PolicyPriority.HIGH);
        deny.setConditionGroups(new ArrayList<>(List.of(
                group(LogicalOperator.AND, true, true))));

        var decision = authorizeWithPolicies(
                List.of(allow, deny),
                Map.of(ConditionAttribute.COUNTRY, "LK"));

        assertFalse(decision.allowed());
        assertEquals(List.of(deny.getId()), decision.policyIds());
    }

    // --------------------------------------------------
    // DENY POLICIES FAIL CLOSED
    // --------------------------------------------------

    @Test
    void shouldNotLetMissingAttributeBypassDenyPolicy() {

        Policy allow = createPolicy(Effect.ALLOW, PolicyPriority.LOW);

        Policy deny = createPolicy(Effect.DENY, PolicyPriority.HIGH);
        PolicyConditionGroup group = createGroup(LogicalOperator.AND);
        group.getConditions().add(
                createCondition(ConditionAttribute.COUNTRY,
                        ConditionOperator.NOT_IN, "US,UK"));
        deny.setConditionGroups(new ArrayList<>(List.of(group)));

        var decision = authorizeWithPolicies(
                List.of(allow, deny),
                Map.of()   // COUNTRY missing
        );

        assertFalse(decision.allowed());
    }

    private boolean denyMatches(List<PolicyConditionGroup> roots, Map<ConditionAttribute, ?> attrs) {
        Policy deny = createPolicy(Effect.DENY, PolicyPriority.HIGH);
        deny.setConditionGroups(new ArrayList<>(roots));

        Policy allow = createPolicy(Effect.ALLOW, PolicyPriority.LOW); // unconditional

        // deny matched <=> access denied
        return !authorizeWithPolicies(List.of(deny, allow), attrs).allowed();
    }

    private boolean denyMatchesWithCondition(
            ConditionAttribute attr, ConditionOperator op, String expected, Object actual) {

        PolicyConditionGroup g = createGroup(LogicalOperator.AND);
        g.getConditions().add(createCondition(attr, op, expected));
        return denyMatches(List.of(g), actual == null ? Map.of() : Map.of(attr, actual));
    }

    @Test
    void denyFailsClosedOnMalformedBetween() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.TIME, ConditionOperator.BETWEEN, "09:00", LocalTime.of(10, 0)));
    }

    @Test
    void denyFailsClosedOnInvalidCidr() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.0/99", "192.168.1.5"));
    }

    @Test
    void denyFailsClosedOnNonIpValue() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "10.0.0.0/8", "not-an-ip"));
    }

    @Test
    void denyFailsClosedOnMalformedIpv6Network() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                ":::/32", "2001:db8::1"));
    }

    @Test
    void denyDoesNotMatchOnIpFamilyMismatch() {
        // IPv4 address vs IPv6 CIDR is a genuine "not in range", not a misconfiguration
        assertFalse(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "2001:db8::/32", "192.168.1.5"));
        assertFalse(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "192.168.1.0/24", "2001:db8::1"));
    }

    @Test
    void denyFailsClosedOnUnparsableComparison() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.TIME, ConditionOperator.GREATER_THAN,
                "abc", LocalTime.of(10, 0)));
    }

    @Test
    void denyFailsClosedOnInvalidDayOfWeekExpression() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.DAY_OF_WEEK, ConditionOperator.GREATER_THAN,
                "FUNDAY", DayOfWeek.WEDNESDAY));
    }

    @Test
    void denyFailsClosedOnNullExpectedValue() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, null, "LK"));
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.IN, null, "LK"));
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.CONTAINS, null, "LK"));
    }

    @Test
    void denyFailsClosedOnEmptyGroup() {
        assertTrue(denyMatches(List.of(createGroup(LogicalOperator.AND)), Map.of()));
    }

    @Test
    void denyFailsClosedOnNullOperatorGroup() {
        PolicyConditionGroup g = group(LogicalOperator.AND, false);
        g.setLogicalOperator(null);
        assertTrue(denyMatches(List.of(g), Map.of(ConditionAttribute.COUNTRY, "LK")));
    }

    @Test
    void denyDoesNotMatchWhenConditionsGenuinelyFalse() {
        // fail-closed must not turn every DENY into a blanket deny
        assertFalse(denyMatchesWithCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "XX", "LK"));
        assertFalse(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "10.0.0.0/8", "192.168.1.5"));
    }

    @Test
    void denyMatchesWhenConditionsGenuinelyTrue() {
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK", "LK"));
        assertTrue(denyMatchesWithCondition(
                ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR,
                "10.0.0.0/8", "10.1.2.3"));
    }

    // DENY truth tables: when every leaf evaluates cleanly, results must equal
    // the ordinary AND/OR logic (no fail-closed paths triggered).
    @ParameterizedTest
    @MethodSource("twoRootCases")
    void denyTwoRootGroups_matchSameAsAllow(
            LogicalOperator op1, LogicalOperator op2,
            boolean a, boolean b, boolean c, boolean d) {

        boolean expected = combine(op1, a, b) && combine(op2, c, d);

        assertEquals(expected, denyMatches(
                List.of(group(op1, a, b), group(op2, c, d)),
                Map.of(ConditionAttribute.COUNTRY, "LK")));
    }

    @ParameterizedTest
    @MethodSource("directPlusChildCases")
    void denyParentWithChild_matchSameAsAllow(
            LogicalOperator parentOp, LogicalOperator childOp,
            boolean a, boolean b, boolean c, boolean d) {

        PolicyConditionGroup root = group(parentOp, a, b);
        root.getChildGroups().add(group(childOp, c, d));

        assertEquals(combine(parentOp, a, b, combine(childOp, c, d)),
                denyMatches(List.of(root), Map.of(ConditionAttribute.COUNTRY, "LK")));
    }

    @ParameterizedTest
    @MethodSource("deepChainCases")
    void denyThreeLevelChain_matchSameAsAllow(
            LogicalOperator op1, LogicalOperator op2, LogicalOperator op3,
            boolean a, boolean b, boolean c, boolean d) {

        PolicyConditionGroup child = group(op2, b);
        child.getChildGroups().add(group(op3, c, d));
        PolicyConditionGroup root = group(op1, a);
        root.getChildGroups().add(child);

        assertEquals(combine(op1, a, combine(op2, b, combine(op3, c, d))),
                denyMatches(List.of(root), Map.of(ConditionAttribute.COUNTRY, "LK")));
    }
}