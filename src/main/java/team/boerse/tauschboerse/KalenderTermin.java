package team.boerse.tauschboerse;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

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

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public KalenderTerminType getType() {
        return type;
    }

    public void setType(KalenderTerminType type) {
        this.type = type;
    }

    public long getId() {
        return id;
    }

}
