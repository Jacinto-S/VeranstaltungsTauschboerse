package team.boerse.tauschboerse;

public enum KalenderTerminType {

    V("Vorlesung", "Vorlesung", "#FF0000"),
    P("Praktikum", "Praktikum", "#00FF00"),
    S("Seminar", "Seminar", "#0000FF"),
    U("Übung", "Übung", "#FFFF00");

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
