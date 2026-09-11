package com.syndicate.workstream;

import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.workstream.dto.CreateWorkstreamRequest;
import com.syndicate.workstream.dto.UpdateWorkstreamRequest;
import com.syndicate.workstream.dto.WorkstreamDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkstreamService {

    private final WorkstreamRepository workstreamRepository;
    private final TransactionService transactionService;

    public WorkstreamService(WorkstreamRepository workstreamRepository, TransactionService transactionService) {
        this.workstreamRepository = workstreamRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public WorkstreamDto create(UUID transactionId, CreateWorkstreamRequest request, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        Transaction transaction = transactionService.findTransaction(transactionId);
        Workstream workstream = workstreamRepository.save(
                new Workstream(transaction, request.type(), request.description()));
        return WorkstreamDto.from(workstream);
    }

    public List<WorkstreamDto> listForTransaction(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return workstreamRepository.findByTransactionId(transactionId).stream().map(WorkstreamDto::from).toList();
    }

    public WorkstreamDto get(UUID workstreamId, UUID callerId) {
        Workstream workstream = findWorkstream(workstreamId);
        transactionService.requireMembership(workstream.getTransaction().getId(), callerId);
        return WorkstreamDto.from(workstream);
    }

    @Transactional
    public WorkstreamDto update(UUID workstreamId, UpdateWorkstreamRequest request, UUID callerId) {
        Workstream workstream = findWorkstream(workstreamId);
        transactionService.requireMembership(workstream.getTransaction().getId(), callerId);
        workstream.setDescription(request.description());
        return WorkstreamDto.from(workstream);
    }

    public Workstream findWorkstream(UUID workstreamId) {
        return workstreamRepository.findById(workstreamId)
                .orElseThrow(() -> new ResourceNotFoundException("Workstream not found: " + workstreamId));
    }

    public void requireAccess(UUID workstreamId, UUID callerId) {
        Workstream workstream = findWorkstream(workstreamId);
        transactionService.requireMembership(workstream.getTransaction().getId(), callerId);
    }
}
