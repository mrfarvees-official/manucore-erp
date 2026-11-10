package com.manucore.identity.domains.controller;

import com.manucore.identity.audit.ActorResolver;
import com.manucore.identity.audit.Claims;
import com.manucore.identity.domains.dto.DomainDtos.AddDomainRequest;
import com.manucore.identity.domains.dto.DomainDtos.DomainResponse;
import com.manucore.identity.domains.dto.DomainDtos.UpdateDomainRequest;
import com.manucore.identity.domains.service.DomainService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity/domains")
@AllArgsConstructor
public class DomainController {

    private final DomainService service;
    private final ActorResolver actors;

    @GetMapping
    public List<DomainResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Claims.tenantOf(jwt)).stream().map(DomainResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse add(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody AddDomainRequest request) {
        return DomainResponse.from(service.add(Claims.tenantOf(jwt), actors.from(jwt), request));
    }

    @PostMapping("/{id}/verify")
    public DomainResponse verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return DomainResponse.from(service.verify(Claims.tenantOf(jwt), actors.from(jwt), id));
    }

    /* Only "make primary" is supported. */
    @PutMapping("/{id}")
    public DomainResponse update(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody UpdateDomainRequest request) {
        if (!request.primary()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another domain as primary instead");
        }

        return DomainResponse.from(service.makePrimary(Claims.tenantOf(jwt), actors.from(jwt), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.remove(Claims.tenantOf(jwt), actors.from(jwt), id);
    }
}