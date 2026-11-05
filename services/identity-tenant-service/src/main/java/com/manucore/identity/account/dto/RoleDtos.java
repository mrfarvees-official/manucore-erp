package com.manucore.identity.account.dto;

import com.manucore.identity.account.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class RoleDtos {

    private static final String ROLE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{1,49}$";

    private RoleDtos() {
    }

    public record RoleResponse(
            UUID id,
            String name,
            String description,
            boolean system,
            long userCount) {

        public static RoleResponse from(Role role, long userCount) {
            return new RoleResponse(
                    role.getId(),
                    role.getName(),
                    role.getDescription(),
                    role.isSystemRole(),
                    userCount);
        }
    }

    /** What the user form needs to render a checkbox. */
    public record RoleOption(String name, String description) {
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = ROLE_PATTERN) String name,
            @Size(max = 255) String description) {
    }

    public record UpdateRoleRequest(@Size(max = 255) String description) {
    }
}