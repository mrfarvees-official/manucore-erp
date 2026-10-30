package com.manucore.policy;

import com.manucore.policy.enums.ConditionAttribute;
import com.manucore.policy.enums.ConditionOperator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "policy_condition")
@Getter
@Setter
public class PolicyCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_group_id", nullable = false)
    private PolicyConditionGroup conditionGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionAttribute attribute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionOperator operator;

    @Column(name = "expected_value", nullable = false, length = 2000)
    private String expectedValue;
}
