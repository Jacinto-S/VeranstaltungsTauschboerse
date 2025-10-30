package team.boerse.tauschboerse.audit;

/**
 * Aufzählung aller möglichen Audit-Event-Typen im System
 */
public enum AuditEventType {
    // Login/Logout
    LOGIN_REQUEST("Login-Anfrage", true),
    LOGIN_SUCCESS("Login erfolgreich", true),
    LOGIN_FAILED("Login fehlgeschlagen", true),
    LOGOUT("Logout", true),

    // Kalender
    CALENDAR_UPLOAD("Kalender hochgeladen", true),
    CALENDAR_DELETE("Kalender gelöscht", true),
    CALENDAR_VIEW("Kalender angesehen", false),

    // Angebote
    OFFER_CREATE("Angebot erstellt", true),
    OFFER_DELETE("Angebot gelöscht", true),
    OFFER_VIEW("Angebot angesehen", false),
    OFFER_ACCEPT("Angebot akzeptiert", true),
    OFFER_DECLINE("Angebot abgelehnt", false),

    // Vermittlung
    MATCH_SUCCESS("Vermittlung erfolgreich", true),
    MATCH_FAILED("Vermittlung fehlgeschlagen", false),

    // Admin
    USER_BANNED("Benutzer geblockt", true),
    USER_UNBANNED("Benutzer entsperrt", false),

    // System
    ERROR("Fehler", false),
    WARNING("Warnung", false),
    // User Account Management
    PRIVATE_MAIL_CHANGED("Private E-Mail geändert", true),
    PASSKEY_CREATED("Passkey erstellt", true),
    PASSKEY_REVOKED("Passkey widerrufen", true),

    // Studiengang
    STUDIENGANG_SET("Studiengang gesetzt", true);

    private final String displayName;
    private final boolean visible;

    AuditEventType(String displayName, boolean visible) {
        this.displayName = displayName;
        this.visible = visible;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVisible() {
        return visible;
    }
}
