package my.savingbuddy.repository;

import my.savingbuddy.domain.Bill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    int countByAccountId(Long accountId);

    @EntityGraph(attributePaths = "account")
    List<Bill> findAllByOrderByDueDayAsc();
}
