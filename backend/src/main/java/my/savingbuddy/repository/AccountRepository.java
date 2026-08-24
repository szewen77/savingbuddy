package my.savingbuddy.repository;

import my.savingbuddy.domain.Account;
import my.savingbuddy.domain.AccountKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByOrderBySortOrderAsc();
    Optional<Account> findFirstByKind(AccountKind kind);
}
