package vn.trainocate.moneytransfer.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.trainocate.moneytransfer.transaction.dto.request.CreateTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.ReverseTransactionRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransactionHistoryRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.TransactionInfoRequest;
import vn.trainocate.moneytransfer.transaction.dto.request.UpdateTransactionStatusRequest;
import vn.trainocate.moneytransfer.transaction.dto.response.ReverseTransactionResponse;
import vn.trainocate.moneytransfer.transaction.dto.response.TransactionResponse;
import vn.trainocate.moneytransfer.transaction.entity.TransactionEntity;
import vn.trainocate.moneytransfer.transaction.exception.BusinessException;
import vn.trainocate.moneytransfer.transaction.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        if (transactionRepository.existsByReferenceId(request.getReferenceId())) {
            throw new BusinessException("DUPLICATE_REFERENCE", "Transaction with this reference ID already exists");
        }

        TransactionEntity entity = TransactionEntity.builder()
                .referenceId(request.getReferenceId())
                .txType(request.getTxType())
                .status("PENDING")
                .senderAccount(request.getSenderAccount())
                .receiverAccount(request.getReceiverAccount())
                .amount(request.getAmount())
                .fee(request.getFee() != null ? request.getFee() : java.math.BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .description(request.getDescription())
                .build();

        entity = transactionRepository.save(entity);
        log.info("Transaction created: txId={}, referenceId={}", entity.getTxId(), entity.getReferenceId());

        return toResponse(entity);
    }

    public TransactionResponse getTransactionInfo(TransactionInfoRequest request) {
        TransactionEntity entity;

        if (request.getTxId() != null) {
            entity = transactionRepository.findById(request.getTxId())
                    .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found"));
        } else if (request.getReferenceId() != null) {
            entity = transactionRepository.findByReferenceId(request.getReferenceId())
                    .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found"));
        } else {
            throw new BusinessException("INVALID_REQUEST", "Either txId or referenceId must be provided");
        }

        return toResponse(entity);
    }

    @Transactional
    public ReverseTransactionResponse reverseTransaction(ReverseTransactionRequest request) {
        TransactionEntity original = transactionRepository.findById(request.getTxId())
                .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found"));

        if ("REVERSED".equals(original.getStatus())) {
            throw new BusinessException("TX_ALREADY_REVERSED", "Transaction has already been reversed");
        }

        original.setStatus("REVERSED");
        transactionRepository.save(original);

        TransactionEntity reverseTx = TransactionEntity.builder()
                .referenceId("REV-" + original.getReferenceId())
                .txType(original.getTxType())
                .status("COMPLETED")
                .senderAccount(original.getReceiverAccount())
                .receiverAccount(original.getSenderAccount())
                .amount(original.getAmount().negate())
                .fee(java.math.BigDecimal.ZERO)
                .currency(original.getCurrency())
                .description("Reversal of " + original.getReferenceId() + ": " + request.getReason())
                .completedAt(LocalDateTime.now())
                .build();

        reverseTx = transactionRepository.save(reverseTx);

        log.info("Transaction reversed: originalTxId={}, reverseTxId={}, initiatedBy={}",
                original.getTxId(), reverseTx.getTxId(), request.getInitiatedBy());

        return ReverseTransactionResponse.builder()
                .reverseTxId(reverseTx.getTxId())
                .status("REVERSED")
                .reversedAmount(original.getAmount())
                .build();
    }

    @Transactional
    public TransactionResponse updateStatus(UpdateTransactionStatusRequest request) {
        TransactionEntity entity = transactionRepository.findById(request.getTxId())
                .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found"));

        entity.setStatus(request.getStatus());

        if ("COMPLETED".equals(request.getStatus())) {
            entity.setCompletedAt(LocalDateTime.now());
        }

        entity = transactionRepository.save(entity);
        log.info("Transaction status updated: txId={}, status={}", entity.getTxId(), entity.getStatus());

        return toResponse(entity);
    }

    public java.util.List<TransactionResponse> getHistory(TransactionHistoryRequest request) {
        return transactionRepository
                .findBySenderAccountOrReceiverAccountOrderByInitiatedAtDesc(
                        request.getAccountNo(), request.getAccountNo())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TransactionResponse toResponse(TransactionEntity entity) {
        return TransactionResponse.builder()
                .txId(entity.getTxId())
                .referenceId(entity.getReferenceId())
                .txType(entity.getTxType())
                .status(entity.getStatus())
                .senderAccount(entity.getSenderAccount())
                .receiverAccount(entity.getReceiverAccount())
                .amount(entity.getAmount())
                .fee(entity.getFee())
                .currency(entity.getCurrency())
                .description(entity.getDescription())
                .initiatedAt(entity.getInitiatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
