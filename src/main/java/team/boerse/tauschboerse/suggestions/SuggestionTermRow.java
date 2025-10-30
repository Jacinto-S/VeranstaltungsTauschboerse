package team.boerse.tauschboerse.suggestions;

import java.util.Date;

import team.boerse.tauschboerse.KalenderTerminType;

public interface SuggestionTermRow {
    Long getUserId();

    Long getStudiengangId();

    Date getStart();

    Date getEnd();

    String getName();

    KalenderTerminType getType();
}
