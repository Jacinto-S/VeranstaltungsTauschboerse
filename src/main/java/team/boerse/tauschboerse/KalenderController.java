package team.boerse.tauschboerse;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.CalendarComponent;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.metrics.UserMetricsService;
import team.boerse.tauschboerse.settings.SystemSettingsService;
import team.boerse.tauschboerse.studiengang.NotSharedService;
import team.boerse.tauschboerse.suggestions.GroupSuggestionService;
import team.boerse.tauschboerse.suggestions.GroupSuggestionService.SuggestedSlot;

@RestController
@RequiredArgsConstructor
public class KalenderController {

    private final KalenderTerminRepository kalenderTerminRepository;
    private final KalenderRepository kalenderRepository;
    private final TauschTerminRepository tauschTerminRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserMetricsService userMetricsService;
    private final SystemSettingsService systemSettingsService;
    private final GroupSuggestionService groupSuggestionService;
    private final NotSharedService notSharedService;
    private final Logger logger = LoggerFactory.getLogger(KalenderController.class);

    public static KalenderTerminType getKalenderType(String kalenderEintrag) {
        Matcher matcher = Pattern.compile("\\(([^)]+)\\)").matcher(kalenderEintrag);
        if (matcher.find()) {
            return switch (matcher.group(1).substring(0, 1)) {
                case "Ü", "U" -> KalenderTerminType.U;
                case "P" -> KalenderTerminType.P;
                case "V" -> KalenderTerminType.V;
                case "S", "SU" -> KalenderTerminType.S;
                case "T" -> KalenderTerminType.T;
                default -> KalenderTerminType.UNKNOWN;
            };
        }
        return null;
    }

    @PostMapping("/uploadKalender")
    public ResponseEntity<String> uploadICSFile(@RequestBody String icsFile) throws IOException {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        List<Kalender> oldkalenderList = kalenderRepository.findAllByUserId(user.getId());
        if (icsFile == null || icsFile.isBlank()) {
            return ResponseEntity.badRequest().body("Empty body");
        }

        String normalized = icsFile;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        String trimmed = normalized.stripLeading();
        if (!trimmed.toUpperCase().startsWith("BEGIN:VCALENDAR")) {
            logger.warn("Received non-ICS upload (does not start with BEGIN:VCALENDAR)");
            return ResponseEntity.badRequest().body("Invalid ICS content");
        }
        net.fortuna.ical4j.model.Calendar calendar;
        try {
            StringReader sin = new StringReader(icsFile);
            CalendarBuilder builder = new CalendarBuilder();
            calendar = builder.build(sin);
        } catch (ParserException e) {
            logger.warn("Failed to parse ICS upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid ICS format");
        } catch (Exception e) {
            logger.warn("Error while handling ICS upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid ICS");
        }
        Kalender kalender = new Kalender();
        kalender.setUserId(user.getId());
        List<KalenderTermin> termine = kalender.getTermine();
        try {
            for (CalendarComponent o : calendar.getComponents()) {
                net.fortuna.ical4j.model.Component component = o;
                if (component.getName().equals("VEVENT")) {
                    try {
                        termine.add(getTermin(component));
                    } catch (ParseException pe) {
                        logger.warn("Skipping malformed VEVENT: {}", pe.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Error iterating calendar components: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("Invalid ICS events");
        }
        kalender.setTermine(termine);
        if (oldkalenderList != null && oldkalenderList.size() > 0) {
            java.util.Set<Long> oldTerminIds = new java.util.HashSet<>();
            for (Kalender old : oldkalenderList) {
                if (old != null && old.getTermine() != null) {
                    for (KalenderTermin t : old.getTermine()) {
                        if (t != null)
                            oldTerminIds.add(t.getId());
                    }
                    old.getTermine().clear();
                    kalenderRepository.save(old);
                }
            }

            java.util.List<TauschTermin> myOffers = tauschTerminRepository.findTauschTerminByUserid(user.getId());
            if (myOffers != null && !myOffers.isEmpty()) {
                tauschTerminRepository.deleteAll(myOffers);
            }

            if (!oldTerminIds.isEmpty()) {
                try {
                    kalenderTerminRepository.deleteAllById(oldTerminIds);
                } catch (Exception ignore) {
                    for (Long id : oldTerminIds) {
                        try {
                            kalenderTerminRepository.deleteById(id);
                        } catch (Exception ex) {
                        }
                    }
                }
            }

            for (Kalender old : oldkalenderList) {
                try {
                    kalenderRepository.delete(old);
                } catch (Exception ex) {
                    logger.warn("Failed to delete old calendar {}: {}", old != null ? old.getUserId() : -1,
                            ex.getMessage());
                }
            }
        }
        logger.info(String.format("User %s uploaded a new calendar", user.getHsMail()));
        kalenderRepository.save(kalender);
        try {
            groupSuggestionService.rebuildCache();
        } catch (Exception ex) {
            logger.warn("Failed to rebuild suggestion cache after upload: {}", ex.getMessage());
        }
        try {
            auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_UPLOAD,
                    "Uploaded calendar");
            userMetricsService.getOrCreateMetrics(user.getId(),
                    team.boerse.tauschboerse.audit.SemesterUtil.getSemesterForDate(new java.util.Date()));
            userMetricsService.recordCalendarUpload(user.getId());
            User u = user;
            u.setLastActivityDate(new java.util.Date());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for calendar upload", ex);
        }
        return ResponseEntity.ok("OK");
    }

    @SuppressWarnings("null")
    @GetMapping(value = "/removeTermin")
    public void removeTermin(@RequestParam long terminid) {
        User user = UserUtil.getUser();
        if (user == null) {
            return;
        }
        List<Kalender> kalenderList = kalenderRepository.findAllByUserId(user.getId());

        if (kalenderList == null || kalenderList.size() == 0) {
            return;
        }
        if (kalenderList.size() > 1) {
            logger.error("User has more than one calendar, this should not happen!");
        }
        Kalender kalender = kalenderList.get(0);

        if (kalender == null) {
            return;
        }
        KalenderTermin termin = kalenderTerminRepository.findById(terminid).orElse(null);
        if (termin == null) {
            return;
        }
        if (kalender.getTermine().stream().noneMatch(t -> t.id == terminid)) {
            List<TauschTermin> tauschTermine = tauschTerminRepository.findTauschTerminByUserid(user.getId());
            TauschTermin tauschTerminToRemove = null;
            KalenderTermin terminToRemove = null;
            for (TauschTermin tauschTermin : tauschTermine) {
                for (KalenderTermin t : tauschTermin.getGesucht()) {
                    if (t.getId() == terminid) {
                        terminToRemove = t;
                        tauschTerminToRemove = tauschTermin;
                        break;
                    }
                }
                if (terminToRemove != null) {
                    tauschTerminToRemove.getGesucht().remove(terminToRemove);
                    // save
                    tauschTerminRepository.save(tauschTerminToRemove);
                    if (tauschTerminToRemove.getGesucht().size() == 0) {
                        tauschTerminRepository.delete(tauschTerminToRemove);
                    }
                    logger.info(String.format("User %s removed a single Offer", user.getHsMail()));
                    try {
                        auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.OFFER_DELETE,
                                "Removed single offer linked to a calendar entry");
                        userMetricsService.recordActivity(user.getId());
                    } catch (Exception ex) {
                        logger.warn("Failed to record audit/metrics for offer delete", ex);
                    }
                    break;
                }
            }
            return;

        }

        kalender.getTermine().remove(termin);
        kalenderRepository.save(kalender);
        kalenderTerminRepository.delete(termin);
        logger.info(String.format("User %s removed a calendar entry", user.getHsMail()));
        try {
            auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_DELETE,
                    "Deleted calendar entry");
            userMetricsService.recordActivity(user.getId());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for calendar delete", ex);
        }
    }

    @GetMapping(value = "/myKalender", produces = "application/json")
    public ResponseEntity<MyKalenderResponseDTO> getKalender(@RequestParam(required = false) String terminid) {
        long processingTime = System.currentTimeMillis();
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Kalender> kalenderListResult = kalenderRepository.findAllByUserId(user.getId());
        if (kalenderListResult == null || kalenderListResult.size() > 1) {
            logger.error("User has more than one calendar, this should not happen!");
            return ResponseEntity.badRequest().build();
        }

        if (kalenderListResult.size() == 0) {
            return ResponseEntity.badRequest().build();
        }

        Kalender kalender = kalenderListResult.get(0);

        if (kalender == null) {
            return ResponseEntity.badRequest().build();
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

        List<KalenderTermin> termina = null;
        termina = kalender.getTermine().stream().filter(t -> t.getType() != KalenderTerminType.V)
                .toList();

        team.boerse.tauschboerse.settings.SystemSettings settings = systemSettingsService.getOrCreateSettings();
        boolean isPooledMode = settings.getMode() == team.boerse.tauschboerse.settings.SystemMode.POOLED_3CYCLE;

        boolean overwiew = terminid == null;
        String realTitle = "";
        String termincopy = terminid;
        String[] starts = { "08:15", "10:00", "11:45", "14:15", "16:00", "17:45", "19:30" };
        String[] ends = { "09:45", "11:30", "13:15", "15:45", "17:30", "19:15", "21:00" };

        for (KalenderTermin ter : termina) {
            terminid = "" + ter.getId();
            if (termincopy != null && termincopy.equals(terminid)) {
                realTitle = ter.getName();
            }
            if (terminid != null) {

                KalenderTermin usersTermin = kalenderTerminRepository.findById(Long.parseLong(terminid))
                        .orElse(null);
                if (usersTermin == null) {
                    return ResponseEntity.badRequest().build();
                }
                String title = usersTermin.getName().split("\\(")[0];
                c.setTime(usersTermin.getStart());
                String userstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE));
                c.setTime(usersTermin.getEnd());
                int userday = c.get(Calendar.DAY_OF_WEEK) - 2;

                if (!isPooledMode && (termincopy == null || termincopy.equals(terminid))) {
                    List<TauschTermin> fremde = tauschTerminRepository
                            .findByAngebot_TypeAndAngebot_NameStartingWithAndUseridNot(
                                    usersTermin.getType(), title.trim(), user.getId());
                    fremde.sort((a, b) -> Integer.compare(a.getGesucht() != null ? a.getGesucht().size() : 0,
                            b.getGesucht() != null ? b.getGesucht().size() : 0));

                    for (TauschTermin termin : fremde) {
                        KalenderTermin angebot = termin.getAngebot();
                        if (angebot == null)
                            continue;
                        if (termin.getGesucht() == null)
                            continue;

                        Long mySg = user.getStudiengang() != null ? user.getStudiengang().getId() : null;
                        User partner = userRepository.findById(termin.getUserid()).orElse(null);
                        Long partnerSg = partner != null && partner.getStudiengang() != null
                                ? partner.getStudiengang().getId()
                                : null;

                        String myTerminBase = title.trim();
                        if (mySg != null && notSharedService.isNotShared(myTerminBase, mySg)) {
                            if (!Objects.equals(mySg, partnerSg))
                                continue;
                        }
                        String angebotBase = angebot.getName() != null ? angebot.getName().split("\\(")[0].trim() : "";
                        if (partnerSg != null && notSharedService.isNotShared(angebotBase, partnerSg)) {
                            if (!Objects.equals(mySg, partnerSg))
                                continue;
                        }
                        for (KalenderTermin ge : termin.getGesucht()) {
                            c.setTime(ge.getStart());
                            String start = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            c.setTime(ge.getEnd());
                            int gday = c.get(Calendar.DAY_OF_WEEK) - 2;
                            if (userstart.equals(start) && gday == userday) {
                                c.setTime(angebot.getStart());
                                String angebotstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE));
                                c.setTime(angebot.getEnd());
                                String angebotend = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE));

                                KalenderTerminDTO terminDTO = new KalenderTerminDTO(angebot.getName(), "OFFER",
                                        angebot.getType().getColorCode(), angebotstart, angebotend, angebot.id);
                                c.setTime(angebot.getStart());
                                int theday = c.get(Calendar.DAY_OF_WEEK) - 2;

                                boolean slotBelegt = false;
                                for (KalenderTerminDTO check : kalenderList.get(theday)) {
                                    if (hasTimeOverlap(check.start(), check.end(), terminDTO.start(),
                                            terminDTO.end())) {
                                        slotBelegt = true;
                                        break;
                                    }
                                }
                                if (!slotBelegt) {
                                    kalenderList.get(theday).add(terminDTO);
                                }
                                break;
                            }
                        }
                    }
                }

                List<TauschTermin> eigene = tauschTerminRepository
                        .findByUseridAndAngebot_TypeAndAngebot_NameStartingWith(
                                user.getId(), usersTermin.getType(), title.trim());
                for (TauschTermin termin : eigene) {
                    if (termin.getGesucht() == null)
                        continue;
                    for (KalenderTermin ge : termin.getGesucht()) {
                        c.setTime(ge.getStart());
                        if (termincopy != null && !terminid.equals(termincopy)) {
                            continue;
                        }
                        String angebotstart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE));
                        c.setTime(ge.getEnd());
                        String angebotend = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE));

                        String baseTitle = ge.getName() != null ? ge.getName().split("\\(")[0].trim() : "";
                        String computedTitle = baseTitle;
                        try {
                            Long sgId = user.getStudiengang() != null ? user.getStudiengang().getId() : null;
                            List<SuggestedSlot> suggestions = groupSuggestionService
                                    .getSuggestionsForCourse(baseTitle, ge.getType(), sgId);
                            int targetDayIndex = c.get(Calendar.DAY_OF_WEEK) - 2;
                            SuggestedSlot matched = null;
                            for (SuggestedSlot s : suggestions) {
                                if (s.getDayIndex() == targetDayIndex
                                        && angebotstart.equalsIgnoreCase(s.getStart())
                                        && angebotend.equalsIgnoreCase(s.getEnd())) {
                                    matched = s;
                                    break;
                                }
                            }
                            if (matched != null && matched.getGroup() != null && !matched.getGroup().isBlank()) {
                                String typeShort = ge.getType().name();
                                computedTitle = baseTitle + " (" + typeShort + "-" + matched.getGroup() + ")";
                            }
                        } catch (Exception ignore) {
                        }

                        KalenderTerminDTO terminDTO = new KalenderTerminDTO(computedTitle, "ANGEFRAGT",
                                ge.getType().getColorCode(), angebotstart, angebotend, ge.id);

                        c.setTime(ge.getStart());
                        int theday = c.get(Calendar.DAY_OF_WEEK) - 2;

                        boolean hasOverlap = false;
                        for (KalenderTermin origTermin : kalender.getTermine()) {
                            c.setTime(origTermin.getStart());
                            String origStart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            c.setTime(origTermin.getEnd());
                            String origEnd = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            int origDay = c.get(Calendar.DAY_OF_WEEK) - 2;

                            if (origDay == theday && hasTimeOverlap(origStart, origEnd, angebotstart, angebotend)) {
                                hasOverlap = true;
                                break;
                            }
                        }

                        if (!hasOverlap) {
                            kalenderList.get(theday).add(terminDTO);
                        }
                    }
                }

            }
        }
        if (!overwiew) {
            try {
                KalenderTermin selected = null;
                try {
                    if (termincopy != null) {
                        selected = kalenderTerminRepository.findById(Long.parseLong(termincopy)).orElse(null);
                    }
                } catch (Exception ignore) {
                }
                if (selected != null && selected.getType() != KalenderTerminType.V) {
                    String base = selected.getName() != null ? selected.getName().split("\\(")[0].trim() : "";
                    Long sgId = user.getStudiengang() != null ? user.getStudiengang().getId() : null;
                    List<SuggestedSlot> suggestions = groupSuggestionService
                            .getSuggestionsForCourse(base, selected.getType(), sgId);

                    for (SuggestedSlot s : suggestions) {
                        int theday = s.getDayIndex();
                        if (theday < 0 || theday >= kalenderList.size())
                            continue;
                        List<KalenderTerminDTO> day = kalenderList.get(theday);

                        boolean hasOverlap = false;
                        for (KalenderTermin origTermin : kalender.getTermine()) {
                            c.setTime(origTermin.getStart());
                            String origStart = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            c.setTime(origTermin.getEnd());
                            String origEnd = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                                    c.get(Calendar.MINUTE));
                            int origDay = c.get(Calendar.DAY_OF_WEEK) - 2;

                            if (origDay == theday && hasTimeOverlap(origStart, origEnd, s.getStart(), s.getEnd())) {
                                hasOverlap = true;
                                break;
                            }
                        }

                        if (!hasOverlap) {
                            for (KalenderTerminDTO existingDTO : day) {
                                if (hasTimeOverlap(existingDTO.start(), existingDTO.end(), s.getStart(), s.getEnd())) {
                                    hasOverlap = true;
                                    break;
                                }
                            }
                        }

                        if (hasOverlap)
                            continue;

                        String colorGhost = selected.getType().getColorCode();
                        String typeShort = selected.getType().name();
                        String group = (s.getGroup() != null && !s.getGroup().isBlank()) ? s.getGroup() : "?";
                        String title = base + " (" + typeShort + "-" + group + ")";
                        day.add(new KalenderTerminDTO(title, "VORSCHLAG", colorGhost, s.getStart(), s.getEnd(), -1));
                    }
                }
                for (int i = 0; i != 5; i++) {
                    List<KalenderTerminDTO> day = kalenderList.get(i);
                    for (int j = 0; j != 7; j++) {
                        boolean hasOverlap = false;
                        for (KalenderTerminDTO termin : day) {
                            if (hasTimeOverlap(termin.start(), termin.end(), starts[j], ends[j])) {
                                hasOverlap = true;
                                break;
                            }
                        }
                        if (!hasOverlap) {
                            String colorcodelightblue = "rgba(227, 227, 227, 0.4)";
                            day.add(new KalenderTerminDTO(realTitle.split("\\(")[0], "",
                                    colorcodelightblue, starts[j],
                                    ends[j], -1));
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to attach suggestions: {}", ex.getMessage());
            }
        }

        MyKalenderResponseDTO response = new MyKalenderResponseDTO(
                kalenderList,
                settings.getMode(),
                settings.getScheduleType(),
                settings.getIntervalHours(),
                settings.getDailyTime(),
                settings.getNextRunAt());

        processingTime = System.currentTimeMillis() - processingTime;

        try {
            auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.CALENDAR_VIEW,
                    "Viewed calendar");
            userMetricsService.recordActivity(user.getId());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for calendar view", ex);
        }

        return ResponseEntity.ok(response);
    }

    record KalenderTerminDTO(String title, String subtext, String color, String start, String end, long offerid) {
    }

    record MyKalenderResponseDTO(
            List<List<KalenderTerminDTO>> calendar,
            team.boerse.tauschboerse.settings.SystemMode systemMode,
            team.boerse.tauschboerse.settings.ScheduleType scheduleType,
            Integer intervalHours,
            java.time.LocalTime dailyTime,
            java.time.Instant nextRunAt) {
    }

    private KalenderTermin getTermin(net.fortuna.ical4j.model.Component component) throws ParseException {
        KalenderTermin termin = new KalenderTermin();
        String summary = component.getProperty("SUMMARY") != null ? component.getProperty("SUMMARY").getValue()
                : "Unbekannt";
        termin.setName(summary);
        String description = component.getProperty("DESCRIPTION") != null
                ? component.getProperty("DESCRIPTION").getValue()
                : "";
        termin.setDescription(description);
        String location = component.getProperty("LOCATION") != null ? component.getProperty("LOCATION").getValue()
                : "";
        termin.setLocation(location);
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

    private boolean hasTimeOverlap(String start1, String end1, String start2, String end2) {
        int start1Minutes = timeToMinutes(start1);
        int end1Minutes = timeToMinutes(end1);
        int start2Minutes = timeToMinutes(start2);
        int end2Minutes = timeToMinutes(end2);

        return start1Minutes < end2Minutes && start2Minutes < end1Minutes;
    }

    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

}
