package team.boerse.tauschboerse.feedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import team.boerse.tauschboerse.TauschTerminController;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.UserUtil;

@RestController
public class FeedbackController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/feedback")
    public List<Feedback> getFeedbacks(Pageable pageable, @RequestParam(defaultValue = "") String password) {
        if (pageable == null) {
            pageable = Pageable.ofSize(20);
        }

        User user = UserUtil.getUser();
        if (user == null || user.isAdmin() != null && !user.isAdmin()) {
            return List.of();
        }
        return feedbackRepository.findAllByOrderByCreateDateDesc(pageable).getContent();
    }

    record FeedbackDTO(boolean isPublic, String feedback, int rating) {
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> createFeedback(@RequestBody FeedbackDTO feedback) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(401).body("You are not logged in");
        }
        Feedback newFeedback = new Feedback(user.getId(), feedback.isPublic(), feedback.feedback(), feedback.rating());
        feedbackRepository.save(newFeedback);
        return ResponseEntity.ok("Feedback created");
    }

    record FeedbackResponse(long id, String creator, boolean isPublic, String feedback, Date createDate, int rating) {
    }

    @GetMapping("/randomFeedback")
    public ResponseEntity<List<FeedbackResponse>> getRandomFeedback(@RequestParam(defaultValue = "1") int count) {
        if (count < 1) {
            return ResponseEntity.badRequest().body(null);
        }
        if (count > 20) {
            count = 20;
        }
        Page<Feedback> page = feedbackRepository.findAllByIsPublicTrueOrderByCreateDateDesc(Pageable.ofSize(200));
        List<Feedback> feedbacks = page.getContent();
        List<Feedback> randomFeedbacks = new ArrayList<>();

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < feedbacks.size(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes);
        int size = Math.min(indexes.size(), count);
        for (int i = 0; i < size; i++) {
            randomFeedbacks.add(feedbacks.get(indexes.get(i)));
        }

        List<FeedbackResponse> response = randomFeedbacks.stream()
                .map(f -> new FeedbackResponse(f.getId(),
                        TauschTerminController.extractName(
                                userRepository.findById(f.getCreator()).orElse(new User()).getHsMail()),
                        f.isPublic(),
                        f.getFeedback(), f.getCreateDate(), f.getRating()))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/Stats", produces = "text/plain")
    public String getStats() {

        long totalUsers = userRepository.count();

        long count = feedbackRepository.count();
        double avgRating = 0;
        for (Feedback f : feedbackRepository.findAll()) {
            avgRating += f.getRating();
        }
        avgRating /= count;

        StringBuilder table = new StringBuilder();
        table.append("Total Users: " + totalUsers + "\n");
        table.append("Total Feedbacks: " + count + "\n");
        table.append("Average Rating: " + avgRating + "\n");
        return table.toString();

    }

}