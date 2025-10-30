# VeranstaltungsTauschboerse

Eine Plattform zum Tauschen von Terminen für universitäre Veranstaltungen, die zu verschiedenen Zeiten angeboten werden, um den Zeitplan der Studierenden besser anzupassen.

## Funktionen

- Benutzerregistrierung und Authentifizierung mit WebAuthn
- Erstellung und Verwaltung von Tauschangeboten für Veranstaltungstermine
- Automatisches Matching von Tauschpartnern
- Kalenderintegration (iCal-Unterstützung)
- Bewertungssystem für Tausche
- Admin-Panel für Systemverwaltung
- E-Mail-Benachrichtigungen

## Architektur

Die Anwendung folgt einer Microservices-ähnlichen Struktur mit einem Spring Boot Backend und einem Vite-basierten Frontend. Das Backend stellt REST-APIs bereit, während das Frontend eine responsive Weboberfläche mit Bootstrap und Chart.js und weiteren Bibliotheken bietet. Die Daten werden in einer MariaDB-Datenbank gespeichert.

### Komponenten

- **Backend (Spring Boot)**: Verarbeitet Geschäftslogik, Matching-Algorithmus, E-Mail-Versand und API-Endpunkte.
- **Frontend (Vite + Bootstrap)**: Benutzeroberfläche für Registrierung, Tauschangebote, Kalender und Administration.
- **Datenbank (MariaDB)**: Speichert Benutzer, Termine, Kalender und Bewertungen.
- **Matching-Service**: Automatischer Algorithmus zur Paarung von Tauschangeboten basierend auf Verfügbarkeit und Präferenzen.

## Technologiestack

- **Backend**: Java 25, Spring Boot 3.5.7, Spring Data JPA, Spring Security, WebAuthn4J, iCal4J, Angus Mail
- **Frontend**: Vite, Bootstrap 5, Chart.js, Sass, WebAuth
- **Datenbank**: MariaDB 10.5+
- **Build-Tools**: Maven, npm
- **Sonstiges**: Lombok, Jackson, AsciiDoc für Dokumentation

## Datenmodell

- **User**: Benutzerinformationen, WebAuthn-Credentials
- **TauschTermin**: Tauschangebote mit Details zu gewünschten und angebotenen Terminen
- **Kalender**: Veranstaltungskalender mit Terminen
- **KalenderTermin**: Einzelne Termine in Kalendern
- **Audit**: Protokollierung von Aktionen für Compliance
- **Feedback**: Bewertungen von Tauschpartnern

## API

Die Anwendung bietet RESTful APIs für:

- Benutzerverwaltung: Registrierung, Login, Profil
- Tauschangebote: Erstellen, Bearbeiten, Löschen
- Matching: Manuelles und automatisches Matching
- Kalender: Import/Export von iCal-Dateien
- Administration: Systemeinstellungen, Metriken

 
## Voraussetzungen

- Java 25
- Maven 3.6+
- MariaDB 10.5+
- Node.js 18+ (für Frontend-Build)
- npm (für Frontend-Paketverwaltung)

## Installation und Setup

1. Repository klonen:
   ```
   git clone https://github.com/Jacinto-S/VeranstaltungsTauschboerse.git
   cd VeranstaltungsTauschboerse
   ```

2. Datenbank einrichten:
   - MariaDB installieren und starten
   - Datenbank "tauschkatalog" erstellen
   - Benutzer "root" mit Zugriff auf die Datenbank konfigurieren (Besser: application.properties anpassen)

3. Backend-Abhängigkeiten installieren:
   ```
   mvn clean install
   ```

4. Frontend-Abhängigkeiten installieren:
   ```
   cd frontend
   npm install
   cd ..
   ```

5. Frontend bauen und in Backend integrieren:
   ```
   cd frontend
   npm run build
   npm run copyToSpringWin  # Kopiert nach src/main/resources/static
   cd ..
   ```

## Ausführen

### Entwicklung

1. Backend starten:
   ```
   mvn spring-boot:run
   ```

2. Frontend im Entwicklungsmodus starten:
   ```
   cd frontend
   pnpm run dev
   ```
   Frontend läuft dann auf http://localhost:5173, Backend auf http://localhost:8085

### Produktion

1. JAR bauen:
   ```
   mvn clean package
   ```

2. JAR ausführen:
   ```
   java -jar target/tauschboerse-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
   ```

## Konfiguration

- `src/main/resources/application.properties`: Lokale Entwicklung
- `src/main/resources/prod.properties`: Produktionsumgebung

Wichtige Einstellungen:
- Datenbank-URL und -Credentials
- CORS-allowed Origins
- Domain für E-Mails und Links
- Logging-Level

## Tests

Tests ausführen:
```
mvn test
```

## Deployment

Für Produktion kann die Anwendung in einem Docker-Container oder auf einem Server deployed werden. Die prod.properties enthält Einstellungen für eine entfernte Datenbank (z. B. tauschdb:3345). Verwende Spring Profiles für unterschiedliche Umgebungen.

## Beitragen

Bitte Issues und Pull Requests auf GitHub erstellen.

## Lizenz
https://polyformproject.org/licenses/noncommercial/1.0.0/
Die Anwendung wird unter der Polyform Noncommercial License v1.0.0 lizenziert.
[LICENSE.md](LICENSE.md)
