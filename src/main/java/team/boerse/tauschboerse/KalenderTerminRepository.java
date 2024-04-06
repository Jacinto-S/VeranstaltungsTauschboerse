package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KalenderTerminRepository extends JpaRepository<KalenderTermin, Long> {

    List<KalenderTermin> findByNameStartsWith(String name);

}
