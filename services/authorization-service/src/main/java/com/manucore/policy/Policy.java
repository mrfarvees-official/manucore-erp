package com.manucore.policy;

import com.manucore.policy.enums.Effect;
import com.manucore.policy.enums.PolicyPriority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_policies_tenant_name",
                columnNames = { "tenant_id", "name" }
        ),
        indexes = @Index(
                name = "idx_policies_tenant_action_resource",
                columnList = "tenant_id,action_id,resource_id"
        )
)
@Getter
@Setter
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Effect effect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyPriority priority = PolicyPriority.MEDIUM;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "policy_subjects_mapping",
            joinColumns = @JoinColumn(name = "policy_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    private Set<PolicySubject> subjects = new HashSet<>();

    /** Root groups only. Child groups hang off their parent group. */
    @OneToMany(
            mappedBy = "policy",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PolicyConditionGroup> conditionGroups = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
}