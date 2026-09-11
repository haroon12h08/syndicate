package com.syndicate.fact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FactRepository extends JpaRepository<Fact, UUID> {
    List<Fact> findByWorkstreamId(UUID workstreamId);

    List<Fact> findByWorkstreamIdAndStatusNot(UUID workstreamId, FactStatus status);
}
