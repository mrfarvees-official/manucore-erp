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
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group_id")
    private PolicyCondition parentGroup;

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
