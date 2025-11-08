package team.boerse.tauschboerse.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.RememberMeServices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.UserUtil;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditLog;
import team.boerse.tauschboerse.audit.AuditLogRepository;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.audit.LoginMethod;
import team.boerse.tauschboerse.metrics.UserMetrics;
import team.boerse.tauschboerse.metrics.UserMetricsRepository;
import team.boerse.tauschboerse.feedback.Feedback;
import team.boerse.tauschboerse.feedback.FeedbackRepository;
import team.boerse.tauschboerse.Kalender;
import team.boerse.tauschboerse.KalenderRepository;
import team.boerse.tauschboerse.TauschTermin;
import team.boerse.tauschboerse.TauschTerminRepository;
import team.boerse.tauschboerse.KalenderTermin;
import team.boerse.tauschboerse.KalenderTerminRepository;

import java.util.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final UserMetricsRepository userMetricsRepository;
    private final FeedbackRepository feedbackRepository;
    private final KalenderRepository calendarRepository;
    private final TauschTerminRepository tauschTerminRepository;
    private final KalenderTerminRepository kalenderTerminRepository;
    private final RememberMeServices rememberMeServices;
    private final UserDetailsService userDetailsService;

    private boolean isAdmin() {
        User user = UserUtil.getUser();
        return user != null && Boolean.TRUE.equals(user.getIsAdmin());
    }

    private void requireAdmin() {
        if (!isAdmin()) {
            throw new AdminAccessDeniedException("Access denied");
        }
    }

    @GetMapping("/users/{userId}/impersonate")
    public void impersonateUser(
            @PathVariable Long userId,
            HttpServletRequest request,
            HttpServletResponse response) throws java.io.IOException {
        requireAdmin();

        User admin = UserUtil.getUser();
        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
            return;
        }
        if (Boolean.TRUE.equals(target.getIsBanned())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot impersonate banned user");
            return;
        }

        try {
            String details = String.format("Admin %s (id=%d) impersonates %s (id=%d)",
                    admin.getHsMail(), admin.getId(), target.getHsMail(), target.getId());
            auditService.logEvent(admin.getId(), AuditEventType.WARNING, null, details);
            auditService.logEvent(target.getId(), AuditEventType.WARNING, null,
                    String.format("Impersonation login by admin %s (id=%d)", admin.getHsMail(), admin.getId()));
        } catch (Exception ignore) {
        }

        UserDetails ud = userDetailsService.loadUserByUsername(target.getHsMail());
        var auth = new UsernamePasswordAuthenticationToken(ud, ud.getPassword(), ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            request.getSession(true);
            rememberMeServices.loginSuccess(request, response, auth);
        } catch (Exception ignored) {
        }

        try {
            target.setLastActivityDate(new java.util.Date());
            userRepository.save(target);
        } catch (Exception ignored) {
        }

        response.setStatus(200);
    }

    @GetMapping("/auditlogs")
    public ResponseEntity<PageResponse<AuditLogDTO>> getAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String hsMail,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) LoginMethod loginMethod,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate,
            @RequestParam(defaultValue = "false") boolean includeDebug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireAdmin();

        Pageable pageable = PageRequest.of(page, size);
        Date start = startDate != null ? new Date(startDate) : null;
        Date end = endDate != null ? new Date(endDate) : null;

        if (userId == null && hsMail != null && !hsMail.isBlank()) {
            Optional<User> uOpt = userRepository.findByHsMail(hsMail);
            if (uOpt.isPresent()) {
                userId = uOpt.get().getId();
            } else {
                return ResponseEntity.ok(PageResponse.empty(page, size));
            }
        }

        Page<AuditLog> logs = auditService.getAuditLogsByFilters(
                userId, eventType, semester, loginMethod, start, end, includeDebug, pageable);

        PageResponse<AuditLogDTO> dto = PageResponse.from(logs.map(this::toAuditLogDTO));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/auditlogs/categories")
    public ResponseEntity<List<Map<String, Object>>> getAuditCategories(
            @RequestParam(defaultValue = "false") boolean includeDebug) {
        requireAdmin();
        List<Map<String, Object>> cats = new ArrayList<>();
        for (AuditEventType t : AuditEventType.values()) {
            if (!includeDebug && !t.isVisible())
                continue;
            Map<String, Object> m = new HashMap<>();
            m.put("name", t.name());
            m.put("displayName", t.getDisplayName());
            m.put("visible", t.isVisible());
            cats.add(m);
        }
        return ResponseEntity.ok(cats);
    }

    @GetMapping("/auditlogs/user/{userId}")
    public ResponseEntity<PageResponse<AuditLogDTO>> getUserAuditLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireAdmin();

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditService.getAuditLogsByUser(userId, pageable);

        PageResponse<AuditLogDTO> dto = PageResponse.from(logs.map(this::toAuditLogDTO));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserDTO>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, name = "q") String query) {
        requireAdmin();

        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = (query != null && !query.isBlank())
                ? userRepository.findByHsMailContainingIgnoreCase(query, pageable)
                : userRepository.findAll(pageable);

        if (semester != null && !semester.isBlank()) {
            List<UserMetrics> ms = userMetricsRepository.findByRegistrationSemester(semester);
            Set<Long> allowed = new HashSet<>();
            for (UserMetrics m : ms) {
                if (m.getUser() != null) {
                    allowed.add(m.getUser().getId());
                }
            }
            List<UserDTO> filtered = users.getContent().stream()
                    .filter(u -> allowed.contains(u.getId()))
                    .map(UserDTO::from)
                    .toList();
            PageResponse<UserDTO> response = new PageResponse<>(filtered, page, size, filtered.size(), 1);
            return ResponseEntity.ok(response);
        }
        PageResponse<UserDTO> dto = PageResponse.from(users.map(UserDTO::from));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<Map<String, Object>> banUser(
            @PathVariable Long userId,
            @RequestBody BanRequest banRequest) {
        requireAdmin();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot ban admin user"));
        }

        user.setIsBanned(true);
        user.setBanReason(banRequest.reason());
        userRepository.save(user);

        auditService.logEvent(userId, AuditEventType.USER_BANNED, null,
                "User banned by admin. Reason: " + banRequest.reason());

        return ResponseEntity.ok(Map.of("message", "User banned successfully", "userId", userId));
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<Map<String, Object>> unbanUser(@PathVariable Long userId) {
        requireAdmin();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        user.setIsBanned(false);
        user.setBanReason(null);
        userRepository.save(user);

        auditService.logEvent(userId, AuditEventType.USER_UNBANNED, null, "User unbanned by admin");

        return ResponseEntity.ok(Map.of("message", "User unbanned successfully", "userId", userId));
    }

    @GetMapping("/users/{userId}/metrics")
    public ResponseEntity<?> getUserMetrics(@PathVariable Long userId) {
        requireAdmin();
        UserMetrics metrics = userMetricsRepository.findByUserId(userId).orElse(null);

        if (metrics == null) {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            User user = uOpt.get();
            metrics = new UserMetrics(user, null);
            try {
                metrics = userMetricsRepository.save(metrics);
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to create user metrics"));
            }
        }

        User userEntity = metrics.getUser();
        SafeUser safeUser = null;
        if (userEntity != null) {
            safeUser = new SafeUser(
                    userEntity.getId(),
                    userEntity.getHsMail(),
                    userEntity.getPrivateMail(),
                    userEntity.getIsAdmin(),
                    userEntity.getIsBanned(),
                    userEntity.getRegistrationDate(),
                    userEntity.getLastActivityDate());
        }

        UserMetricsResponse resp = UserMetricsResponse.from(metrics, safeUser);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/users/metrics/all")
    public ResponseEntity<List<UserMetrics>> getAllUserMetrics(
            @RequestParam(required = false) String semester) {
        requireAdmin();

        List<UserMetrics> metrics;
        if (semester != null && !semester.isEmpty()) {
            metrics = userMetricsRepository.findByRegistrationSemester(semester);
        } else {
            metrics = userMetricsRepository.findAll();
        }
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/statistics/overview")
    public ResponseEntity<Map<String, Object>> getStatisticsOverview() {
        requireAdmin();

        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepository.count();
        Long usersWithPasskeys = userMetricsRepository.countByUsesPasskeys(true);
        Long usersWithCalendar = userMetricsRepository.countByHasUploadedCalendar(true);
        Long usersWithOffers = userMetricsRepository.countByHasCreatedOffer(true);
        Long successfulMatches = userMetricsRepository.countByWasSuccessfullyMatched(true);

        stats.put("totalUsers", totalUsers);
        stats.put("usersWithPasskeys", usersWithPasskeys != null ? usersWithPasskeys : 0);
        stats.put("usersWithCalendar", usersWithCalendar != null ? usersWithCalendar : 0);
        stats.put("usersWithOffers", usersWithOffers != null ? usersWithOffers : 0);
        stats.put("successfulMatches", successfulMatches != null ? successfulMatches : 0);

        List<Object[]> usersBySemester = userMetricsRepository.countUsersBySemester();
        stats.put("usersBySemester", usersBySemester);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/statistics/semester/{semester}")
    public ResponseEntity<Map<String, Object>> getSemesterStatistics(@PathVariable String semester) {
        requireAdmin();

        Map<String, Object> stats = new HashMap<>();

        Long totalUsers = userMetricsRepository.countUsersBySemesterTotal(semester);
        Long passkeyUsers = userMetricsRepository.countPasskeyUsersBySemester(semester);
        Long calendarUploads = userMetricsRepository.countCalendarUploadsBySemester(semester);
        Long offerCreators = userMetricsRepository.countOfferCreatorsBySemester(semester);
        Long successfulMatches = userMetricsRepository.countSuccessfulMatchesBySemester(semester);

        stats.put("semester", semester);
        stats.put("totalUsers", totalUsers);
        stats.put("passkeyUsers", passkeyUsers != null ? passkeyUsers : 0);
        stats.put("calendarUploads", calendarUploads != null ? calendarUploads : 0);
        stats.put("offerCreators", offerCreators != null ? offerCreators : 0);
        stats.put("successfulMatches", successfulMatches != null ? successfulMatches : 0);

        List<Object[]> loginMethods = auditLogRepository.countLoginMethodsBySemester(semester);
        stats.put("loginMethods", loginMethods);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/statistics/timeline")
    public ResponseEntity<Map<String, Object>> getTimelineStatistics(
            @RequestParam String semester,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end,
            @RequestParam(required = false, defaultValue = "day") String interval,
            @RequestParam(required = false, defaultValue = "false") boolean includeDebug) {
        requireAdmin();

        Map<String, Object> result = new HashMap<>();

        Date startDate = start != null ? new Date(start) : null;
        Date endDate = end != null ? new Date(end) : null;

        if (startDate == null || endDate == null) {
            List<Object[]> daily = auditLogRepository.countEventsByDateAndSemester(semester, includeDebug);
            if (!daily.isEmpty()) {
                Object[] first = daily.get(0);
                Object[] last = daily.get(daily.size() - 1);
                startDate = startDate == null ? (Date) first[0] : startDate;
                endDate = endDate == null ? (Date) last[0] : endDate;
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();

        java.util.function.Function<List<Object[]>, List<List<Object>>> toPoints = (rows) -> {
            List<List<Object>> points = new ArrayList<>();
            for (Object[] r : rows) {
                if (r.length == 2 && r[0] instanceof Date) {
                    points.add(List.of(((Date) r[0]).getTime(), ((Number) r[1]).longValue()));
                } else if (r.length == 3 && r[0] instanceof Date) {
                    Date day = (Date) r[0];
                    int hour = ((Number) r[1]).intValue();
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(day);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    points.add(List.of(cal.getTimeInMillis(), ((Number) r[2]).longValue()));
                }
            }
            return points;
        };

        boolean hourly = "hour".equalsIgnoreCase(interval) || "hourly".equalsIgnoreCase(interval);

        List<Object[]> all;
        if (hourly && startDate != null && endDate != null) {
            all = auditLogRepository.countEventsByHourAndSemester(semester, startDate, endDate, includeDebug);
        } else if (startDate != null && endDate != null) {
            all = auditLogRepository.countEventsByDateAndSemesterInRange(semester, startDate, endDate, includeDebug);
        } else {
            all = auditLogRepository.countEventsByDateAndSemester(semester, includeDebug);
        }
        series.add(Map.of(
                "key", "pageViews",
                "label", "Seitenaufrufe",
                "points", toPoints.apply(all)));

        List<Object[]> logins;
        List<Object[]> calendarUploads;
        List<Object[]> offersCreated;
        List<Object[]> matches;

        if (hourly && startDate != null && endDate != null) {

            List<Object[]> h1 = auditLogRepository.countEventsByHourAndSemesterForType(semester,
                    AuditEventType.LOGIN_SUCCESS, startDate, endDate);
            List<Object[]> h2 = auditLogRepository.countEventsByHourAndSemesterForType(semester,
                    AuditEventType.CALENDAR_UPLOAD, startDate, endDate);
            List<Object[]> h3 = auditLogRepository.countEventsByHourAndSemesterForType(semester,
                    AuditEventType.OFFER_CREATE, startDate, endDate);
            List<Object[]> h4 = auditLogRepository.countEventsByHourAndSemesterForType(semester,
                    AuditEventType.MATCH_SUCCESS, startDate, endDate);
            logins = h1;
            calendarUploads = h2;
            offersCreated = h3;
            matches = h4;
        } else if (startDate != null && endDate != null) {
            logins = auditLogRepository.countEventsByDateAndSemesterForTypeInRange(semester,
                    AuditEventType.LOGIN_SUCCESS, startDate, endDate);
            calendarUploads = auditLogRepository.countEventsByDateAndSemesterForTypeInRange(semester,
                    AuditEventType.CALENDAR_UPLOAD, startDate, endDate);
            offersCreated = auditLogRepository.countEventsByDateAndSemesterForTypeInRange(semester,
                    AuditEventType.OFFER_CREATE, startDate, endDate);
            matches = auditLogRepository.countEventsByDateAndSemesterForTypeInRange(semester,
                    AuditEventType.MATCH_SUCCESS, startDate, endDate);
        } else {
            logins = auditLogRepository.countEventsByDateAndSemesterForType(semester, AuditEventType.LOGIN_SUCCESS);
            calendarUploads = auditLogRepository.countEventsByDateAndSemesterForType(semester,
                    AuditEventType.CALENDAR_UPLOAD);
            offersCreated = auditLogRepository.countEventsByDateAndSemesterForType(semester,
                    AuditEventType.OFFER_CREATE);
            matches = auditLogRepository.countEventsByDateAndSemesterForType(semester,
                    AuditEventType.MATCH_SUCCESS);
        }

        series.add(Map.of("key", "logins", "label", "Logins", "points", toPoints.apply(logins)));
        series.add(Map.of("key", "calendarUploads", "label", "Kalender-Uploads", "points",
                toPoints.apply(calendarUploads)));
        series.add(
                Map.of("key", "offersCreated", "label", "Angebote erstellt", "points", toPoints.apply(offersCreated)));
        series.add(Map.of("key", "matches", "label", "Vermittlungen", "points", toPoints.apply(matches)));

        result.put("semester", semester);
        result.put("interval", hourly ? "hour" : "day");
        result.put("start", startDate != null ? startDate.getTime() : null);
        result.put("end", endDate != null ? endDate.getTime() : null);
        result.put("series", series);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/statistics/flags")
    public ResponseEntity<Map<String, Object>> getFlagStatistics(@RequestParam(required = false) String semester) {
        requireAdmin();

        Map<String, Object> stats = new HashMap<>();

        Long usesPasskeys = userMetricsRepository.countByUsesPasskeys(true);
        Long hasCalendar = userMetricsRepository.countByHasUploadedCalendar(true);
        Long hasOffers = userMetricsRepository.countByHasCreatedOffer(true);
        Long wasMatched = userMetricsRepository.countByWasSuccessfullyMatched(true);

        stats.put("usesPasskeys", usesPasskeys != null ? usesPasskeys : 0);
        stats.put("hasCalendar", hasCalendar != null ? hasCalendar : 0);
        stats.put("hasOffers", hasOffers != null ? hasOffers : 0);
        stats.put("wasMatched", wasMatched != null ? wasMatched : 0);

        List<Object[]> semesterCounts = userMetricsRepository.countUsersBySemester();
        Map<String, Map<String, Object>> semesterStats = new HashMap<>();
        Map<String, Long> totalUsersBySemester = new HashMap<>();

        for (Object[] row : semesterCounts) {
            String semesterName = (String) row[0];
            Long passkeyUsers = userMetricsRepository.countPasskeyUsersBySemester(semesterName);
            Long calendarUsers = userMetricsRepository.countCalendarUploadsBySemester(semesterName);
            Long offerCreators = userMetricsRepository.countOfferCreatorsBySemester(semesterName);
            Long matchedUsers = userMetricsRepository.countSuccessfulMatchesBySemester(semesterName);
            Long totalUsers = userMetricsRepository.countUsersBySemesterTotal(semesterName);

            Map<String, Object> semesterData = new HashMap<>();
            semesterData.put("usesPasskeys", passkeyUsers != null ? passkeyUsers : 0);
            semesterData.put("hasCalendar", calendarUsers != null ? calendarUsers : 0);
            semesterData.put("hasOffers", offerCreators != null ? offerCreators : 0);
            semesterData.put("wasMatched", matchedUsers != null ? matchedUsers : 0);
            semesterData.put("totalUsers", totalUsers != null ? totalUsers : 0);

            semesterStats.put(semesterName, semesterData);
            totalUsersBySemester.put(semesterName, totalUsers != null ? totalUsers : 0);
        }

        stats.put("bySemester", semesterStats);
        stats.put("totalUsersBySemester", totalUsersBySemester);

        if (semester != null && !semester.isBlank()) {
            Map<String, Object> selected = semesterStats.getOrDefault(semester,
                    Map.of("usesPasskeys", 0, "hasCalendar", 0, "hasOffers", 0, "wasMatched", 0, "totalUsers", 0));
            stats.put("selectedSemester", semester);
            stats.put("selectedSemesterStats", selected);
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/feedback")
    public ResponseEntity<PageResponse<FeedbackDTO>> getFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireAdmin();

        Pageable pageable = PageRequest.of(page, size);
        Page<Feedback> feedbacks = feedbackRepository.findAllByOrderByCreateDateDesc(pageable);

        PageResponse<FeedbackDTO> dto = PageResponse.from(feedbacks.map(this::toFeedbackDTO));
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/feedback/{feedbackId}")
    public ResponseEntity<Map<String, Object>> deleteFeedback(@PathVariable Long feedbackId) {
        requireAdmin();

        Optional<Feedback> fOpt = feedbackRepository.findById(feedbackId);
        if (fOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Feedback not found"));
        }

        feedbackRepository.deleteById(feedbackId);
        User adminUser = UserUtil.getUser();
        if (adminUser != null) {
            auditService.logEvent(adminUser.getId(), AuditEventType.WARNING, null,
                    "Feedback " + feedbackId + " deleted by admin");
        }

        return ResponseEntity.ok(Map.of("message", "Feedback deleted successfully", "feedbackId", feedbackId));
    }

    @PatchMapping("/feedback/{feedbackId}/visibility")
    public ResponseEntity<Map<String, Object>> updateFeedbackVisibility(
            @PathVariable Long feedbackId,
            @RequestBody VisibilityRequest request) {
        requireAdmin();

        Optional<Feedback> fOpt = feedbackRepository.findById(feedbackId);
        if (fOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Feedback not found"));
        }

        Feedback feedback = fOpt.get();
        feedback.setPublic(request.isPublic());
        feedbackRepository.save(feedback);

        User adminUser = UserUtil.getUser();
        if (adminUser != null) {
            auditService.logEvent(adminUser.getId(), AuditEventType.WARNING, null,
                    "Feedback " + feedbackId + " visibility changed to " + request.isPublic() + " by admin");
        }

        return ResponseEntity.ok(Map.of(
                "message", "Feedback visibility updated successfully",
                "feedbackId", feedbackId,
                "isPublic", feedback.isPublic()));
    }

    @DeleteMapping("/settings/calendars/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAllCalendars() {
        requireAdmin();

        try {
            List<TauschTermin> allTauschTermine = tauschTerminRepository.findAll();
            if (!allTauschTermine.isEmpty()) {
                tauschTerminRepository.deleteAll(allTauschTermine);
            }

            List<Kalender> allCalendars = calendarRepository.findAll();
            for (Kalender cal : allCalendars) {
                if (cal != null && cal.getTermine() != null && !cal.getTermine().isEmpty()) {
                    cal.getTermine().clear();
                    calendarRepository.save(cal);
                }
            }

            List<KalenderTermin> allKalenderTermine = kalenderTerminRepository.findAll();
            if (!allKalenderTermine.isEmpty()) {
                kalenderTerminRepository.deleteAll(allKalenderTermine);
            }

            long deletedCount = 0;
            if (!allCalendars.isEmpty()) {
                deletedCount = allCalendars.size();
                calendarRepository.deleteAll(allCalendars);
            }

            User adminUser = UserUtil.getUser();
            if (adminUser != null) {
                auditService.logEvent(adminUser.getId(), AuditEventType.WARNING, null,
                        String.format("Admin deleted all calendars: %d calendars removed", deletedCount));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "All calendars deleted successfully",
                    "count", deletedCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to delete calendars",
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/settings/flags/reset-all")
    public ResponseEntity<Map<String, Object>> resetAllFlags() {
        requireAdmin();

        try {
            List<UserMetrics> allMetrics = userMetricsRepository.findAll();

            long resetCount = 0;
            for (UserMetrics metrics : allMetrics) {
                boolean changed = false;

                if (Boolean.TRUE.equals(metrics.getHasUploadedCalendar())) {
                    metrics.setHasUploadedCalendar(false);
                    changed = true;
                }

                if (Boolean.TRUE.equals(metrics.getHasCreatedOffer())) {
                    metrics.setHasCreatedOffer(false);
                    changed = true;
                }

                if (changed) {
                    userMetricsRepository.save(metrics);
                    resetCount++;
                }
            }

            User adminUser = UserUtil.getUser();
            if (adminUser != null) {
                auditService.logEvent(adminUser.getId(), AuditEventType.WARNING, null,
                        String.format("Admin reset all flags (calendar & offers) for %d users", resetCount));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "All flags reset successfully",
                    "count", resetCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to reset flags",
                    "message", e.getMessage()));
        }
    }

    public record BanRequest(String reason) {
    }

    public record VisibilityRequest(boolean isPublic) {
    }

    public static class AdminAccessDeniedException extends RuntimeException {
        public AdminAccessDeniedException(String message) {
            super(message);
        }
    }

    private AuditLogDTO toAuditLogDTO(AuditLog log) {
        String hsMail = null;
        if (log.getUserId() != null) {
            Optional<User> uOpt = userRepository.findById(log.getUserId());
            if (uOpt.isPresent())
                hsMail = uOpt.get().getHsMail();
        }
        return new AuditLogDTO(
                log.getId(),
                hsMail,
                log.getEventType(),
                log.getLoginMethod(),
                log.getEventDetails(),
                log.getTimestamp(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getSemester());
    }

    private FeedbackDTO toFeedbackDTO(Feedback feedback) {
        String creatorMail = null;
        Optional<User> uOpt = userRepository.findById(feedback.getCreator());
        if (uOpt.isPresent()) {
            creatorMail = uOpt.get().getHsMail();
        }
        return new FeedbackDTO(
                feedback.getId(),
                creatorMail,
                feedback.getRating(),
                feedback.isPublic(),
                feedback.getFeedback(),
                feedback.getCreateDate());
    }

    public record AuditLogDTO(
            Long id,
            String hsMail,
            AuditEventType eventType,
            LoginMethod loginMethod,
            String eventDetails,
            Date timestamp,
            String ipAddress,
            String userAgent,
            String semester) {
    }

    public record UserDTO(
            Long id,
            String hsMail,
            String privateMail,
            String studiengangShortCode,
            Boolean isAdmin,
            Boolean isBanned) {
        public static UserDTO from(User u) {
            return new UserDTO(u.getId(), u.getHsMail(), u.getPrivateMail(),
                    u.getStudiengang() != null ? u.getStudiengang().getShortCode() : "-",
                    u.getIsAdmin(), u.getIsBanned());
        }
    }

    public record FeedbackDTO(
            Long id,
            String creatorMail,
            Integer rating,
            Boolean isPublic,
            String feedback,
            Date createDate) {
    }

    @Getter
    public static class PageResponse<T> {
        private final List<T> content;
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;

        public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
            this.content = content;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public static <T> PageResponse<T> from(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }

        public static <T> PageResponse<T> empty(int page, int size) {
            return new PageResponse<>(Collections.emptyList(), page, size, 0, 0);
        }

    }

    public record SafeUser(
            Long id,
            String hsMail,
            String privateMail,
            Boolean isAdmin,
            Boolean isBanned,
            Date registrationDate,
            Date lastActivityDate) {
    }

    public static record UserMetricsResponse(
            Long id,
            SafeUser user,
            Boolean usesPasskeys,
            Boolean hasUploadedCalendar,
            Boolean hasCreatedOffer,
            Boolean wasSuccessfullyMatched,
            Integer totalLogins,
            Integer totalCalendarUploads,
            Integer totalOffersCreated,
            Integer totalOffersAccepted,
            Integer totalMatches,
            Integer totalLoginAttempts,
            Date firstPasskeyUse,
            Date firstCalendarUpload,
            Date firstOfferCreated,
            Date firstSuccessfulMatch,
            Date lastLogin,
            Date lastActivity,
            String registrationSemester,
            Date createdAt,
            Date updatedAt) {

        public static UserMetricsResponse from(UserMetrics m, SafeUser user) {
            return new UserMetricsResponse(
                    m.getId(),
                    user,
                    m.getUsesPasskeys(),
                    m.getHasUploadedCalendar(),
                    m.getHasCreatedOffer(),
                    m.getWasSuccessfullyMatched(),
                    m.getTotalLogins(),
                    m.getTotalCalendarUploads(),
                    m.getTotalOffersCreated(),
                    m.getTotalOffersAccepted(),
                    m.getTotalMatches(),
                    m.getTotalLoginAttempts(),
                    m.getFirstPasskeyUse(),
                    m.getFirstCalendarUpload(),
                    m.getFirstOfferCreated(),
                    m.getFirstSuccessfulMatch(),
                    m.getLastLogin(),
                    m.getLastActivity(),
                    m.getRegistrationSemester(),
                    m.getCreatedAt(),
                    m.getUpdatedAt());
        }
    }

}
