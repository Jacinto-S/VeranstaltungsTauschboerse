package team.boerse.tauschboerse;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    List<KalenderTermin> gesucht;

    @CreatedDate
    Date createdDate;

    public TauschTermin(long userid, KalenderTermin angebot, List<KalenderTermin> gesucht) {
        this.userid = userid;
        this.angebot = angebot;
        this.gesucht = gesucht;
        this.createdDate = new Date();
    }

    public TauschTermin() {

    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public long getUserId() {
        return userid;
    }

    public KalenderTermin getAngebot() {
        return angebot;
    }

    public List<KalenderTermin> getGesucht() {
        return gesucht;
    }

    public long getId() {
        return id;
    }

}
