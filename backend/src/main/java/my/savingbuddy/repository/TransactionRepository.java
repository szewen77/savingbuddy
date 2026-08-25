package my.savingbuddy.repository;

import my.savingbuddy.domain.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every finder requires a userId. The unscoped variants were deleted, not
 * deprecated, so an unscoped query is a compile error rather than a data leak.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    int countByUserIdAndAccountId(Long userId, Long accountId);

    @EntityGraph(attributePaths = "account")
    List<Transaction> findAllByUserIdOrderByOccurredAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "account")
    List<Transaction> findAllByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
        Long userId,
        LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
