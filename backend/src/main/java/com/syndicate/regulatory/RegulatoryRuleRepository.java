package com.syndicate.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegulatoryRuleRepository extends JpaRepository<RegulatoryRule, UUID> {
    List<RegulatoryRule> findByActiveTrueOrderByCodeAsc();
}
