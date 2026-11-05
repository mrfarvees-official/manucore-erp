package com.manucore.identity.account.service;

import com.manucore.identity.account.Role;
import com.manucore.identity.account.repository.RoleRepository;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.account.dto.RoleDtos.CreateRoleRequest;
import com.manucore.identity.account.dto.RoleDtos.RoleResponse;
import com.manucore.identity.account.dto.RoleDtos.UpdateRoleRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RoleService {

    public static final String ADMIN = "ADMIN";

    private final RoleRepository roles;
    private final UserAccountRepository users;

    @Transactional(readOnly = true)
    public List<RoleResponse> list(String tenantId) {
        Map<String, Long> counts = countUsers(tenantId);

        return roles.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(r -> RoleResponse.from(r, counts.getOrDefault(r.getName(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(String tenantId, UUID id) {
        Role role = find(tenantId, id);

        return RoleResponse.from(role, countUsers(tenantId).getOrDefault(role.getName(), 0L));
    }

    @Transactional
    public RoleResponse create(String tenantId, CreateRoleRequest request) {
        String name = request.name().trim().toUpperCase(Locale.ROOT);

        if (roles.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + name);
        }

        Role role = roles.save(new Role(tenantId, name, clean(request.description()), false));

        return RoleResponse.from(role, 0);
    }

    /* The name is immutable because policies and users refer to it. */
    @Transactional
    public RoleResponse update(String tenantId, UUID id, UpdateRoleRequest request) {
        Role role = find(tenantId, id);
        role.setDescription(clean(request.description()));

        return RoleResponse.from(role, countUsers(tenantId).getOrDefault(role.getName(), 0L));
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        Role role = find(tenantId, id);

        if (role.isSystemRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The built-in " + role.getName() + " role cannot be deleted");
        }

        long assigned = countUsers(tenantId).getOrDefault(role.getName(), 0L);

        if (assigned > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role is still assigned to " + assigned + " user(s)");
        }

        roles.delete(role);
    }

    /** Creates the role if it is missing. Used by the startup backfill. */
    @Transactional
    public void ensure(String tenantId, String name, String description, boolean system) {
        if (!roles.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            roles.save(new Role(tenantId, name, description, system));
        }
    }

    public static List<String> parse(String csv) {
        if (csv == null) {
            return List.of();
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> r.toUpperCase(Locale.ROOT))
                .toList();
    }

    private Map<String, Long> countUsers(String tenantId) {
        Map<String, Long> counts = new HashMap<>();

        for (String csv : users.findRoleStrings(tenantId)) {
            for (String name : parse(csv)) {
                counts.merge(name, 1L, Long::sum);
            }
        }

        return counts;
    }

    private Role find(String tenantId, UUID id) {
        return roles.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}