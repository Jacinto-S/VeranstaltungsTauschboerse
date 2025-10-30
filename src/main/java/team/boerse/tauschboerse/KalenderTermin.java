package team.boerse.tauschboerse;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class KalenderTermin {

    Date start;
    Date end;
    String name;
    String description;
    String location;
    KalenderTerminType type;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;

    public KalenderTermin(Date start, Date end, String name, String description, String location,
            KalenderTerminType type) {
        this.start = start;
        this.end = end;
        this.name = name;
        this.description = description;
        this.location = location;
        this.type = type;
    }

    public KalenderTermin() {
    }

    // Sonderbehandlung für Namen mit (SU-...), diese werden zu (S-...) umgewandelt
    public void setName(String name) {
        if (name != null && name.indexOf("(SU-") != -1) {
            name = name.replace("(SU-", "(S-");
        }
        this.name = name;
    }

}
