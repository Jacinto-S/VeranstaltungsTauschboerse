package team.boerse.tauschboerse;

import lombok.Getter;

@Getter
public enum KalenderTerminType {

    UNKNOWN("Unbekannt", "Unbekannt", "#000000"),
    V("Vorlesung", "Vorlesung", "#7F90EA"),
    P("Praktikum", "Praktikum", "#f4a460"),
    S("Seminar", "Seminar", "#556b2f"),
    U("Übung", "Übung", "#32cd32"),
    T("Tutorium", "Tutorium", "#919191ff");

    private String shortForm;
    private String longForm;
    private String colorCode;

    KalenderTerminType(String shortForm, String longForm, String colorCode) {
        this.shortForm = shortForm;
        this.longForm = longForm;
        this.colorCode = colorCode;
    }
}
