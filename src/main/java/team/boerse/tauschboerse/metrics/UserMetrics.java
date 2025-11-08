package team.boerse.tauschboerse.metrics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import team.boerse.tauschboerse.User;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "user_metrics")
public class UserMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    private User user;

    private Boolean usesPasskeys = false;
    private Boolean hasUploadedCalendar = false;
    private Boolean hasCreatedOffer = false;
    private Boolean wasSuccessfullyMatched = false;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstPasskeyUse;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstCalendarUpload;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstOfferCreated;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstSuccessfulMatch;

    // Zähler
    private Integer totalLogins = 0;
    private Integer totalCalendarUploads = 0;
    private Integer totalOffersCreated = 0;
    private Integer totalOffersAccepted = 0;
    private Integer totalMatches = 0;
    private Integer totalLoginAttempts = 0;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastLogin;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastActivity;

    private String registrationSemester;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    public UserMetrics() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public UserMetrics(User user, String registrationSemester) {
        this.user = user;
        this.registrationSemester = registrationSemester;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

}
