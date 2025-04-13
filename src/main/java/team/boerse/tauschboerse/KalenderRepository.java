package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KalenderRepository extends JpaRepository<Kalender, Long> {

    List<Kalender> findAllByUserId(long userId);

    Kalender findByUserId(long userId);
}
