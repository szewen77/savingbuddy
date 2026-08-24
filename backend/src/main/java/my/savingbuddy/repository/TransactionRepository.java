package my.savingbuddy.repository;

import my.savingbuddy.domain.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    int countByAccountId(Long accountId);

    @EntityGraph(attributePaths = "account")
    List<Transaction> findAllByOrderByOccurredAtDescIdDesc();

    @EntityGraph(attributePaths = "account")
    List<Transaction> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
        LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
