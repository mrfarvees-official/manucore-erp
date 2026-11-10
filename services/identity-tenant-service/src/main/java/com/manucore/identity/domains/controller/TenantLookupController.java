package com.manucore.identity.domains.controller;

import com.manucore.identity.domains.dto.DomainDtos.TenantLookupResponse;
import com.manucore.identity.domains.service.DomainService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class TenantLookupController {

    private final DomainService domains;

    @GetMapping("/tenant")
    public TenantLookupResponse lookup(@RequestParam String host) {
        return domains.resolve(host)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown domain"));
    }
}