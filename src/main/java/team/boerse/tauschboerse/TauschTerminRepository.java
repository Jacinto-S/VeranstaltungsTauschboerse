package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TauschTerminRepository extends JpaRepository<TauschTermin, Long> {

        List<TauschTermin> findTauschTerminByUserid(Long userid);

        TauschTermin findTauschTerminByGesucht(KalenderTermin gesucht);

        TauschTermin findTauschTerminByAngebot(KalenderTermin angebot);

        // Gefilterte Suche: Nur Angebote eines Kurses (Prefix) und Typs, nicht vom
        // aktuellen Nutzer
        List<TauschTermin> findByAngebot_TypeAndAngebot_NameStartingWithAndUseridNot(
                        KalenderTerminType type,
                        String angebotNamePrefix,
                        Long userid);

        List<TauschTermin> findByUseridAndAngebot_TypeAndAngebot_NameStartingWith(
                        Long userid,
                        KalenderTerminType type,
                        String angebotNamePrefix);

        // Pessimistic Lock, um Race Conditions bei acceptOffer zu verhindern
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select t from TauschTermin t where t.angebot = :angebot")
        TauschTermin findWithLockByAngebot(@Param("angebot") KalenderTermin angebot);

}
