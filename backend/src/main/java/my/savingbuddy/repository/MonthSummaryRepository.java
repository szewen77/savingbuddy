package my.savingbuddy.repository;

import my.savingbuddy.domain.MonthSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MonthSummaryRepository extends JpaRepository<MonthSummary, Long> {
    List<MonthSummary> findAllByUserIdOrderByMonthAsc(Long userId);
}
