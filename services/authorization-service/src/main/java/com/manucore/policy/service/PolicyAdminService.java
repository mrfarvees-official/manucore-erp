package com.manucore.policy.service;

import com.manucore.policy.Policy;
import com.manucore.policy.PolicyCondition;
import com.manucore.policy.PolicyConditionGroup;
import com.manucore.policy.PolicySubject;
import com.manucore.policy.dto.PolicyDtos;
import com.manucore.policy.enums.PolicyPriority;
import com.manucore.policy.enums.SubjectType;
import com.manucore.policy.repository.ActionRepository;
import com.manucore.policy.repository.PolicyRepository;
import com.manucore.policy.repository.PolicySubjectRepository;
import com.manucore.policy.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyAdminService {

    private final PolicyRepository policies;
    private final ActionRepository actions;
    private final ResourceRepository resources;
    private final PolicySubjectRepository subjects;

    @Transactional(readOnly = true)
    public PolicyDtos.PolicyPage list(String tenantId, int page, int size) {
        Page<Policy> result = policies.findByTenantId(
                tenantId, PageRequest.of(page, size, Sort.by("name").ascending()));

        return new PolicyDtos.PolicyPage(
                result.getContent().stream().map(PolicyDtos.PolicyResponse::from).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PolicyDtos.PolicyResponse get(String tenantId, UUID id) {
        return PolicyDtos.PolicyResponse.from(find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public boolean exists(String tenantId, String name) {
        return policies.existsByTenantIdAndNameIgnoreCase(tenantId, name.trim());
    }

    @Transactional
    public PolicyDtos.PolicyResponse create(String tenantId, String actor, PolicyDtos.PolicyRequest request) {
        PolicyRequestValidator.validate(request);

        String name = request.name().trim();
        if (policies.existsByTenantIdAndNameIgnoreCase(tenantId, name))
            throw nameTaken(name);

        Policy policy = new Policy();
        policy.setTenantId(tenantId);
        apply(policy, tenantId, actor, request);

        return PolicyDtos.PolicyResponse.from(policies.save(policy));
    }

    @Transactional
    public PolicyDtos.PolicyResponse update(String tenantId, UUID id, String actor, PolicyDtos.PolicyRequest request) {
        PolicyRequestValidator.validate(request);

        Policy policy = find(tenantId, id);

        String name = request.name().trim();
        if (policies.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id))
            throw nameTaken(name);

        apply(policy, tenantId, actor, request);

        return PolicyDtos.PolicyResponse.from(policy);
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        policies.delete(find(tenantId, id));
    }

    /* Full replace of everything a client can set. */
    private void apply(Policy policy, String tenantId, String actor, PolicyDtos.PolicyRequest request) {
        policy.setName(request.name().trim());
        policy.setDescription(request.description().trim());
        policy.setAction(actions.findByNameIgnoreCase(request.action().trim())
                .orElseThrow(() -> unknown("action", request.action())));
        policy.setResource(resources.findByNameIgnoreCase(request.resource().trim())
                .orElseThrow(() -> unknown("resource", request.resource())));
        policy.setEffect(request.effect());
        policy.setPriority(request.priority() == null
                ? PolicyPriority.MEDIUM
                : request.priority());

        Set<PolicySubject> resolved = new HashSet<>();
        for (PolicyDtos.SubjectDto subject : request.subjects())
            resolved.add(subject(tenantId, subject));

        policy.getSubjects().clear();
        policy.getSubjects().addAll(resolved);

        // orphanRemoval deletes the old tree
        policy.getConditionGroups().clear();
        if (request.conditionGroups() != null) {
            for (PolicyDtos.ConditionGroupDto dto : request.conditionGroups()) {
                PolicyConditionGroup root = buildGroup(dto);
                root.setPolicy(policy);
                policy.getConditionGroups().add(root);
            }
        }
    }

    private PolicyConditionGroup buildGroup(PolicyDtos.ConditionGroupDto dto) {
        PolicyConditionGroup group = new PolicyConditionGroup();
        group.setLogicalOperator(dto.operator());

        if (dto.conditions() != null) {
            for (PolicyDtos.ConditionDto c : dto.conditions()) {
                PolicyCondition condition = new PolicyCondition();
                condition.setConditionGroup(group);
                condition.setAttribute(c.attribute());
                condition.setOperator(c.operator());
                condition.setExpectedValue(c.value() == null ? "" : c.value().trim());
                group.getConditions().add(condition);
            }
        }

        if (dto.groups() != null) {
            for (PolicyDtos.ConditionGroupDto childDto : dto.groups()) {
                PolicyConditionGroup child = buildGroup(childDto);
                child.setParentGroup(group);
                group.getChildGroups().add(child);
            }
        }

        return group;
    }

    private PolicySubject subject(String tenantId, PolicyDtos.SubjectDto dto) {
        // Roles are matched case-insensitively by the engine, so store them canonically.
        String id = dto.type() == SubjectType.ROLE
                ? dto.id().trim().toUpperCase(Locale.ROOT)
                : dto.id().trim();

        return subjects.findByTenantIdAndSubjectTypeAndSubjectId(tenantId, dto.type(), id)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    PolicySubject created = new PolicySubject();
                    created.setTenantId(tenantId);
                    created.setSubjectType(dto.type());
                    created.setSubjectId(id);
                    return subjects.save(created);
                });
    }

    private Policy find(String tenantId, UUID id) {
        // Another tenant's policy looks exactly like a missing one.
        return policies.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Policy not found"));
    }

    private static ResponseStatusException nameTaken(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A policy named '" + name + "' already exists.");
    }

    private static ResponseStatusException unknown(String kind, String name) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown " + kind + " '" + name + "'. See GET /api/v1/authorization/catalog.");
    }
}
