package com.manucore.policy.dto;

import com.manucore.policy.enums.Effect;
import com.manucore.policy.enums.PolicyPriority;

import java.util.List;
import java.util.UUID;

public record AuthorizationDecision(
        boolean allowed,
        Effect effect,
        PolicyPriority priority,
        List<UUID> policyIds,
        String reason
) {
}
