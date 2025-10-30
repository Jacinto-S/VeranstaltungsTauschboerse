package team.boerse.tauschboerse.evaluation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import team.boerse.tauschboerse.UserUtil;

@RestController
@RequestMapping("/api/admin/evaluation")
public class AdminEvaluationController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEvaluationController.class);

    @Autowired
    private EvaluationService evaluationService;

    @GetMapping("/semesters")
    public ResponseEntity<?> getSemesters(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }

        try {
            List<String> semesterIds = evaluationService.getAllSemesterIds();
            String currentSemester = evaluationService.getCurrentSemesterId();

            List<Map<String, Object>> semesters = new java.util.ArrayList<>();

            if (!semesterIds.contains(currentSemester)) {
                Map<String, Object> current = new HashMap<>();
                current.put("id", currentSemester);
                current.put("name", formatSemesterName(currentSemester));
                current.put("active", true);
                semesters.add(current);
            }

            for (String semId : semesterIds) {
                Map<String, Object> sem = new HashMap<>();
                sem.put("id", semId);
                sem.put("name", formatSemesterName(semId));
                sem.put("active", semId.equals(currentSemester));
                semesters.add(sem);
            }

            return ResponseEntity.ok(semesters);

        } catch (Exception e) {
            logger.error("Error fetching semesters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch semesters"));
        }
    }

    @GetMapping("/statistics/{semesterId}")
    public ResponseEntity<?> getStatistics(@PathVariable String semesterId, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }

        try {
            Map<String, Object> statistics = evaluationService.getStatistics(semesterId);
            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            logger.error("Error fetching statistics for semester {}", semesterId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch statistics"));
        }
    }

    @GetMapping("/results/{semesterId}")
    public ResponseEntity<?> getResults(@PathVariable String semesterId, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }

        try {
            Map<String, Object> results = evaluationService.getResults(semesterId);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Error fetching results for semester {}", semesterId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch results"));
        }
    }

    @GetMapping("/export/{semesterId}")
    public ResponseEntity<?> exportResults(@PathVariable String semesterId, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        try {
            String csv = evaluationService.exportResultsAsCSV(semesterId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment",
                    "evaluation_" + semesterId + ".csv");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csv);

        } catch (Exception e) {
            logger.error("Error exporting results for semester {}", semesterId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to export results");
        }
    }

    private boolean isAdmin(HttpSession session) {
        return UserUtil.getUser().getIsAdmin();
    }

    private String formatSemesterName(String semesterId) {
        if (semesterId == null)
            return "";

        if (semesterId.startsWith("ws")) {
            return "Wintersemester " + semesterId.substring(2);
        } else if (semesterId.startsWith("ss")) {
            return "Sommersemester " + semesterId.substring(2);
        }

        return semesterId;
    }
}
