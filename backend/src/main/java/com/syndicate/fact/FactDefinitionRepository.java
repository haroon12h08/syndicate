package com.syndicate.fact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FactDefinitionRepository extends JpaRepository<FactDefinition, String> {

    List<FactDefinition> findAllByOrderByFactKeyAsc();

    @Query(value = "SELECT fact_key FROM fact_definition_aliases WHERE alias = :alias", nativeQuery = true)
    Optional<String> findKeyByAlias(@Param("alias") String alias);
}
