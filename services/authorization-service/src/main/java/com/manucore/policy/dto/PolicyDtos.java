package com.manucore.policy.dto;

import com.manucore.policy.Policy;
import com.manucore.policy.PolicyConditionGroup;
import com.manucore.policy.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record SubjectDto(
            @NotNull SubjectType type,
            @NotBlank @Size(max = 150) String id) {
    }

    public record ConditionDto(
            @NotNull ConditionAttribute attribute,
            @NotNull ConditionOperator operator,
            @Size(max = 2000) String value) {
    }

    public record ConditionGroupDto(
            @NotNull LogicalOperator operator,
            @Valid List<ConditionDto> conditions,
            @Valid List<ConditionGroupDto> groups) {
    }

    public record PolicyRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 100) String action,
            @NotBlank @Size(max = 150) String resource,
            @NotNull Effect effect,
            PolicyPriority priority,
            @NotEmpty @Size(max = 50) @Valid List<SubjectDto> subjects,
            @Valid List<ConditionGroupDto> conditionGroups) {
    }

    public record PolicyResponse(
            UUID id,
            String name,
            String description,
            String action,
            String resource,
            Effect effect,
            PolicyPriority priority,
            List<SubjectDto> subjects,
            List<ConditionGroupDto> conditionGroups,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy) {

        public static PolicyResponse from(Policy p) {
            return new PolicyResponse(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getAction().getName(),
                    p.getResource().getName(),
                    p.getEffect(),
                    p.getPriority(),
                    p.getSubjects().stream()
                            .map(s -> new SubjectDto(s.getSubjectType(), s.getSubjectId()))
                            .sorted(Comparator
                                    .comparing((SubjectDto s) -> s.type().name())
                                    .thenComparing(SubjectDto::id))
                            .toList(),
                    p.getConditionGroups().stream()
                            .map(PolicyResponse::group)
                            .toList(),
                    p.getCreatedAt(),
                    p.getUpdatedAt(),
                    p.getUpdatedBy());
        }

        private static ConditionGroupDto group(PolicyConditionGroup g) {
            return new ConditionGroupDto(
                    g.getLogicalOperator(),
                    g.getConditions().stream()
                            .map(c -> new ConditionDto(
                                    c.getAttribute(), c.getOperator(), c.getExpectedValue()))
                            .toList(),
                    g.getChildGroups().stream()
                            .map(PolicyResponse::group)
                            .toList());
        }
    }

    public record PolicyPage(
            List<PolicyResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    /** Names a policy editor UI needs to build its forms. */
    public record CatalogResponse(
            List<String> actions,
            List<String> resources,
            List<String> effects,
            List<String> priorities,
            List<String> subjectTypes,
            List<String> conditionAttributes,
            List<String> conditionOperators,
            List<String> logicalOperators) {
    }
}