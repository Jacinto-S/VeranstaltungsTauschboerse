package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import team.boerse.tauschboerse.suggestions.SuggestionTermRow;

public interface KalenderRepository extends JpaRepository<Kalender, Long> {

    List<Kalender> findAllByUserId(long userId);

    Kalender findByUserId(long userId);

    /**
     * Liefert alle relevanten Termine für die Gruppenvorschläge als schlanke
     * Projektion.
     * Filter: keine Vorlesungen
     * später im Service geprüft.
     */
    @Query("select k.userId as userId, u.studiengang.id as studiengangId, t.start as start, t.end as end, t.name as name, t.type as type "
            +
            "from Kalender k join k.termine t, User u " +
            "where u.id = k.userId and t.type <> team.boerse.tauschboerse.KalenderTerminType.V and t.start is not null and t.end is not null")
    List<SuggestionTermRow> findAllTermsForSuggestions();
}
