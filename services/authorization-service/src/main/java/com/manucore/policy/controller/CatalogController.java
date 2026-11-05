package com.manucore.policy.controller;

import com.manucore.policy.Action;
import com.manucore.policy.Resource;
import com.manucore.policy.dto.PolicyDtos;
import com.manucore.policy.enums.*;
import com.manucore.policy.repository.ActionRepository;
import com.manucore.policy.repository.ResourceRepository;
import com.manucore.policy.service.CallerContextFactory;
import com.manucore.policy.service.PolicyAdminGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/authorization/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final ActionRepository actions;
    private final ResourceRepository resources;
    private final PolicyAdminGuard guard;
    private final CallerContextFactory callers;

    @GetMapping
    public PolicyDtos.CatalogResponse catalog(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        guard.require(callers.from(jwt, http), PolicyAdminGuard.CATALOG_RESOURCE, "READ");

        return new PolicyDtos.CatalogResponse(
                actions.findAll().stream().map(Action::getName).sorted().toList(),
                resources.findAll().stream().map(Resource::getName).sorted().toList(),
                names(Effect.values()),
                names(PolicyPriority.values()),
                names(SubjectType.values()),
                names(ConditionAttribute.values()),
                names(ConditionOperator.values()),
                names(LogicalOperator.values()));
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
