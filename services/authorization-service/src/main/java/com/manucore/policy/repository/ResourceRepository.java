package com.manucore.policy.repository;

import com.manucore.policy.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    Optional<Resource> findByNameIgnoreCase(String name);
}
