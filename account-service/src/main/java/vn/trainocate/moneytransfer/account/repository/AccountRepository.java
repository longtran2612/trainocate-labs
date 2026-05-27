package vn.trainocate.moneytransfer.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
