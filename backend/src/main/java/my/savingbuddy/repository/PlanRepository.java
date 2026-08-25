package my.savingbuddy.repository;

import my.savingbuddy.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
