package team.boerse.tauschboerse;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class User {

    @Id
    @GeneratedValue
    private String id;
    private String hsMail;
    private String privateMail;
    private String vorname;
    private String nachname;
    private String studiengang;
    private String accessToken;
    private Boolean complexSwitchAccepted;
    private Boolean mailVerified;
    private Boolean privateMailVerified;
    private Boolean isBanned;
    private String banReason;

    public User(String hsMail, String privateMail, String vorname, String nachname, String studiengang,
            String accessToken, Boolean complexSwitchAccepted, Boolean mailVerified, Boolean privateMailVerified,
            Boolean isBanned, String banReason) {
        this.hsMail = hsMail;
        this.privateMail = privateMail;
        this.vorname = vorname;
        this.nachname = nachname;
        this.studiengang = studiengang;
        this.accessToken = accessToken;
        this.complexSwitchAccepted = complexSwitchAccepted;
        this.mailVerified = mailVerified;
        this.privateMailVerified = privateMailVerified;
        this.isBanned = isBanned;
        this.banReason = banReason;
    }

    public UUID getId() {
        return UUID.fromString(id);
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

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(String nachname) {
        this.nachname = nachname;
    }

    public String getStudiengang() {
        return studiengang;
    }

    public void setStudiengang(String studiengang) {
        this.studiengang = studiengang;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Boolean getComplexSwitchAccepted() {
        return complexSwitchAccepted;
    }

    public void setComplexSwitchAccepted(Boolean complexSwitchAccepted) {
        this.complexSwitchAccepted = complexSwitchAccepted;
    }

    public Boolean getMailVerified() {
        return mailVerified;
    }

    public void setMailVerified(Boolean mailVerified) {
        this.mailVerified = mailVerified;
    }

    public Boolean getPrivateMailVerified() {
        return privateMailVerified;
    }

    public void setPrivateMailVerified(Boolean privateMailVerified) {
        this.privateMailVerified = privateMailVerified;
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
