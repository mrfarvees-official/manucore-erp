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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyEngine {

    private final PolicyRepository policyRepository;

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
        // Build the evaluation context
        Map<ConditionAttribute, Object> context = buildContext(userId, roles, departmentId, attributes);

        // Load policies for this tenant
        List<Policy> matchingPolicies = policyRepository.findByTenantId(tenantId)
                .stream()
                .filter(policy -> matchesAction(policy, actionName))
                .filter(policy -> matchesResource(policy, resourceName))
                .filter(policy -> matchesSubject(
                        policy,
                        tenantId,
                        userId,
                        roles,
                        departmentId,
                        serviceId))
                .filter(policy -> evaluatePolicy(policy, context))
                .toList();

        // Default deny
        if (matchingPolicies.isEmpty()) return denyByDefault();

        // Find the highest priority
        PolicyPriority highestPriority = matchingPolicies
                .stream()
                .map(Policy::getPriority)
                .filter(Objects::nonNull)
                .max((a, b) -> Integer.compare(
                        priorityRank(a),
                        priorityRank(b)
                ))
                .orElse(null);

        if (highestPriority == null) return denyByDefault();

        // Keep only policies at the highest priority
        List<Policy> highestPriorityPolicies = matchingPolicies
                .stream()
                .filter(policy -> policy.getPriority() == highestPriority)
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
                    "Access denied by a matching policy");
        }

        // Otherwise, allow
        List<Policy> allowPolicies = highestPriorityPolicies
                .stream()
                .filter(policy -> policy.getEffect() == Effect.ALLOW)
                .toList();

        if (allowPolicies.isEmpty()) return denyByDefault();

        return new AuthorizationDecision(
                true,
                Effect.ALLOW,
                highestPriority,
                allowPolicies.stream()
                        .map(Policy::getId)
                        .toList(),
                "Access granted by a matching policy.");
    }

    // Context
    private Map<ConditionAttribute, Object> buildContext(
            String userId,
            List<String> roles,
            String departmentId,
            Map<ConditionAttribute, ?> attributes
    ) {
        Map<ConditionAttribute, Object> context = new EnumMap<>(ConditionAttribute.class);

        if (attributes != null) {
            context.putAll(attributes);
        }

        // Trusted identity information takes precedence
        // over caller-provided condition attributes (CURRENT USER).
        putIfPresent(context, ConditionAttribute.USER_ID, userId);

        context.put(ConditionAttribute.ROLE, roles == null ? List.of() : roles);

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

    // Action
    private boolean matchesAction(
            Policy policy,
            String actionName
    ) {
        return policy.getAction() != null
                && sameName(
                        policy.getAction().getName(),
                        actionName);
    }

    // Resource
    private boolean matchesResource(
            Policy policy,
            String resourceName
    ) {
        return policy.getResource() != null
                && sameName(
                        policy.getResource().getName(),
                        resourceName);
    }

    private boolean sameName(String first, String second) {
        return first != null && first.equalsIgnoreCase(second);
    }

    // Subject
    private boolean matchesSubject(
            Policy policy,
            String tenantId,
            String userId,
            List<String> roles,
            String departmentId,
            String serviceId
    ) {
        if (policy.getSubjects() == null ||
            policy.getSubjects().isEmpty()) return false;

        return policy.getSubjects()
                .stream()
                .filter(subject ->
                        sameName(subject.getSubjectId(), tenantId)
                )
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

        if (subjectId == null) return false;

        SubjectType type = subject.getSubjectType();

        if (type == null) return false;

        return switch (type) {
            case USER ->
                    sameName(subjectId, userId);
            case ROLE ->
                    roles != null
                            && roles.stream()
                            .anyMatch(role -> sameName(role, subjectId));
            case DEPARTMENT ->
                    sameName(subjectId, departmentId);
            case SERVICE ->
                    sameName(subjectId, serviceId);
        };
    }

    // Policy condition evaluation
    private boolean evaluatePolicy(
            Policy policy,
            Map<ConditionAttribute, Object> context
    ) {
        if (policy.getConditionGroups() == null || policy.getConditionGroups().isEmpty()) return false;

        // All root groups must be pass
        // TODO: fill back
        return false;
    }

    private boolean evaluateGroup(
            PolicyConditionGroup group,
            Map<ConditionAttribute, Object> context
    ) {
        List<Boolean> results = new ArrayList<>();

        // Evaluate conditions belongs to this group
        if (group.getConditions() != null) {
            for (PolicyCondition condition : group.getConditions()) {
                // TODO: fill back
            }
        }

        return false;
    }

    private boolean evaluateCondition(
            PolicyCondition condition,
            Map<ConditionAttribute, Object> context
    ) {
        ConditionAttribute attribute = condition.getAttribute();
        ConditionOperator operator = condition.getOperator();

        if (attribute == null || operator == null) return false;

        Object actualValue = context.get(attribute);
        String expectedValue = condition.getExpectedValue();

        return switch (operator) {
            case EXISTS ->
                    actualValue != null;
            case NOT_EXISTS ->
                    actualValue == null;
            case EQUALS ->
                    actualValue != null
                            && equalsValue(actualValue, expectedValue);
            case NOT_EQUALS ->
                    actualValue != null
                            && !equalsValue(actualValue, expectedValue);
            case IN ->
                    actualValue != null
                            && isIn(actualValue, expectedValue);
            case NOT_IN ->
                    actualValue != null
                            && !isIn(actualValue, expectedValue);
            case CONTAINS ->
                    actualValue != null
                            && containsValue(actualValue, expectedValue);
            case GREATER_THAN ->
                    actualValue != null
                            && compare(actualValue, expectedValue) > 0;
            case LESS_THAN ->
                    actualValue != null
                            && compare(actualValue, expectedValue) < 0;
            case BETWEEN ->
                    actualValue != null
                            && isBetween(actualValue, expectedValue);
            case IN_CIDR ->
                    actualValue != null
                            && isInCidr(actualValue.toString(), expectedValue);
        };
    }
    //-----------------
    // VALUE COMPARISON
    //-----------------
    private boolean equalsValue(
            Object actual,
            String expected
    ) {
        if (expected == null) return false;

        if (actual instanceof Collection<?> values) {
            return values.stream()
                    .anyMatch(value ->
                            equalsValue(value, expected));
        }

        return actual.toString().equalsIgnoreCase(expected.trim());
    }

    private boolean isIn(
            Object actual,
            String expected
    ) {
        if (expected == null) return false;

        List<String> expectedValues = parseValues(expected);

        if (actual instanceof Collection<?> values) {
            return values.stream()
                    .anyMatch(value ->
                            value != null
                                && expectedValues.stream()
                                    .anyMatch(item ->
                                            equalsValue(value, item)));
        }

        return expectedValues.stream()
                .anyMatch(item ->
                        equalsValue(actual, item));
    }

    private boolean containsValue(
            Object actual,
            String expected
    ) {
        if (expected == null) return false;

        if (actual instanceof Collection<?> values) {
            return values.stream()
                    .anyMatch(value ->
                            equalsValue(value, expected));
        }

        if (actual instanceof String text) {
            return text.toLowerCase()
                    .contains(expected.trim().toLowerCase());
        }

        if (actual instanceof Map<?, ?> map) {
            return map.containsKey(expected) ||
                    map.containsValue(expected);
        }

        return false;
    }

    private List<String> parseValues(String value) {
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    //--------------------------------
    // NUMERIC AND TEMPORAL COMPARISON
    //--------------------------------
    private int compare(
            Object actual,
            String expected
    ) {
        if (expected == null) throw new IllegalArgumentException("Expected value cannot be null");

        if (actual instanceof Number) {
            return new BigDecimal(actual.toString())
                    .compareTo(new BigDecimal(expected.trim()));
        }

        if (actual instanceof Instant value) {
            return value.compareTo(Instant.parse(expected.trim()));
        }

        if (actual instanceof LocalDateTime value) {
            return value.compareTo(LocalDateTime.parse(expected.trim()));
        }

        if (actual instanceof LocalDate value) {
            return  value.compareTo(LocalDate.parse(expected.trim()));
        }

        if (actual instanceof LocalTime value) {
            return  value.compareTo(LocalTime.parse(expected.trim()));
        }

        if (actual instanceof Comparable<?> value) {
            @SuppressWarnings("unchecked")
            Comparable<Object> comparable = (Comparable<Object>) value;
            return comparable.compareTo(expected);
        }

        throw new IllegalArgumentException("Unsupported comparison type: " + actual.getClass().getName());
    }

    private boolean isBetween(
            Object actual,
            String expected
    ) {
        if (expected == null) return false;

        String[] bounds = expected.split(",", -1);

        if (bounds.length != 2) return false;

        String lower = bounds[0].trim();
        String upper = bounds[0].trim();

        if (lower.isEmpty() || upper.isEmpty()) return false;

        return compare(actual, lower) >= 0 && compare(actual, upper) <= 0;
    }
    
    //-------------------
    // IP CIDR EVALUATION
    //-------------------
    private boolean isInCidr(
            String ipAddress,
            String cidr
    ) {
        if (ipAddress == null || cidr == null) return false;
        
        try {
            String[] parts = cidr.trim().split("/", -1);
            
            if (parts.length != 2) return false;
            
            String networkAddress = parts[0].trim();
            
            // Accept only numeric IPv4 or IPv6 literals
            if (!isIpLiteral(ipAddress) || !isIpLiteral(networkAddress)) return false;

            byte[] addressBytes = InetAddress.getByName(ipAddress).getAddress();

            byte[] networkBytes = InetAddress.getByName(networkAddress).getAddress();

            if (addressBytes.length != networkBytes.length) return false;

            int prefixLength = Integer.parseInt(parts[1].trim());
            int totalBits = addressBytes.length * 8;

            if (prefixLength < 0 || prefixLength > totalBits) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addressBytes[i] != networkBytes[i]) return false;
            }

            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;

                if ((addressBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isIpLiteral(String value) {
        if (value == null || value.isBlank()) return false;
        
        // IPv6 literals contain a colon
        if(value.contains(":")) {
            return value.matches("[0-9a-fA-F:.]+");
        }
        
        // IPv4 literals must contain exactly for octets
        if (!value.matches("[0-9.]+")) return false;
        
        String[] octets = value.split("\\.", -1);
        
        if (octets.length != 4) return false;
        
        for (String octet : octets) {
            if (octet.isEmpty()) return false;
            
            try {
                int number = Integer.parseInt(octet);

                if (number < 0 || number > 255) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        return false;
    }

    // -------------------
    // PRIORITY RESOLUTION
    // -------------------
    private int priorityRank(PolicyPriority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MODERATE -> 2;
            case MEDIUM -> 3;
            case HIGH -> 4;
            case CRITICAL -> 5;
        };
    }

    // ----------------
    // DEFAULT DECISION
    // ----------------
    private AuthorizationDecision denyByDefault() {
        return new AuthorizationDecision(
                false,
                Effect.DENY,
                null,
                List.<UUID>of(),
                "Access denied. No matching authorization policy.");
    }
}
