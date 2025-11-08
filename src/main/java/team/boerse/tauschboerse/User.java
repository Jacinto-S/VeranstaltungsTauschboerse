package team.boerse.tauschboerse;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;
import team.boerse.tauschboerse.studiengang.Studiengang;

@Setter
@Getter
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String hsMail;
    private String privateMail;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = String.class)
    @CollectionTable(name = "user_access_tokens")
    private List<String> accessToken;
    private Boolean isBanned;
    private String banReason;
    private Boolean isAdmin;

    // Neue Felder für Metrics
    private Boolean usesPasskeys = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = true)
    private Date registrationDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = true)
    private Date lastActivityDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studiengang_id", nullable = true)
    private Studiengang studiengang;

    public User(String hsMail, String privateMail,
            Boolean isBanned, String banReason) {
        this.hsMail = hsMail;
        this.privateMail = privateMail;
        this.isBanned = isBanned;
        this.banReason = banReason;
    }

    public User() {
    }

    public List<String> getAccessToken() {
        if (accessToken == null) {
            accessToken = new ArrayList<>();
        }
        return accessToken;
    }

}
