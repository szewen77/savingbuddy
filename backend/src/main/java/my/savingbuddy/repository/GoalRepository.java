package my.savingbuddy.repository;

import my.savingbuddy.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Every finder requires a userId. The unscoped variants were deleted, not
 * deprecated, so an unscoped query is a compile error rather than a data leak.
 */
public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByUserIdOrderBySortOrderAsc(Long userId);
}
