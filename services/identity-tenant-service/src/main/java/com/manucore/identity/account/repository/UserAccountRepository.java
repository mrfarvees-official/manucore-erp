package com.manucore.identity.account.repository;

import com.manucore.identity.account.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    List<UserAccount> findByTenantId(String tenantId);

    long countByTenantId(String tenantId);

    Optional<UserAccount> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from UserAccount u
            where u.tenantId = :tenantId
              and (lower(u.name) like :pattern escape '!'
                   or lower(u.email) like :pattern escape '!')
              and (:status = 'ALL'
                   or (:status = 'ACTIVE' and u.active = true)
                   or (:status = 'INACTIVE' and u.active = false))
            order by lower(u.name) asc
            """)
    Page<UserAccount> search(@Param("tenantId") String tenantId,
                             @Param("pattern") String pattern,
                             @Param("status") String status,
                             Pageable pageable);

    @Query("""
            select count(u) from UserAccount u
            where u.tenantId = :tenantId and u.active = true
              and upper(concat(',', replace(u.roles, ' ', ''), ',')) like '%,ADMIN,%'
            """)
    long countActiveAdmins(@Param("tenantId") String tenantId);

    @Query("select u.roles from UserAccount u where u.tenantId = :tenantId")
    List<String> findRoleStrings(@Param("tenantId") String tenantId);
}