package team.boerse.tauschboerse;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class User {

    @Id
    @GeneratedValue
    private Long id;
    private String hsMail;
    private String privateMail;
    private String accessToken;
    private Boolean isBanned;
    private String banReason;

    public User(String hsMail, String privateMail,
            String accessToken,
            Boolean isBanned, String banReason) {
        this.hsMail = hsMail;
        this.privateMail = privateMail;
        this.accessToken = accessToken;
        this.isBanned = isBanned;
        this.banReason = banReason;
    }

    public User() {
    }

    public long getId() {
        return id;
    }

    public String getHsMail() {
        return hsMail;
    }

    public String getPrivateMail() {
        return privateMail;
    }

    public void setPrivateMail(String privateMail) {
        this.privateMail = privateMail;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Boolean getIsBanned() {
        return isBanned;
    }

    public void setIsBanned(Boolean isBanned) {
        this.isBanned = isBanned;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    // Getter, Setter und Konstruktoren hier...
}
