package com.manucore.identity.account.service;

import com.manucore.identity.account.UserAccount;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.account.dto.UserDtos;
import com.manucore.identity.account.dto.UserDtos.CreateUserRequest;
import com.manucore.identity.account.dto.UserDtos.UpdateUserRequest;
import com.manucore.identity.audit.ChangeSet;
import com.manucore.identity.audit.dto.Actor;
import com.manucore.identity.audit.service.AuditService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.manucore.identity.account.dto.RoleDtos.RoleOption;
import com.manucore.identity.account.repository.RoleRepository;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserAdminService {

    private static final String ADMIN = "ADMIN";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "ALL");

    private final PasswordEncoder passwordEncoder;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Page<UserAccount> search(String tenantId, String query, String status, int page, int size) {
        String statusFilter = status.trim().toUpperCase(Locale.ROOT);

        if (!STATUSES.contains(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status filter");
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));

        String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";

        return users.search(tenantId, pattern, statusFilter, pageable);
    }

    @Transactional(readOnly = true)
    public UserAccount get(String tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<RoleOption> roleOptions(String tenantId) {
        return roles.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(r -> new RoleOption(r.getName(), r.getDescription()))
                .toList();
    }

    private void requireKnownRoles(String tenantId, Set<String> requested) {
        Set<String> known = roles.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(r -> r.getName().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (String role : requested) {
            if (!known.contains(role)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
            }
        }
    }

    @Transactional
    public UserAccount create(String tenantId, Actor actor, CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use: " + email);
        }

        checkPasswordLength(request.password());

        Set<String> roles = normalizeRoles(request.roles());
        requireKnownRoles(tenantId, roles);

        boolean active = request.active() == null || request.active();

        UserAccount saved = users.save(new UserAccount(
                UUID.randomUUID(),
                tenantId,
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                String.join(",", roles),
                active));

        audit.record(tenantId, actor, "USER_CREATED", "USER", saved.getId().toString(), saved.getEmail(),
                new ChangeSet()
                        .add("name", null, saved.getName())
                        .add("roles", null, saved.getRoles())
                        .add("active", null, saved.isActive())
                        .asList());

        return saved;
    }

    @Transactional
    public UserAccount update(String tenantId, Actor actor, UUID id, UpdateUserRequest request) {
        UUID callerId = UUID.fromString(actor.id());
        UserAccount user = find(tenantId, id);
        Set<String> roles = normalizeRoles(request.roles());
        requireKnownRoles(tenantId, roles);

        boolean isAdminNow = user.isActive() && hasRole(user, ADMIN);
        boolean isAdminAfter = request.active() && roles.contains(ADMIN);

        if (id.equals(callerId)) {
            boolean losesAdmin = hasRole(user, ADMIN) && !roles.contains(ADMIN);

            if (!request.active() || losesAdmin) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You cannot deactivate yourself or remove your own ADMIN role");
            }
        }

        if (isAdminNow && !isAdminAfter && users.countActiveAdmins(tenantId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "At least one active administrator is required");
        }

        String name = request.name().trim();
        String newRoles = String.join(",", roles);
        boolean passwordReset = request.password() != null && !request.password().isBlank();

        ChangeSet changes = new ChangeSet()
                .add("name", user.getName(), name)
                .add("roles", user.getRoles(), newRoles)
                .add("active", user.isActive(), request.active());

        if (passwordReset) {
            checkPasswordLength(request.password());
            changes.note("password", "reset by an administrator");
        }

        user.setName(name);
        user.setRoles(newRoles);
        user.setActive(request.active());

        if (passwordReset) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (!changes.isEmpty()) {
            audit.record(tenantId, actor, "USER_UPDATED", "USER", user.getId().toString(),
                    user.getEmail(), changes.asList());
        }

        return user;
    }

    /* Another tenant's user looks exactly like a missing user (404). */
    private UserAccount find(String tenantId, UUID id) {
        return users.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static boolean hasRole(UserAccount user, String role) {
        return UserDtos.splitRoles(user.getRoles()).stream()
                .anyMatch(r -> r.equalsIgnoreCase(role));
    }

    private static Set<String> normalizeRoles(List<String> roles) {
        Set<String> normalized = new TreeSet<>();

        for (String role : roles) {
            normalized.add(role.trim().toUpperCase(Locale.ROOT));
        }

        return normalized;
    }

    /* BCrypt only uses the first 72 bytes and Spring rejects longer input. */
    private static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}