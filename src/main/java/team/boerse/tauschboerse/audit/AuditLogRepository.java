package team.boerse.tauschboerse.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

        Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

        @Query("SELECT a FROM AuditLog a WHERE " +
                        "(:userId IS NULL OR a.userId = :userId) AND " +
                        "(:eventType IS NULL OR a.eventType = :eventType) AND " +
                        "(:semester IS NULL OR a.semester = :semester) AND " +
                        "(:loginMethod IS NULL OR a.loginMethod = :loginMethod) AND " +
                        "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
                        "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
                        "(:includeDebug = true OR a.eventType NOT IN (team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_VIEW, team.boerse.tauschboerse.audit.AuditEventType.OFFER_VIEW)) "
                        +
                        "ORDER BY a.timestamp DESC")
        Page<AuditLog> findByFilters(
                        Long userId,
                        AuditEventType eventType,
                        String semester,
                        LoginMethod loginMethod,
                        Date startDate,
                        Date endDate,
                        boolean includeDebug,
                        Pageable pageable);

        @Query("SELECT a.loginMethod, COUNT(a) FROM AuditLog a WHERE a.eventType = 'LOGIN_SUCCESS' AND a.semester = ?1 GROUP BY a.loginMethod")
        List<Object[]> countLoginMethodsBySemester(String semester);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as date, COUNT(a) FROM AuditLog a " +
                        "WHERE a.semester = :semester AND (:includeDebug = true OR a.eventType NOT IN (team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_VIEW, team.boerse.tauschboerse.audit.AuditEventType.OFFER_VIEW)) "
                        +
                        "GROUP BY FUNCTION('DATE', a.timestamp) ORDER BY date")
        List<Object[]> countEventsByDateAndSemester(@Param("semester") String semester,
                        @Param("includeDebug") boolean includeDebug);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as date, COUNT(a) FROM AuditLog a " +
                        "WHERE a.semester = :semester AND a.timestamp >= :start AND a.timestamp <= :end AND (:includeDebug = true OR a.eventType NOT IN (team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_VIEW, team.boerse.tauschboerse.audit.AuditEventType.OFFER_VIEW)) "
                        +
                        "GROUP BY FUNCTION('DATE', a.timestamp) ORDER BY date")
        List<Object[]> countEventsByDateAndSemesterInRange(@Param("semester") String semester,
                        @Param("start") Date start,
                        @Param("end") Date end, @Param("includeDebug") boolean includeDebug);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as date, COUNT(a) FROM AuditLog a " +
                        "WHERE a.semester = :semester AND a.eventType = :eventType " +
                        "GROUP BY FUNCTION('DATE', a.timestamp) ORDER BY date")
        List<Object[]> countEventsByDateAndSemesterForType(@Param("semester") String semester,
                        @Param("eventType") AuditEventType eventType);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as date, COUNT(a) FROM AuditLog a " +
                        "WHERE a.semester = :semester AND a.eventType = :eventType AND a.timestamp >= :start AND a.timestamp <= :end "
                        +
                        "GROUP BY FUNCTION('DATE', a.timestamp) ORDER BY date")
        List<Object[]> countEventsByDateAndSemesterForTypeInRange(@Param("semester") String semester,
                        @Param("eventType") AuditEventType eventType, @Param("start") Date start,
                        @Param("end") Date end);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as day, FUNCTION('HOUR', a.timestamp) as hour, COUNT(a) FROM AuditLog a "
                        +
                        "WHERE a.semester = :semester AND a.timestamp >= :start AND a.timestamp <= :end AND (:includeDebug = true OR a.eventType NOT IN (team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_VIEW, team.boerse.tauschboerse.audit.AuditEventType.OFFER_VIEW)) "
                        +
                        "GROUP BY FUNCTION('DATE', a.timestamp), FUNCTION('HOUR', a.timestamp) ORDER BY day, hour")
        List<Object[]> countEventsByHourAndSemester(@Param("semester") String semester, @Param("start") Date start,
                        @Param("end") Date end, @Param("includeDebug") boolean includeDebug);

        @Query("SELECT FUNCTION('DATE', a.timestamp) as day, FUNCTION('HOUR', a.timestamp) as hour, COUNT(a) FROM AuditLog a "
                        +
                        "WHERE a.semester = :semester AND a.eventType = :eventType AND a.timestamp >= :start AND a.timestamp <= :end "
                        +
                        "GROUP BY FUNCTION('DATE', a.timestamp), FUNCTION('HOUR', a.timestamp) ORDER BY day, hour")
        List<Object[]> countEventsByHourAndSemesterForType(@Param("semester") String semester,
                        @Param("eventType") AuditEventType eventType, @Param("start") Date start,
                        @Param("end") Date end);

}
