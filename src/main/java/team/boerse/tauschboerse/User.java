package team.boerse.tauschboerse;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

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

    public Boolean isBanned() {
        return isBanned;
    }

    public void setBanned(Boolean isBanned) {
        this.isBanned = isBanned;
    }

    public String getBanReason() {
        return banReason == null ? "" : banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public Boolean isAdmin() {
        return isAdmin;
    }

    // Getter, Setter und Konstruktoren hier...
}
