package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TauschTerminRepository extends JpaRepository<TauschTermin, Long> {

    List<TauschTermin> findTauschTerminByUserid(Long userid);

    // find TauschTermin by KaleanderTermin in gesucht list
    TauschTermin findTauschTerminByGesucht(KalenderTermin gesucht);

    TauschTermin findTauschTerminByAngebot(KalenderTermin angebot);

}
