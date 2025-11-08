package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KalenderTerminRepository extends JpaRepository<KalenderTermin, Long> {

    List<KalenderTermin> findByNameStartsWith(String name);

    /**
     * Gibt alle eindeutigen Kürzel (baseName) aus allen Kalendereinträgen zurück.
     * Extrahiert das Kürzel aus dem Namen (Prefix vor dem ersten Leerzeichen).
     */
    @Query("SELECT DISTINCT SUBSTRING(t.name, 1, LOCATE(' ', t.name) - 1) as kurzel " +
            "FROM KalenderTermin t " +
            "WHERE t.name IS NOT NULL AND LOCATE(' ', t.name) > 0 " +
            "ORDER BY kurzel ASC")
    List<String> getAllDistinctKurzel();

    /**
     * Gibt alle eindeutigen Kürzel für einen bestimmten Studiengang zurück.
     * Filtert nach Kalendereinträgen von Usern mit diesem Studiengang.
     */
    @Query("SELECT DISTINCT SUBSTRING(t.name, 1, LOCATE(' ', t.name) - 1) as kurzel " +
            "FROM KalenderTermin t " +
            "WHERE t.id IN (" +
            "  SELECT kt.id FROM KalenderTermin kt " +
            "  WHERE EXISTS (" +
            "    SELECT 1 FROM Kalender k WHERE kt IN ELEMENTS(k.termine) " +
            "    AND EXISTS (" +
            "      SELECT 1 FROM User u WHERE k.userId = u.id AND u.studiengang.id = :studiengangId" +
            "    )" +
            "  )" +
            ") " +
            "AND t.name IS NOT NULL AND LOCATE(' ', t.name) > 0 " +
            "ORDER BY kurzel ASC")
    List<String> getAllDistinctKurzelByStudiengang(@Param("studiengangId") Long studiengangId);

}
