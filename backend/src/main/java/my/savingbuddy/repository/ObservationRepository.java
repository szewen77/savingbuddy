package my.savingbuddy.repository;

import my.savingbuddy.domain.Observation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long> {
    List<Observation> findAllByOrderBySortOrderAsc();
}
