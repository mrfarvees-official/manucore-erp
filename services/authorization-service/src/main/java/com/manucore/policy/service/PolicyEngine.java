package com.manucore.policy.service;

import com.manucore.policy.Policy;
import com.manucore.policy.PolicyCondition;
import com.manucore.policy.PolicyConditionGroup;
import com.manucore.policy.PolicySubject;
import com.manucore.policy.dto.AuthorizationDecision;
import com.manucore.policy.enums.*;
import com.manucore.policy.repository.PolicyRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Policy decision engine.
 *
 * <h3>Decision model</h3>
 * <ol>
 *   <li>Only policies of the caller's tenant that match action, resource,
 *       subject and conditions participate.</li>
 *   <li>The highest priority among participating policies wins. A policy with
 *       no priority is treated as {@link PolicyPriority#LOW}.</li>
 *   <li>Within the winning priority, DENY beats ALLOW. A higher-priority ALLOW
 *       therefore overrides a lower-priority DENY by design.</li>
 *   <li>No participating policy means deny (default deny).</li>
 * </ol>
 *
 * <h3>Fail-closed evaluation</h3>
 * Missing attributes, malformed expressions, empty groups and over-deep
 * condition trees never grant access: an ALLOW policy then does not match,
 * and a DENY policy does match.
 *
 * <h3>Priority ranks</h3>
 * LOW(1) &lt; MODERATE(2) &lt; MEDIUM(3) &lt; HIGH(4) &lt; CRITICAL(5).
 * MODERATE and MEDIUM are distinct levels kept for backward compatibility.
 *
 * <h3>Identity matching</h3>
 * User, department and service IDs match exactly (case-sensitive).
 * Roles, actions and resources match case-insensitively.
 *
 * <h3>IP matching</h3>
 * IPv4-mapped IPv6 addresses (::ffff:a.b.c.d) are treated as IPv4.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyEngine {

    /** Maximum nesting of condition groups. Deeper trees (or cycles) fail closed. */
    static final int MAX_GROUP_DEPTH = 16;

    private final PolicyRepository policyRepository;

    // --------------------------------------------------
    // AUTHORIZATION
    // --------------------------------------------------

    public AuthorizationDecision authorize(
            String tenantId,
            String userId,
            List<String> roles,
            String departmentId,
            String serviceId,
            String actionName,
            String resourceName,
            Map<ConditionAttribute, ?> attributes
    ) {

        Map<ConditionAttribute, Object> context =
                buildContext(userId, roles, departmentId, attributes);

        List<Policy> matchingPolicies = policyRepository
                .findCandidates(tenantId, actionName, resourceName)
                .stream()
                .filter(Objects::nonNull)
                // Defense in depth: never trust the repository for tenant isolation.
                .filter(policy -> Objects.equals(policy.getTenantId(), tenantId))
                .filter(policy -> matchesAction(policy, actionName))
                .filter(policy -> matchesResource(policy, resourceName))
                .filter(policy -> matchesSubject(
                        policy,
                        tenantId,
                        userId,
                        roles,
                        departmentId,
                        serviceId
                ))
                .filter(policy -> evaluatePolicy(policy, context))
                .toList();

        // Default deny
        if (matchingPolicies.isEmpty()) {
            return denyByDefault();
        }

        // Highest effective priority (null priority counts as LOW)
        PolicyPriority highestPriority = matchingPolicies
                .stream()
                .map(this::effectivePriority)
                .max(Comparator.comparingInt(this::priorityRank))
                .orElseThrow();

        // Keep only highest-priority policies
        List<Policy> highestPriorityPolicies = matchingPolicies
                .stream()
                .filter(policy -> effectivePriority(policy) == highestPriority)
                .toList();

        // Deny wins when priorities are equal
        List<Policy> denyPolicies = highestPriorityPolicies
                .stream()
                .filter(policy -> policy.getEffect() == Effect.DENY)
                .toList();

        if (!denyPolicies.isEmpty()) {
            return new AuthorizationDecision(
                    false,
                    Effect.DENY,
                    highestPriority,
                    denyPolicies.stream()
                            .map(Policy::getId)
                            .toList(),
                    "Access denied by a matching policy."
            );
        }

        // Otherwise allow
        List<Policy> allowPolicies = highestPriorityPolicies
                .stream()
                .filter(policy -> policy.getEffect() == Effect.ALLOW)
                .toList();

        if (allowPolicies.isEmpty()) {
            return denyByDefault();
        }

        return new AuthorizationDecision(
                true,
                Effect.ALLOW,
                highestPriority,
                allowPolicies.stream()
                        .map(Policy::getId)
                        .toList(),
                "Access granted by a matching policy."
        );
    }

    // --------------------------------------------------
    // CONTEXT
    // --------------------------------------------------

    private Map<ConditionAttribute, Object> buildContext(
            String userId,
            List<String> roles,
            String departmentId,
            Map<ConditionAttribute, ?> attributes
    ) {

        Map<ConditionAttribute, Object> context =
                new EnumMap<>(ConditionAttribute.class);

        if (attributes != null) {
            context.putAll(attributes);
        }

        // Trusted identity information takes precedence
        // over caller-provided condition attributes.

        putIfPresent(context, ConditionAttribute.USER_ID, userId);

        context.put(
                ConditionAttribute.ROLE,
                roles == null ? List.of() : roles
        );

        putIfPresent(context, ConditionAttribute.DEPARTMENT, departmentId);

        return context;
    }

    private void putIfPresent(
            Map<ConditionAttribute, Object> context,
            ConditionAttribute attribute,
            Object value
    ) {

        if (value != null) {
            context.put(attribute, value);
        }
    }

    // --------------------------------------------------
    // ACTION / RESOURCE MATCHING
    // --------------------------------------------------

    private boolean matchesAction(Policy policy, String actionName) {

        return policy.getAction() != null
                && sameName(policy.getAction().getName(), actionName);
    }

    private boolean matchesResource(Policy policy, String resourceName) {

        return policy.getResource() != null
                && sameName(policy.getResource().getName(), resourceName);
    }

    /** Case-insensitive match. Null on either side never matches. */
    private boolean sameName(String first, String second) {

        return first != null
                && first.equalsIgnoreCase(second);
    }

    /** Exact, case-sensitive match. Null on either side never matches. */
    private boolean sameId(String first, String second) {

        return first != null
                && first.equals(second);
    }

    // --------------------------------------------------
    // SUBJECT MATCHING
    // --------------------------------------------------

    private boolean matchesSubject(
            Policy policy,
            String tenantId,
            String userId,
            List<String> roles,
            String departmentId,
            String serviceId
    ) {

        if (policy.getSubjects() == null
                || policy.getSubjects().isEmpty()) {
            return false;
        }

        return policy.getSubjects()
                .stream()
                .filter(Objects::nonNull)

                // Subject must belong to the current tenant
                .filter(subject ->
                        Objects.equals(subject.getTenantId(), tenantId))

                .anyMatch(subject -> matchesSubjectType(
                        subject,
                        userId,
                        roles,
                        departmentId,
                        serviceId
                ));
    }

    private boolean matchesSubjectType(
            PolicySubject subject,
            String userId,
            List<String> roles,
            String departmentId,
            String serviceId
    ) {

        String subjectId = subject.getSubjectId();

        if (subjectId == null) {
            return false;
        }

        SubjectType type = subject.getSubjectType();

        if (type == null) {
            return false;
        }

        return switch (type) {

            case USER -> sameId(subjectId, userId);

            case ROLE ->
                    roles != null
                            && roles.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(role -> sameName(role, subjectId));

            case DEPARTMENT -> sameId(subjectId, departmentId);

            case SERVICE -> sameId(subjectId, serviceId);
        };
    }

    // --------------------------------------------------
    // POLICY CONDITION EVALUATION
    // --------------------------------------------------

    private boolean evaluatePolicy(
            Policy policy,
            Map<ConditionAttribute, Object> context
    ) {

        // A policy without condition groups matches
        // when its action, resource and subject match.
        if (policy.getConditionGroups() == null
                || policy.getConditionGroups().isEmpty()) {
            return true;
        }

        // ALLOW policies fail closed by NOT matching.
        // DENY policies fail closed by matching.
        boolean failResult = policy.getEffect() == Effect.DENY;

        try {
            // All root groups must pass (AND).
            return policy.getConditionGroups()
                    .stream()
                    .allMatch(group ->
                            evaluateGroup(group, context, failResult, 0));

        } catch (IllegalStateException tooDeep) {
            // Over-deep or cyclic group tree: the whole policy fails closed.
            return failResult;
        }
    }

    // --------------------------------------------------
    // CONDITION GROUP EVALUATION
    // --------------------------------------------------

    private boolean evaluateGroup(
            PolicyConditionGroup group,
            Map<ConditionAttribute, Object> context,
            boolean failResult,
            int depth
    ) {

        if (depth > MAX_GROUP_DEPTH) {
            throw new IllegalStateException(
                    "Condition group nesting exceeds " + MAX_GROUP_DEPTH);
        }

        if (group == null || group.getLogicalOperator() == null) {
            return failResult;
        }

        List<Boolean> results = new ArrayList<>();

        if (group.getConditions() != null) {
            for (PolicyCondition condition : group.getConditions()) {
                results.add(evaluateCondition(condition, context, failResult));
            }
        }

        if (group.getChildGroups() != null) {
            for (PolicyConditionGroup child : group.getChildGroups()) {
                results.add(evaluateGroup(child, context, failResult, depth + 1));
            }
        }

        // Empty group: misconfiguration, so fail closed for this effect.
        if (results.isEmpty()) {
            return failResult;
        }

        return switch (group.getLogicalOperator()) {
            case AND -> results.stream().allMatch(Boolean::booleanValue);
            case OR  -> results.stream().anyMatch(Boolean::booleanValue);
        };
    }

    // --------------------------------------------------
    // CONDITION EVALUATION
    // --------------------------------------------------

    private boolean evaluateCondition(
            PolicyCondition condition,
            Map<ConditionAttribute, Object> context,
            boolean failResult
    ) {

        if (condition == null
                || condition.getAttribute() == null
                || condition.getOperator() == null) {
            return failResult;
        }

        Object actualValue = context.get(condition.getAttribute());

        // Missing attribute: EXISTS / NOT_EXISTS are well-defined,
        // every other operator is "unknown" and fails closed.
        if (actualValue == null
                && condition.getOperator() != ConditionOperator.EXISTS
                && condition.getOperator() != ConditionOperator.NOT_EXISTS) {
            return failResult;
        }

        String expectedValue = condition.getExpectedValue();

        try {
            return switch (condition.getOperator()) {
                case EXISTS       -> actualValue != null;
                case NOT_EXISTS   -> actualValue == null;
                case EQUALS       -> equalsValue(actualValue, expectedValue);
                case NOT_EQUALS   -> !equalsValue(actualValue, expectedValue);
                case IN           -> isIn(actualValue, expectedValue);
                case NOT_IN       -> !isIn(actualValue, expectedValue);
                case CONTAINS     -> containsValue(actualValue, expectedValue);
                case GREATER_THAN -> compare(actualValue, expectedValue) > 0;
                case LESS_THAN    -> compare(actualValue, expectedValue) < 0;
                case BETWEEN      -> isBetween(actualValue, expectedValue);
                case IN_CIDR      -> isInCidr(actualValue.toString(), expectedValue);
            };
        } catch (RuntimeException exception) {
            // Invalid expressions or values must never grant access.
            return failResult;
        }
    }

    // --------------------------------------------------
    // VALUE COMPARISON
    // --------------------------------------------------

    private boolean equalsValue(Object actual, String expected) {

        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        if (actual instanceof Collection<?> values) {
            return values.stream()
                    .anyMatch(value -> equalsValue(value, expected));
        }

        return actual.toString()
                .equalsIgnoreCase(expected.trim());
    }

    private boolean isIn(Object actual, String expected) {

        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        List<String> expectedValues = parseValues(expected);

        if (actual instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(value ->
                            expectedValues.stream()
                                    .anyMatch(item -> equalsValue(value, item)));
        }

        return expectedValues.stream()
                .anyMatch(item -> equalsValue(actual, item));
    }

    private boolean containsValue(Object actual, String expected) {

        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        return switch (actual) {
            case Collection<?> values -> values.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(value -> equalsValue(value, expected));
            case String text -> text.toLowerCase(Locale.ROOT)
                    .contains(expected.trim().toLowerCase(Locale.ROOT));
            case Map<?, ?> map -> map.containsKey(expected)
                    || map.containsValue(expected);
            default -> false;
        };
    }

    private List<String> parseValues(String value) {

        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    // --------------------------------------------------
    // NUMERIC AND TEMPORAL COMPARISON
    // --------------------------------------------------

    private int compare(Object actual, String expected) {

        if (actual == null || expected == null) {
            throw new IllegalArgumentException(
                    "Actual and expected values cannot be null");
        }

        String trimmedExpected = expected.trim();

        if (trimmedExpected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected value cannot be empty");
        }

        switch (actual) {
            case Number _ -> {
                return new BigDecimal(actual.toString())
                        .compareTo(new BigDecimal(trimmedExpected));
            }
            case Instant value -> {
                return value.compareTo(Instant.parse(trimmedExpected));
            }
            case LocalDateTime value -> {
                return value.compareTo(LocalDateTime.parse(trimmedExpected));
            }
            case LocalDate value -> {
                return value.compareTo(LocalDate.parse(trimmedExpected));
            }
            case LocalTime value -> {
                return value.compareTo(LocalTime.parse(trimmedExpected));
            }
            case DayOfWeek value -> {
                return value.compareTo(
                        DayOfWeek.valueOf(trimmedExpected.toUpperCase(Locale.ROOT)));
            }
            case Comparable<?> value -> {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) value;
                return comparable.compareTo(expected);
            }
            default -> {
            }
        }

        throw new IllegalArgumentException(
                "Unsupported comparison type: " + actual.getClass().getName());
    }

    private boolean isBetween(Object actual, String expected) {

        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        String[] bounds = expected.split(",", -1);

        if (bounds.length != 2
                || bounds[0].isBlank() || bounds[1].isBlank()) {
            throw new IllegalArgumentException("BETWEEN requires 'lower,upper'");
        }

        return compare(actual, bounds[0].trim()) >= 0
                && compare(actual, bounds[1].trim()) <= 0;
    }

    // --------------------------------------------------
    // IP CIDR EVALUATION
    // --------------------------------------------------

    private boolean isInCidr(String ipAddress, String cidr) {

        if (ipAddress == null || cidr == null) {
            throw new IllegalArgumentException("IP and CIDR cannot be null");
        }

        String[] parts = cidr.trim().split("/", -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR must be 'address/prefix'");
        }

        String networkAddress = parts[0].trim();

        // Numeric literals only, so no DNS lookups can happen.
        if (!isIpLiteral(ipAddress) || !isIpLiteral(networkAddress)) {
            throw new IllegalArgumentException("Not a valid IP literal");
        }

        byte[] addressBytes = toAddressBytes(ipAddress);
        byte[] networkBytes = toAddressBytes(networkAddress);

        // IPv4 vs IPv6: a legitimate "not in range" answer, not a misconfiguration.
        if (addressBytes.length != networkBytes.length) {
            return false;
        }

        // NumberFormatException is an IllegalArgumentException, so it propagates.
        int prefixLength = Integer.parseInt(parts[1].trim());
        int totalBits = addressBytes.length * 8;

        if (prefixLength < 0 || prefixLength > totalBits) {
            throw new IllegalArgumentException("CIDR prefix out of range");
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addressBytes[i] != networkBytes[i]) {
                return false;
            }
        }

        if (remainingBits > 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;

            if ((addressBytes[fullBytes] & mask)
                    != (networkBytes[fullBytes] & mask)) {
                return false;
            }
        }

        return true;
    }

    private byte[] toAddressBytes(String literal) {
        try {
            // IPv4-mapped IPv6 (::ffff:a.b.c.d) is returned as a 4-byte IPv4 address.
            return InetAddress.getByName(literal).getAddress();
        } catch (UnknownHostException e) {
            // e.g. ":::" passes the character check but is not a valid address
            throw new IllegalArgumentException("Invalid IP literal: " + literal, e);
        }
    }

    /** Returns true ONLY when the value is a numeric IPv4 or IPv6 literal. */
    private boolean isIpLiteral(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        // IPv6 literal
        if (value.contains(":")) {
            return value.matches("[0-9a-fA-F:.]+");
        }

        // IPv4 literal
        if (!value.matches("[0-9.]+")) {
            return false;
        }

        String[] octets = value.split("\\.", -1);

        if (octets.length != 4) {
            return false;
        }

        for (String octet : octets) {

            if (octet.isEmpty()) {
                return false;
            }

            try {
                int number = Integer.parseInt(octet);

                if (number < 0 || number > 255) {
                    return false;
                }

            } catch (NumberFormatException exception) {
                return false;
            }
        }

        return true;
    }

    // --------------------------------------------------
    // PRIORITY RESOLUTION
    // --------------------------------------------------

    /** A policy without a priority participates as LOW instead of being dropped. */
    private PolicyPriority effectivePriority(Policy policy) {

        return policy.getPriority() == null
                ? PolicyPriority.LOW
                : policy.getPriority();
    }

    private int priorityRank(PolicyPriority priority) {

        return switch (priority) {

            case LOW -> 1;

            case MODERATE -> 2;

            case MEDIUM -> 3;

            case HIGH -> 4;

            case CRITICAL -> 5;
        };
    }

    // --------------------------------------------------
    // DEFAULT DECISION
    // --------------------------------------------------

    private AuthorizationDecision denyByDefault() {

        return new AuthorizationDecision(
                false,
                Effect.DENY,
                null,
                List.of(),
                "Access denied. No matching authorization policy."
        );
    }
}