package com.manucore.identity.account.dto;

import com.manucore.identity.account.UserAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private static final String ROLE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{1,49}$";

    private UserDtos() {
    }

    /** The roles column is a comma separated string, e.g. "ADMIN,INVENTORY_MANAGER". */
    public static List<String> splitRoles(String csv) {
        if (csv == null) {
            return List.of();
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();
    }

    public record UserResponse(
            UUID id,
            String email,
            String name,
            List<String> roles,
            boolean active) {

        public static UserResponse from(UserAccount user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    splitRoles(user.getRoles()),
                    user.isActive());
        }
    }

    public record UserPage(
            List<UserResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        public static UserPage from(Page<UserAccount> page) {
            return new UserPage(
                    page.getContent().stream().map(UserResponse::from).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotEmpty @Size(max = 20) List<@Pattern(regexp = ROLE_PATTERN) String> roles,
            Boolean active) {
    }

    /** Full update. A blank password keeps the current one. The email cannot be changed. */
    public record UpdateUserRequest(
            @NotBlank @Size(max = 200) String name,
            @NotEmpty @Size(max = 20) List<@Pattern(regexp = ROLE_PATTERN) String> roles,
            @NotNull Boolean active,
            @Size(min = 8, max = 72) String password) {
    }
}