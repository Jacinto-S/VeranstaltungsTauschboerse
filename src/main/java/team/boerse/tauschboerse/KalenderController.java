package team.boerse.tauschboerse;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;

@RestController
public class KalenderController {

    // list all calendar entries
    @Autowired
    private KalenderRepository kalenderRepository;

    @GetMapping(path = "/kalender", produces = "application/json")
    public Iterable<KalenderTermin> list() {
        List<KalenderTermin> kalender = new ArrayList<>();
        kalenderRepository.findAll().forEach(kalender::add);
        if (kalender.isEmpty()) {
            kalender.add(new KalenderTermin(new Date(), new Date(), "Test", "Test", "Test", KalenderTerminType.P));
        }

        return kalender;
    }

    @GetMapping("/uploadKalender")
    public void uploadICSFile(@RequestBody(required = false) String icsFile)
            throws IOException, ParserException, ParseException {

        if (icsFile == null) {
            icsFile = new String(Files
                    .readAllBytes(
                            new File("C:\\boerse\\tauschboerse\\src\\main\\resources\\static\\nico.ics").toPath()));
        }

        StringReader sin = new StringReader(icsFile);
        CalendarBuilder builder = new CalendarBuilder();
        net.fortuna.ical4j.model.Calendar calendar = builder.build(sin);
        for (Object o : calendar.getComponents()) {
            net.fortuna.ical4j.model.Component component = (net.fortuna.ical4j.model.Component) o;
            if (component.getName().equals("VEVENT")) {
                KalenderTermin termin = new KalenderTermin();
                termin.setName(component.getProperty("SUMMARY").getValue());
                termin.setDescription(component.getProperty("DESCRIPTION").getValue());
                termin.setLocation(component.getProperty("LOCATION").getValue());
                String startDateString = component.getProperty("DTSTART").getValue();
                DateTime startDate = new DateTime(startDateString);
                Date startDateObject = startDate;
                termin.setStart(startDateObject);

                String endDateString = component.getProperty("DTEND").getValue();
                DateTime endDate = new DateTime(endDateString);
                Date endDateObject = endDate;
                termin.setEnd(endDateObject);
                
                termin.setType(KalenderTerminType.P);
                kalenderRepository.save(termin);
            }
        }
    }

    // TODO: Hilfsmethode um 


}
