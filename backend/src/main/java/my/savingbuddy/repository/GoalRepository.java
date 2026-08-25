package my.savingbuddy.repository;

import my.savingbuddy.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Every finder requires a userId. The unscoped variants were deleted, not
 * deprecated, so an unscoped query is a compile error rather than a data leak.
 */
public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByUserIdOrderBySortOrderAsc(Long userId);

    /**
     * Scoped lookup for a client-supplied id. Never use the inherited findById
     * here — the id arrives from a path variable, and an unscoped lookup would
     * let one user read or edit another's goal.
     */
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
