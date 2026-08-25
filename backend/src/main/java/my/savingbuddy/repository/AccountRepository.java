package my.savingbuddy.repository;

import my.savingbuddy.domain.Account;
import my.savingbuddy.domain.AccountKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Every finder requires a userId. The unscoped variants were deleted, not
 * deprecated, so an unscoped query is a compile error rather than a data leak.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByUserIdOrderBySortOrderAsc(Long userId);
    Optional<Account> findFirstByUserIdAndKind(Long userId, AccountKind kind);
    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
