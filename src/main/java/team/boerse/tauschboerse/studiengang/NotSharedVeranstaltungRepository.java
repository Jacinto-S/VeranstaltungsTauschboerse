package team.boerse.tauschboerse.studiengang;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotSharedVeranstaltungRepository extends JpaRepository<NotSharedVeranstaltung, Long> {
    List<NotSharedVeranstaltung> findByStudiengang_Id(Long studiengangId);

    boolean existsByStudiengang_IdAndVeranstaltungBaseNameIgnoreCase(Long studiengangId, String veranstaltungBaseName);

    void deleteByStudiengang_Id(Long studiengangId);
}
