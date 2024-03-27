package team.boerse.tauschboerse;

import java.util.Date;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

@Entity
public class TauschTermin {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;

    long userid;
    @OneToOne(cascade = CascadeType.ALL)
    KalenderTermin angebot;
    @OneToMany(cascade = CascadeType.ALL)
    KalenderTermin[] gesucht;

    @CreatedDate
    Date createdDate;

    public TauschTermin(long userid, KalenderTermin angebot, KalenderTermin[] gesucht) {
        this.userid = userid;
        this.angebot = angebot;
        this.gesucht = gesucht;
    }

    public TauschTermin() {

    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public long getUserId() {
        return userid;
    }

}
