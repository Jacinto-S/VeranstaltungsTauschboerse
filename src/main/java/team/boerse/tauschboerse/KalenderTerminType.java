package team.boerse.tauschboerse;

public enum KalenderTerminType {

    UNKNOWN("Unbekannt", "Unbekannt", "#000000"),
    V("Vorlesung", "Vorlesung", "#7F90EA"),
    P("Praktikum", "Praktikum", "#f4a460"),
    S("Seminar", "Seminar", "#556b2f"),
    U("Übung", "Übung", "#32cd32");

    private String shortForm;
    private String longForm;
    private String colorCode;

    KalenderTerminType(String shortForm, String longForm, String colorCode) {
        this.shortForm = shortForm;
        this.longForm = longForm;
        this.colorCode = colorCode;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getLongForm() {
        return longForm;
    }

    public String getShortForm() {
        return shortForm;
    }
}
