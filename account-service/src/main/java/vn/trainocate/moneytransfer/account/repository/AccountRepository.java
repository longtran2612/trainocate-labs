package vn.trainocate.moneytransfer.account.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.trainocate.moneytransfer.account.entity.AccountEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByAccountNo(String accountNo);

    Optional<AccountEntity> findByMobile(String mobile);

    Optional<AccountEntity> findByCif(String cif);

    Optional<AccountEntity> findTopByOrderByAccountNoDesc();

    /** Pessimistic write lock — used in debit/transfer to prevent double-spend. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.accountNo = :accountNo")
    Optional<AccountEntity> findByAccountNoForUpdate(@Param("accountNo") String accountNo);
}
