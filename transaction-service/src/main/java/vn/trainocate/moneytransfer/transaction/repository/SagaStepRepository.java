package vn.trainocate.moneytransfer.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.trainocate.moneytransfer.transaction.entity.SagaStepEntity;

import java.util.UUID;

public interface SagaStepRepository extends JpaRepository<SagaStepEntity, UUID> {
}
