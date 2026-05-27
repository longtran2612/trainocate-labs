package vn.trainocate.moneytransfer.limit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.trainocate.moneytransfer.limit.entity.LimitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LimitRepository extends JpaRepository<LimitEntity, UUID> {

    Optional<LimitEntity> findByAccountNoAndTransferType(String accountNo, String transferType);

    List<LimitEntity> findAllByAccountNo(String accountNo);
}
