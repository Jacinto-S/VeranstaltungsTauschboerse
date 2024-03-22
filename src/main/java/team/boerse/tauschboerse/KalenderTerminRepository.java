package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KalenderTerminRepository extends JpaRepository<KalenderTermin, Long> {

    // name, startsWith, endsWith, contains
    List<KalenderTermin> findByNameStartsWith(String name);

}
