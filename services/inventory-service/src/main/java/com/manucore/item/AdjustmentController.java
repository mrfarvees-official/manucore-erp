package com.manucore.item;

import com.manucore.item.dto.AdjustmentPage;
import com.manucore.item.service.AdjustmentService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/adjustments")
@AllArgsConstructor
public class AdjustmentController {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final AdjustmentService service;

    @GetMapping
    public AdjustmentPage list(@RequestHeader(TENANT_HEADER) String tenantId,
                               @RequestParam(required = false) UUID itemId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return AdjustmentPage.from(service.list(tenantId, itemId, page, size));
    }
}