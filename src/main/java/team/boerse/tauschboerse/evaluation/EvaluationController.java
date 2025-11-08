package team.boerse.tauschboerse.evaluation;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationController.class);

    @Autowired
    private EvaluationService evaluationService;

    @GetMapping("/my-response")
    public ResponseEntity<?> getMyResponse() {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String currentSemester = evaluationService.getCurrentSemesterId();
        Optional<EvaluationResponseDTO> response = evaluationService.getMyResponse(user.getId(), currentSemester);

        if (response.isPresent()) {
            return ResponseEntity.ok(response.get());
        } else {
            return ResponseEntity.ok(Map.of(
                    "submitted", false,
                    "answers", Map.of()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitResponse(@RequestBody Map<String, Object> requestBody) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            String currentSemester = evaluationService.getCurrentSemesterId();
            EvaluationResponseDTO response = evaluationService.submitResponse(
                    user.getId(),
                    currentSemester,
                    requestBody);

            logger.info("User {} submitted evaluation for semester {}", user.getHsMail(), currentSemester);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error submitting evaluation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit evaluation", "message", e.getMessage()));
        }
    }

}
