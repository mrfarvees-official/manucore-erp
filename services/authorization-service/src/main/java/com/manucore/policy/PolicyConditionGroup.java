package com.manucore.policy;

import com.manucore.policy.enums.LogicalOperator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policy_condition_groups")
@Getter
@Setter
public class PolicyConditionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Set on ROOT groups only. Child groups leave this null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    /** Set on CHILD groups only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group_id")
    private PolicyConditionGroup parentGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_operator", nullable = false)
    private LogicalOperator logicalOperator;

    @OneToMany(
            mappedBy = "parentGroup",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PolicyConditionGroup> childGroups = new ArrayList<>();

    @OneToMany(
            mappedBy = "conditionGroup",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PolicyCondition> conditions = new ArrayList<>();
}