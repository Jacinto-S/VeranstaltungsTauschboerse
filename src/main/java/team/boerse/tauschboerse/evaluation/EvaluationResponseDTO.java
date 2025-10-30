package team.boerse.tauschboerse.evaluation;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for submitting and retrieving evaluation responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationResponseDTO {

    private Long id;
    private Boolean submitted;
    private String submittedAt;

    private Map<String, Object> answers;

    public static EvaluationResponseDTO fromEntity(EvaluationResponse response) {
        if (response == null) {
            return null;
        }

        EvaluationResponseDTO dto = new EvaluationResponseDTO();
        dto.setId(response.getId());
        dto.setSubmitted(response.getSubmitted());
        dto.setSubmittedAt(response.getSubmittedAt() != null ? response.getSubmittedAt().toString() : null);

        Map<String, Object> answers = new java.util.HashMap<>();
        answers.put("semesterCount", response.getSemesterCount());
        answers.put("hearAbout", parseJsonArray(response.getHearAbout()));
        answers.put("foundOutside", response.getFoundOutside());
        answers.put("deletedOffers", response.getDeletedOffers());
        answers.put("easeOfUse", response.getEaseOfUse());
        answers.put("intuitiveFunctions", response.getIntuitiveFunctions());
        answers.put("loginReliability", response.getLoginReliability());
        answers.put("emailSatisfaction", response.getEmailSatisfaction());
        answers.put("roundExchangeUnderstanding", response.getRoundExchangeUnderstanding());
        answers.put("roundExchangeAdvantage", response.getRoundExchangeAdvantage());
        answers.put("tradeOffPreference", response.getTradeOffPreference());
        answers.put("overallSatisfaction", response.getOverallSatisfaction());
        answers.put("wouldRecommend", response.getWouldRecommend());
        answers.put("wouldUseAgain", response.getWouldUseAgain());
        answers.put("frustrations", response.getFrustrations());
        answers.put("suggestions", response.getSuggestions());

        dto.setAnswers(answers);
        return dto;
    }

    private static Object parseJsonArray(String jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty()) {
            return null;
        }
        try {
            String cleaned = jsonArray.replaceAll("[\\[\\]\"]", "").trim();
            if (cleaned.isEmpty()) {
                return new String[0];
            }
            return cleaned.split(",\\s*");
        } catch (Exception e) {
            return jsonArray;
        }
    }
}
