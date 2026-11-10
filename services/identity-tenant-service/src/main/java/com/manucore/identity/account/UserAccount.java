package com.manucore.identity.account;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"email"}))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /* Comma separated, e.g. "ADMIN,INVENTORY_MANAGER" */
    @Column(nullable = false)
    private String roles;

    @Column(nullable = false)
    private boolean active = true;

    /* Null means "use the company default". */
    @Column(length = 64)
    private String timezone;

    @Column(length = 35)
    private String locale;

    public UserAccount(UUID id, String tenantId, String name, String email,
                       String passwordHash, String roles, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.active = active;
    }
}
