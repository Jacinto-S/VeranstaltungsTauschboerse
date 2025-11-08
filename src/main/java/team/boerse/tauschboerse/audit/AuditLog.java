package team.boerse.tauschboerse.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_event_type", columnList = "eventType"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_semester", columnList = "semester")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    private LoginMethod loginMethod;

    @Column(columnDefinition = "TEXT")
    private String eventDetails;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    private String ipAddress;

    @Column(length = 2000)
    private String userAgent;

    private String semester;

    @Column(columnDefinition = "LONGTEXT")
    private String stackTrace;

    public AuditLog() {
    }

    public AuditLog(Long userId, AuditEventType eventType, String eventDetails) {
        this.userId = userId;
        this.eventType = eventType;
        this.eventDetails = eventDetails;
        this.timestamp = new Date();
    }

}
