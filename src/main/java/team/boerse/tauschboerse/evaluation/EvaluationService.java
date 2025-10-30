package team.boerse.tauschboerse.evaluation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.boerse.tauschboerse.audit.SemesterUtil;

@Service
public class EvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

    @Autowired
    private EvaluationResponseRepository responseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public String getCurrentSemesterId() {
        return SemesterUtil.getCurrentSemester().toLowerCase();
    }

    @Transactional
    public EvaluationResponse getOrCreateResponse(Long userId, String semesterId) {
        Optional<EvaluationResponse> existing = responseRepository.findByUserIdAndSemesterId(userId, semesterId);

        if (existing.isPresent()) {
            return existing.get();
        }

        EvaluationResponse response = new EvaluationResponse(userId, semesterId);
        return responseRepository.save(response);
    }

    public Optional<EvaluationResponseDTO> getMyResponse(Long userId, String semesterId) {
        Optional<EvaluationResponse> response = responseRepository.findByUserIdAndSemesterId(userId, semesterId);
        return response.map(EvaluationResponseDTO::fromEntity);
    }

    @Transactional
    public EvaluationResponseDTO submitResponse(Long userId, String semesterId, Map<String, Object> answers) {
        EvaluationResponse response = getOrCreateResponse(userId, semesterId);

        updateResponseFields(response, answers);
        response.submit();

        EvaluationResponse saved = responseRepository.save(response);
        logger.info("User {} submitted evaluation for semester {}", userId, semesterId);

        return EvaluationResponseDTO.fromEntity(saved);
    }

    private void updateResponseFields(EvaluationResponse response, Map<String, Object> answers) {
        response.setSemesterCount(getStringValue(answers, "semesterCount"));
        response.setHearAbout(convertToJsonArray(answers.get("hearAbout")));
        response.setFoundOutside(getStringValue(answers, "foundOutside"));
        response.setDeletedOffers(getStringValue(answers, "deletedOffers"));

        response.setEaseOfUse(getIntegerValue(answers, "easeOfUse"));
        response.setIntuitiveFunctions(getIntegerValue(answers, "intuitiveFunctions"));
        response.setLoginReliability(getIntegerValue(answers, "loginReliability"));
        response.setEmailSatisfaction(getStringValue(answers, "emailSatisfaction"));

        response.setRoundExchangeUnderstanding(getIntegerValue(answers, "roundExchangeUnderstanding"));
        response.setRoundExchangeAdvantage(getIntegerValue(answers, "roundExchangeAdvantage"));
        response.setTradeOffPreference(getStringValue(answers, "tradeOffPreference"));

        response.setOverallSatisfaction(getIntegerValue(answers, "overallSatisfaction"));
        response.setWouldRecommend(getStringValue(answers, "wouldRecommend"));
        response.setWouldUseAgain(getStringValue(answers, "wouldUseAgain"));

        response.setFrustrations(getStringValue(answers, "frustrations"));
        response.setSuggestions(getStringValue(answers, "suggestions"));

        response.touch();
    }

    public Map<String, Object> getStatistics(String semesterId) {
        List<EvaluationResponse> allResponses = responseRepository.findBySemesterId(semesterId);
        List<EvaluationResponse> submittedResponses = responseRepository.findBySemesterIdAndSubmittedTrue(semesterId);

        long totalResponses = allResponses.size();
        long completeResponses = submittedResponses.stream().filter(EvaluationResponse::isComplete).count();
        long partialResponses = totalResponses - completeResponses;

        String responseRate = "-";

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalResponses", totalResponses);
        stats.put("completeResponses", completeResponses);
        stats.put("partialResponses", partialResponses);
        stats.put("responseRate", responseRate);

        return stats;
    }

    public Map<String, Object> getResults(String semesterId) {
        List<EvaluationResponse> responses = responseRepository.findBySemesterIdAndSubmittedTrue(semesterId);

        Map<String, Object> results = new HashMap<>();
        results.put("general", aggregateGeneralQuestions(responses));
        results.put("usability", aggregateUsabilityQuestions(responses));
        results.put("roundexchange", aggregateRoundExchangeQuestions(responses));
        results.put("satisfaction", aggregateSatisfactionQuestions(responses));
        results.put("feedback", aggregateFeedbackQuestions(responses));

        return results;
    }

    private List<Map<String, Object>> aggregateGeneralQuestions(List<EvaluationResponse> responses) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        results.add(aggregateSelectQuestion(
                "In wie vielen Semestern hast du die Tauschbörse bis jetzt verwendet?",
                responses.stream().map(EvaluationResponse::getSemesterCount).collect(Collectors.toList()),
                responses.size()));

        Map<String, Integer> hearAboutCounts = new HashMap<>();
        for (EvaluationResponse r : responses) {
            if (r.getHearAbout() != null) {
                String[] items = r.getHearAbout().replaceAll("[\\[\\]\"]", "").split(",");
                for (String item : items) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) {
                        hearAboutCounts.merge(trimmed, 1, Integer::sum);
                    }
                }
            }
        }
        results.add(createQuestionResult(
                "Wie hast du von der Tauschbörse erfahren?",
                "checkbox",
                hearAboutCounts,
                responses.size()));

        results.add(aggregateSelectQuestion(
                "Hast du letztendlich außerhalb der Tauschbörse einen Tauschpartner gefunden?",
                responses.stream().map(EvaluationResponse::getFoundOutside).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateSelectQuestion(
                "Hast du deine Angebote gelöscht oder deaktiviert, wenn du keinen Tauschbedarf mehr hattest?",
                responses.stream().map(EvaluationResponse::getDeletedOffers).collect(Collectors.toList()),
                responses.size()));

        return results;
    }

    private List<Map<String, Object>> aggregateUsabilityQuestions(List<EvaluationResponse> responses) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        results.add(aggregateScaleQuestion(
                "Wie leicht verständlich war die Plattform für dich insgesamt?",
                responses.stream().map(EvaluationResponse::getEaseOfUse).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateScaleQuestion(
                "Wie intuitiv war die Bedienung der Wunsch- und Tauschfunktionen?",
                responses.stream().map(EvaluationResponse::getIntuitiveFunctions).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateScaleQuestion(
                "Wie zuverlässig hat der Login funktioniert?",
                responses.stream().map(EvaluationResponse::getLoginReliability).collect(Collectors.toList()),
                responses.size()));

        Map<String, Integer> emailCounts = new HashMap<>();
        for (EvaluationResponse r : responses) {
            String value = r.getEmailSatisfaction();
            if (value != null) {
                emailCounts.merge(value, 1, Integer::sum);
            }
        }
        results.add(createQuestionResult(
                "Wie zufrieden warst du mit der E-Mail-Benachrichtigung?",
                "scale_1_5",
                emailCounts,
                responses.size()));

        return results;
    }

    private List<Map<String, Object>> aggregateRoundExchangeQuestions(List<EvaluationResponse> responses) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        results.add(aggregateScaleQuestion(
                "Wie verständlich findest du das Prinzip des Rundentauschs?",
                responses.stream().map(EvaluationResponse::getRoundExchangeUnderstanding).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateScaleQuestion(
                "Wie vorteilhaft schätzt du dieses Verfahren im Vergleich zum bisherigen Soforttausch ein?",
                responses.stream().map(EvaluationResponse::getRoundExchangeAdvantage).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateSelectQuestion(
                "Wie empfindest du den Trade-off zwischen Soforttausch und Sammelrunden?",
                responses.stream().map(EvaluationResponse::getTradeOffPreference).collect(Collectors.toList()),
                responses.size()));

        return results;
    }

    private List<Map<String, Object>> aggregateSatisfactionQuestions(List<EvaluationResponse> responses) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        results.add(aggregateScaleQuestion(
                "Wie zufrieden bist du insgesamt mit der Tauschbörse?",
                responses.stream().map(EvaluationResponse::getOverallSatisfaction).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateSelectQuestion(
                "Würdest du die Plattform anderen Studierenden empfehlen?",
                responses.stream().map(EvaluationResponse::getWouldRecommend).collect(Collectors.toList()),
                responses.size()));

        results.add(aggregateSelectQuestion(
                "Würdest du sie wieder verwenden?",
                responses.stream().map(EvaluationResponse::getWouldUseAgain).collect(Collectors.toList()),
                responses.size()));

        return results;
    }

    private List<Map<String, Object>> aggregateFeedbackQuestions(List<EvaluationResponse> responses) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        Map<String, Object> frustrations = new HashMap<>();
        frustrations.put("questionText", "Was hat dich am meisten gestört oder verwirrt?");
        frustrations.put("responses", responses.stream()
                .map(EvaluationResponse::getFrustrations)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()));
        results.add(frustrations);

        Map<String, Object> suggestions = new HashMap<>();
        suggestions.put("questionText", "Hast du weitere Vorschläge oder Wünsche für kommende Semester?");
        suggestions.put("responses", responses.stream()
                .map(EvaluationResponse::getSuggestions)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()));
        results.add(suggestions);

        return results;
    }

    private Map<String, Object> aggregateScaleQuestion(String questionText, List<Integer> values, int total) {
        Map<String, Integer> counts = new HashMap<>();
        double sum = 0;
        int validCount = 0;

        for (Integer value : values) {
            if (value != null) {
                counts.merge(String.valueOf(value), 1, Integer::sum);
                sum += value;
                validCount++;
            }
        }

        double average = validCount > 0 ? sum / validCount : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("questionText", questionText);
        result.put("type", "scale_1_5");
        result.put("answers", counts);
        result.put("average", average);
        result.put("totalAnswers", validCount);

        return result;
    }

    private Map<String, Object> aggregateSelectQuestion(String questionText, List<String> values, int total) {
        Map<String, Integer> counts = new HashMap<>();
        int validCount = 0;

        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                counts.merge(value, 1, Integer::sum);
                validCount++;
            }
        }

        return createQuestionResult(questionText, "select", counts, validCount);
    }

    private Map<String, Object> createQuestionResult(String questionText, String type,
            Map<String, Integer> answers, int total) {
        Map<String, Object> result = new HashMap<>();
        result.put("questionText", questionText);
        result.put("type", type);
        result.put("answers", answers);
        result.put("totalAnswers", total);
        return result;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String convertToJsonArray(Object value) {
        if (value == null)
            return null;

        try {
            if (value instanceof String) {
                return (String) value;
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            logger.error("Error converting to JSON array", e);
            return value.toString();
        }
    }

    public List<String> getAllSemesterIds() {
        return responseRepository.findDistinctSemesterIds();
    }

    public String exportResultsAsCSV(String semesterId) {
        List<EvaluationResponse> responses = responseRepository.findBySemesterIdAndSubmittedTrue(semesterId);

        StringBuilder csv = new StringBuilder();

        csv.append("User ID,Semester Count,Hear About,Found Outside,Deleted Offers,");
        csv.append("Ease of Use,Intuitive Functions,Login Reliability,Email Satisfaction,");
        csv.append("Round Exchange Understanding,Round Exchange Advantage,Trade-off Preference,");
        csv.append("Overall Satisfaction,Would Recommend,Would Use Again,");
        csv.append("Frustrations,Suggestions,Submitted At\n");

        for (EvaluationResponse r : responses) {
            csv.append(csvEscape(r.getUserId().toString())).append(",");
            csv.append(csvEscape(r.getSemesterCount())).append(",");
            csv.append(csvEscape(r.getHearAbout())).append(",");
            csv.append(csvEscape(r.getFoundOutside())).append(",");
            csv.append(csvEscape(r.getDeletedOffers())).append(",");
            csv.append(csvEscape(r.getEaseOfUse())).append(",");
            csv.append(csvEscape(r.getIntuitiveFunctions())).append(",");
            csv.append(csvEscape(r.getLoginReliability())).append(",");
            csv.append(csvEscape(r.getEmailSatisfaction())).append(",");
            csv.append(csvEscape(r.getRoundExchangeUnderstanding())).append(",");
            csv.append(csvEscape(r.getRoundExchangeAdvantage())).append(",");
            csv.append(csvEscape(r.getTradeOffPreference())).append(",");
            csv.append(csvEscape(r.getOverallSatisfaction())).append(",");
            csv.append(csvEscape(r.getWouldRecommend())).append(",");
            csv.append(csvEscape(r.getWouldUseAgain())).append(",");
            csv.append(csvEscape(r.getFrustrations())).append(",");
            csv.append(csvEscape(r.getSuggestions())).append(",");
            csv.append(csvEscape(r.getSubmittedAt())).append("\n");
        }

        return csv.toString();
    }

    private String csvEscape(Object value) {
        if (value == null)
            return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
