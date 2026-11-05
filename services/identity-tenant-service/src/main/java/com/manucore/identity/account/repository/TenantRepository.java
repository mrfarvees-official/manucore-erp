package com.manucore.identity.account.repository;

import com.manucore.identity.account.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

}
