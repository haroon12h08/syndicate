package com.syndicate.diligence;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Puts the standard question set on a new transaction.
 *
 * <p>Separate from {@link DiligenceService} because it is needed while a transaction is being
 * created, and the service that answers questions needs the transaction service to check
 * membership - which would make the two depend on each other.
 */
@Component
public class DiligenceQuestionSeeder {

    private final DiligenceQuestionRepository questionRepository;

    public DiligenceQuestionSeeder(DiligenceQuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(UUID transactionId, String transactionType) {
        for (Object[] row : questionRepository.library(transactionType)) {
            questionRepository.save(new DiligenceQuestion(transactionId, (String) row[0], (String) row[1],
                    WorkstreamType.valueOf((String) row[2]), IssueSeverity.valueOf((String) row[3])));
        }
    }
}
