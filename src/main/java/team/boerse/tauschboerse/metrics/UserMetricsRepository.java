package team.boerse.tauschboerse.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserMetricsRepository extends JpaRepository<UserMetrics, Long> {

    Optional<UserMetrics> findByUserId(Long userId);

    List<UserMetrics> findByRegistrationSemester(String semester);

    Long countByUsesPasskeys(Boolean usesPasskeys);

    Long countByHasUploadedCalendar(Boolean hasUploadedCalendar);

    Long countByHasCreatedOffer(Boolean hasCreatedOffer);

    Long countByWasSuccessfullyMatched(Boolean wasSuccessfullyMatched);

    @Query("SELECT COUNT(um) FROM UserMetrics um WHERE um.usesPasskeys = true AND um.registrationSemester = ?1")
    Long countPasskeyUsersBySemester(String semester);

    @Query("SELECT COUNT(um) FROM UserMetrics um WHERE um.hasUploadedCalendar = true AND um.registrationSemester = ?1")
    Long countCalendarUploadsBySemester(String semester);

    @Query("SELECT COUNT(um) FROM UserMetrics um WHERE um.hasCreatedOffer = true AND um.registrationSemester = ?1")
    Long countOfferCreatorsBySemester(String semester);

    @Query("SELECT COUNT(um) FROM UserMetrics um WHERE um.wasSuccessfullyMatched = true AND um.registrationSemester = ?1")
    Long countSuccessfulMatchesBySemester(String semester);

    @Query("SELECT um.registrationSemester, COUNT(um) FROM UserMetrics um WHERE um.registrationSemester IS NOT NULL GROUP BY um.registrationSemester ORDER BY um.registrationSemester DESC LIMIT 6")
    List<Object[]> countUsersBySemester();

    @Query("SELECT COUNT(um) FROM UserMetrics um WHERE um.registrationSemester = ?1")
    Long countUsersBySemesterTotal(String semester);

}
