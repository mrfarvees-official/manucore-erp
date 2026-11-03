package com.manucore.policy.service;

import com.manucore.policy.dto.PolicyDtos;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

/* Rejects policies the engine could only ever evaluate as "misconfigured" */
final class PolicyRequestValidator {

    static final int MAX_NODES = 200;

    private PolicyRequestValidator() {}

    static void validate(PolicyDtos.PolicyRequest request) {
        int[] counter = {0};

        List<PolicyDtos.ConditionGroupDto> roots = request.conditionGroups() == null
                ? List.of()
                : request.conditionGroups();

        for (PolicyDtos.ConditionGroupDto root : roots) {
            validateGroup(root, 0, counter);
        }
    }

    private static void validateGroup(PolicyDtos.ConditionGroupDto group, int depth, int[] counter) {
        if (group == null)
            throw bad("Condition groups must not contain null entries.");

        if (depth > PolicyEngine.MAX_GROUP_DEPTH)
            throw bad("Condition groups are nested deeper than "
                    + PolicyEngine.MAX_GROUP_DEPTH + " levels.");

        List<PolicyDtos.ConditionDto> conditions = group.conditions() == null
                ? List.of()
                : group.conditions();

        List<PolicyDtos.ConditionGroupDto> children = group.groups() == null
                ? List.of()
                : group.groups();

        if (conditions.isEmpty() && children.isEmpty())
            throw bad("A condition group must contain at least one condition or child group.");

        counter[0] += 1 + conditions.size();
        if (counter[0] > MAX_NODES)
            throw bad("A policy may contain at most " + MAX_NODES + " conditions and groups.");

        for (PolicyDtos.ConditionDto condition: conditions)
            validateCondition(condition);

        for (PolicyDtos.ConditionGroupDto child: children)
            validateGroup(child, depth + 1, counter);
    }

    private static void validateCondition(PolicyDtos.ConditionDto condition) {
        if (condition == null || condition.attribute() == null || condition.operator() == null)
            throw bad("Every condition needs an attribute and an operator.");

        String value = condition.value() == null ? "" : condition.value().trim();

        switch (condition.operator()) {
            case EXISTS, NOT_EXISTS -> {}
            case BETWEEN -> {
                String[] bounds = value.split(",", -1);
                if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank())
                    throw bad("BETWEEN requires a value like 'lower,upper'.");
            }
            case IN_CIDR -> {
                if (!value.matches("[0-9a-fA-F:.]+/[0-9]{1,3}"))
                    throw bad("IN_CIDR requires a value like '10.0.0.0/8'.");
            }
            case IN, NOT_IN -> {
                boolean empty = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .allMatch(String::isEmpty);
                if (empty) throw bad(condition.operator() + " requires at least one value.");
            }
            default -> {
                if (value.isEmpty())
                    throw bad("Operator " + condition.operator() + " requires a value.");
            }
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
