package vn.trainocate.moneytransfer.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.trainocate.moneytransfer.transaction.entity.SagaStateEntity;

import java.util.Optional;
import java.util.UUID;

public interface SagaStateRepository extends JpaRepository<SagaStateEntity, UUID> {
    Optional<SagaStateEntity> findByTxId(UUID txId);
}
