package vn.trainocate.moneytransfer.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.trainocate.moneytransfer.transaction.entity.TransactionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);
}
