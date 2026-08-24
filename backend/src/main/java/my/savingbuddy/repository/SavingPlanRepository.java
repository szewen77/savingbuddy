package my.savingbuddy.repository;

import my.savingbuddy.domain.SavingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingPlanRepository extends JpaRepository<SavingPlan, Long> {}
