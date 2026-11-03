package com.manucore.policy.dto;

import com.manucore.policy.enums.ConditionAttribute;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/* Trusted identity from the JWT plus request attributes derived server-side */
public record CallerContext(
        String tenantId,
        String userId,
        List<String> roles,
        String departmentId,
        String serviceId,
        Map<ConditionAttribute, Object> attributes
) {

    public boolean hasRole(String role) {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    public Map<ConditionAttribute, Object> attributesWithMethod(String method) {
        Map<ConditionAttribute, Object> copy = new EnumMap<>(ConditionAttribute.class);
        copy.putAll(attributes);
        copy.put(ConditionAttribute.REQUEST_METHOD, method);
        return copy;
    }
}
