package vn.trainocate.moneytransfer.kyc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.trainocate.moneytransfer.kyc.entity.KycEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycRepository extends JpaRepository<KycEntity, UUID> {

    Optional<KycEntity> findByUserId(UUID userId);
}
