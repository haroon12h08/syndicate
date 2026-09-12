package com.syndicate.issue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {
    List<Issue> findByWorkstreamId(UUID workstreamId);

    Optional<Issue> findFirstByConflictKey(String conflictKey);
}
