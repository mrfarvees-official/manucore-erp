package com.manucore.policy;

import com.manucore.policy.enums.SubjectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "subjects")
@Getter
@Setter
public class PolicySubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;
}
