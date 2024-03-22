package team.boerse.tauschboerse;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.DateTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class KalenderController {

    // list all calendar entries
    @Autowired
    private KalenderTerminRepository kalenderTerminRepository;
    @Autowired
    private KalenderRepository kalenderRepository;
    @Autowired
    private TauschTerminRepository tauschTerminRepository;

    @GetMapping(path = "/kalender", produces = "application/json")
    public Iterable<KalenderTermin> list() {
        List<KalenderTermin> kalender = new ArrayList<>();
        kalenderTerminRepository.findAll().forEach(kalender::add);
        if (kalender.isEmpty()) {
            kalender.add(new KalenderTermin(new Date(), new Date(), "Test", "Test", "Test", KalenderTerminType.P));
        }

        return kalender;
    }

    public static KalenderTerminType getKalenderType(String kalenderEintrag) {
        Matcher matcher = Pattern.compile("\\(([^)]+)\\)").matcher(kalenderEintrag);
        if (matcher.find()) {
            return switch (matcher.group(1).substring(0, 1)) {
                case "Ü", "U" -> KalenderTerminType.U;
                case "P" -> KalenderTerminType.P;
                case "V" -> KalenderTerminType.V;
                case "S" -> KalenderTerminType.S;
                default -> KalenderTerminType.UNKNOWN;
            };
        }
        return null;
    }

    @PostMapping("/uploadKalender")
    public void uploadICSFile(@RequestBody(required = false) String icsFile)
            throws IOException, ParserException, ParseException {
        User user = UserUtil.getUser();
        if (user == null) {
            return;
        }
        Kalender oldkalender = kalenderRepository.findByUserId(user.getId());

        StringReader sin = new StringReader(icsFile);
        CalendarBuilder builder = new CalendarBuilder();
        net.fortuna.ical4j.model.Calendar calendar = builder.build(sin);
        Kalender kalender = new Kalender();
        kalender.setUserId(user.getId());
        List<KalenderTermin> termine = kalender.getTermine();
        for (Object o : calendar.getComponents()) {
            net.fortuna.ical4j.model.Component component = (net.fortuna.ical4j.model.Component) o;
            if (component.getName().equals("VEVENT")) {
                termine.add(getTermin(component));
            }
        }
        kalender.setTermine(termine);
        if (oldkalender != null) {
            kalenderRepository.delete(oldkalender);
        }
        kalenderRepository.save(kalender);
    }

    @GetMapping("/findTest")
    public ResponseEntity<TauschTermin> findTest() {
        User user = UserUtil.getUser();
        if (user == null) {
            return null;
        }
        Kalender kalender = kalenderRepository.findByUserId(user.getId());
        if (kalender == null) {
            return null;
        }
        KalenderTermin termin = kalenderTerminRepository.findById(107l).orElse(null);
        if (termin == null) {
            return null;
        }
        TauschTermin tausch = tauschTerminRepository.findTauschTerminByGesucht(termin);
        return ResponseEntity.ok(tausch);

    }

    @GetMapping(value = "/myKalender", produces = "application/json")
    public ResponseEntity<String> getKalender(@RequestParam(required = false) String terminid)
            throws JsonProcessingException {
        User user = UserUtil.getUser();
        if (user == null) {
            return null;
        }
        Kalender kalender = kalenderRepository.findByUserId(user.getId());
        if (kalender == null) {
            return ResponseEntity.badRequest().body("No calendar found");
        }
        List<List<KalenderTerminDTO>> kalenderList = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        for (int i = 0; i != 5; i++)
            kalenderList.add(new ArrayList<>());
        for (KalenderTermin termin : kalender.getTermine()) {
            Date d = termin.getStart();
            c.setTime(d);
            int day = c.get(Calendar.DAY_OF_WEEK) - 2;
            String start = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            c.setTime(termin.getEnd());
            String end = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));

            KalenderTerminDTO terminDTO = new KalenderTerminDTO(termin.getName(), "",
                    termin.getType().getColorCode(), start, end, termin.id);

            kalenderList.get(day).add(terminDTO);
        }

        // fill each day with empty entries to have 6 entries per day in the frontend.
        // 8:15, 10:00, 11:45, 14:15, 16:00, 17:45
        if (terminid != null) {
            String[] starts = { "08:15", "10:00", "11:45", "14:15", "16:00", "17:45" };
            String[] ends = { "09:45", "11:30", "13:15", "15:45", "17:30", "19:15" };
            List<TauschTermin> termine = tauschTerminRepository.findAll();
            KalenderTermin usersTermin = kalenderTerminRepository.findById(Long.parseLong(terminid))
                    .orElse(null);
            if (usersTermin == null) {
                return ResponseEntity.badRequest().body("No termin found");
            }
            String title = usersTermin.getName().split("\\(")[0];
            c.setTime(usersTermin.getStart());
            String userstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE));
            c.setTime(usersTermin.getEnd());
            int userday = c.get(Calendar.DAY_OF_WEEK) - 2;

            for (TauschTermin termin : termine) {
                if (termin.angebot.getName().indexOf(title) == -1) {
                    continue;
                }

                if (termin.userid != user.getId() && usersTermin.getType() == termin.angebot.getType()) {
                    KalenderTermin kalenderTermin = termin.angebot;
                    // is usersTermin on the same day and time?
                    if (!usersTermin.getStart().equals(kalenderTermin.getStart())
                            && usersTermin.getName().indexOf(title) != -1) {

                    } else {
                        continue;
                    }
                    for (KalenderTermin ge : termin.gesucht) {
                        c.setTime(ge.getStart());
                        String start = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE));

                        c.setTime(ge.getEnd());

                        if (userstart.equals(start) && c.get(Calendar.DAY_OF_WEEK) - 2 == userday) {
                            KalenderTermin angebot = termin.angebot;
                            Date sDate = angebot.getStart();
                            c.setTime(sDate);
                            String angebotstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            c.setTime(angebot.getEnd());
                            String angebotend = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));

                            KalenderTerminDTO terminDTO = new KalenderTerminDTO(angebot.getName(), "OFFER",
                                    angebot.getType().getColorCode(), angebotstart, angebotend, angebot.id);
                            c.setTime(angebot.getStart());
                            int theday = c.get(Calendar.DAY_OF_WEEK) - 2;
                            kalenderList.get(theday).add(terminDTO);
                            break;
                        }

                    }
                } else if (usersTermin.getType() == termin.angebot.getType()) {
                    for (KalenderTermin ge : termin.gesucht) {
                        c.setTime(ge.getStart());

                        Date sDate = ge.getStart();
                        c.setTime(sDate);
                        String angebotstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE));
                        c.setTime(ge.getEnd());
                        String angebotend = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE));

                        KalenderTerminDTO terminDTO = new KalenderTerminDTO(ge.getName(), "ICH",
                                ge.getType().getColorCode(), angebotstart, angebotend, ge.id);
                        c.setTime(ge.getStart());
                        int theday = c.get(Calendar.DAY_OF_WEEK) - 2;
                        kalenderList.get(theday).add(terminDTO);

                    }
                }
            }

            for (int i = 0; i != 5; i++) {
                List<KalenderTerminDTO> day = kalenderList.get(i);
                for (int j = 0; j != 6; j++) {
                    boolean found = false;
                    for (KalenderTerminDTO termin : day) {
                        if (termin.start().equalsIgnoreCase(starts[j])) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        String colorcodelightblue = "#5d7a8f";
                        day.add(new KalenderTerminDTO(title, "",
                                colorcodelightblue, starts[j],
                                ends[j], -1));
                    }
                }
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(kalenderList);
        return ResponseEntity.ok(json);

        // MAGIC
    }

    // {
    // "title": "CG (V)",
    // "subtext": "",
    // "color": "#4961E1",
    // "start": "11:45",
    // "end": "13:15"
    // }
    // build record

    record KalenderTerminDTO(String title, String subtext, String color, String start, String end, long offerid) {
    };

    private KalenderTermin getTermin(net.fortuna.ical4j.model.Component component) throws ParseException {
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
        termin.setType(getKalenderType(termin.getName()));
        kalenderTerminRepository.save(termin);
        return termin;
    }

}
