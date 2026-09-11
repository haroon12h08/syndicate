package com.syndicate.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findByOwnerOrganizationIdIn(List<UUID> organizationIds);

    boolean existsByCin(String cin);

    @Query("""
            select distinct c from Company c
            join Transaction t on t.company = c
            join TransactionMembership tm on tm.transaction = t
            where tm.user.id = :userId
            """)
    List<Company> findVisibleViaTransactionMembership(@Param("userId") UUID userId);

    @Query("""
            select case when count(t) > 0 then true else false end
            from Transaction t
            join TransactionMembership tm on tm.transaction = t
            where t.company.id = :companyId and tm.user.id = :userId
            """)
    boolean isVisibleViaTransactionMembership(@Param("companyId") UUID companyId, @Param("userId") UUID userId);
}
