package team.boerse.tauschboerse;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KalenderRepository extends JpaRepository<KalenderTermin, Long> {

}