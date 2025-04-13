package team.boerse.tauschboerse.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.CounterService;
import team.boerse.tauschboerse.KalenderRepository;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.captcha.CaptchaController;
import team.boerse.tauschboerse.mail.MailUtils;
import team.boerse.tauschboerse.webauthn.JpaPublicKeyCredentialUserEntity;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    UserRepository userRepository;

    MailUtils mailUtils;

    KalenderRepository kalenderRepository;
    private CounterService counterService = null;
    CaptchaController captchaController;

    public WebSecurityConfig(UserRepository userRepository, MailUtils mailUtils,
            KalenderRepository kalenderRepository, CounterService counterService, CaptchaController captchaController) {
        this.userRepository = userRepository;
        this.mailUtils = mailUtils;
        this.kalenderRepository = kalenderRepository;
        this.counterService = counterService;
        this.captchaController = captchaController;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CaptchaFilter captchaFilter,
            CustomTokenFilter customTokenFilter) throws Exception {
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/login/ott", "/ott/generate", "/", "/Impressum und Datenschutz.html",
                        "/index.html",
                        "/requestLogin", "/betaLogin", "/challenge", "/randomFeedback", "/whoami", "/assets/**")
                .requireCsrfProtectionMatcher(request -> {
                    String path = request.getServletPath();
                    if (request.getMethod().equals("OPTIONS") || request.getMethod().equals("GET")) {
                        return false;
                    }
                    return path.startsWith("/webauthn") || path.startsWith("/login/webauthn");
                }));
        // CSRF-Schutz deaktivieren (nicht empfohlen für Produktionsumgebungen)

        // Autorisierung der Requests
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").hasRole("ADMIN") // Nur Benutzer mit Rolle ADMIN dürfen auf
                // Actuator-Endpunkte zugreifen
                .requestMatchers("/login/**", "/login/webauthn", "/api/csrf-token", "/ott/generate", "/",
                        "/Impressum und Datenschutz.html",
                        "/index.html",
                        "/requestLogin", "/betaLogin",
                        "/challenge", "/randomFeedback", "/whoami",
                        "/assets/**", "/favicon.ico", "/favicon.png")
                .permitAll().requestMatchers("/acuator/**", "/acuator").hasRole("ADMIN") //
                .anyRequest().authenticated() // Alle anderen Endpunkte erfordern Authentifizierung
        );
        // add CaptchaFilter to the filter chain

        // Wochen
        http.addFilterBefore(captchaFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(customTokenFilter, LogoutFilter.class);

        // CORS konfigurieren
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.oneTimeTokenLogin(
                (ott) -> ott.tokenGenerationSuccessHandler(new MagicLinkOneTimeTokenGenerationSuccessHandler())
                        .successHandler(
                                (request, response, authentication) -> {
                                    if (authentication.getPrincipal() == null) {
                                        return;
                                    }

                                    User spruser = (User) authentication.getPrincipal();

                                    team.boerse.tauschboerse.User user = userRepository
                                            .findByHsMail(spruser.getUsername()).orElse(null);

                                    if (user != null) {
                                        // has user a calendar?
                                        boolean calendarExists = kalenderRepository.findByUserId(user.getId()) != null;
                                        if (!calendarExists) {
                                            response.setStatus(HttpServletResponse.SC_CREATED);
                                        } else {
                                            response.setStatus(HttpServletResponse.SC_OK);
                                        }
                                    }
                                }));
        // Basic Auth aktivieren

        // Allowed Origins für -

        if (allowedOrigins == null || allowedOrigins.equals("*")) {
            allowedOrigins = "http://localhost:8085,http://localhost:5173,http://192.168.178.28:5173,https://tauschboerse.nkwebservices.de";

        }

        // get hostname from domain
        URL url = new URL(domain);
        String host = url.getHost();

        Set<String> allowedOriginsSet = Arrays.stream(allowedOrigins.split(","))
                .collect(Collectors.toSet());
        http.webAuthn((webAuthn) -> webAuthn.rpName("Tauschbörse Auth").rpId(host)
                .allowedOrigins(allowedOriginsSet));
        String key = System.getenv("REMEMBERMESECRET") == null ? "defaultRememberMeSecretKey"
                : System.getenv("REMEMBERMESECRET");

        http.rememberMe(
                e -> e.alwaysRemember(true).tokenValiditySeconds(1209600 * 2).key(key)); // 4

        return http.build();
    }

    @Bean
    public RememberMeServices rememberMeServices() {

        String key = System.getenv("REMEMBERMESECRET") == null ? "defaultRememberMeSecretKey"
                : System.getenv("REMEMBERMESECRET");

        CustomTokenBasedRememberMeServices services = new CustomTokenBasedRememberMeServices(key, users());
        services.setTokenValiditySeconds(1209600 * 2); // 4 Wochen
        services.setAlwaysRemember(true); // Optional, wenn Checkbox nicht vorhanden

        return services;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Value("${prometheus.password:}")
    private String passwordFromProperties;

    @Bean
    public UserDetailsService users() {

        return username -> {

            // Persistiere den Benutzer in der Datenbank, falls er noch nicht existiert
            if (username.equals("prometheus")) {
                String password = getPassword();

                String encodedPassword = passwordEncoder().encode(password);
                return User.builder()
                        .username("prometheus")
                        .password(encodedPassword)
                        .roles("ADMIN")
                        .build();
            }

            if (!username.toLowerCase().contains("@student")) {
                return User.builder()
                        .username(username)
                        .password("{noop}" + "password")
                        .roles("USER")
                        .build();
            }

            Optional<team.boerse.tauschboerse.User> ouser = userRepository.findByHsMail(username);
            if (!ouser.isPresent()) {

                team.boerse.tauschboerse.User user = new team.boerse.tauschboerse.User(username, null, false,
                        UUID.randomUUID().toString());

                user = userRepository.save(user);
                return User.builder()
                        .username(user.getHsMail())
                        .roles("USER")
                        .password("{noop}" + "password")
                        .build();

            } else {
                return User.builder()
                        .username(ouser.get().getHsMail())
                        .roles("USER")
                        .password("{noop}" + "password")
                        .build();
            }

        };

    }

    // Beispiel für eine benutzerdefinierte Klasse
    public class CustomTokenBasedRememberMeServices extends TokenBasedRememberMeServices {

        // Konstruktoren passend aufrufen (mit key, userDetailsService)
        public CustomTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService) {
            super(key, userDetailsService);
        }

        @Override
        protected String retrieveUserName(Authentication authentication) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            }
            // *** HIER die Prüfung für WebAuthn einfügen ***
            if (principal instanceof PublicKeyCredentialUserEntity) {
                return ((PublicKeyCredentialUserEntity) principal).getName();
            }
            if (principal instanceof JpaPublicKeyCredentialUserEntity) {
                return ((JpaPublicKeyCredentialUserEntity) principal).getName();
            }
            // Fallback (oder Fehler werfen, wenn kein bekannter Typ)
            return principal.toString();
        }
    }

    // In WebSecurityConfig müssten Sie dann diese Custom-Klasse als Bean erstellen
    // und verwenden.

    // Methode zum Abrufen oder Erstellen des Passworts
    private String getPassword() {
        // 1. Passwort aus Umgebungsvariablen laden
        String password = System.getenv("PROMETHEUS_PASSWORD");

        // 2. Falls das Passwort nicht in Umgebungsvariablen gefunden wird, aus den
        // Properties laden
        if (password == null || password.isEmpty()) {
            password = passwordFromProperties;
        }

        // 3. Wenn kein Passwort gefunden wurde, erstelle oder lade es aus einer Datei
        if (password == null || password.isEmpty()) {
            try {
                password = getPasswordFromFile();
            } catch (IOException e) {
                throw new RuntimeException("Fehler beim Erstellen oder Lesen der Passwort-Datei", e);
            }
        }

        return password;
    }

    // Methode zum Erstellen oder Laden des Passworts aus einer Datei
    private String getPasswordFromFile() throws IOException {
        Path path = Paths.get("./secure-password.txt");

        if (!Files.exists(path)) {
            String generatedPassword = generateRandomPassword();
            Files.writeString(path, generatedPassword);

            path.toFile().setReadable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(false, false);
            path.toFile().setWritable(true, true);
        }

        return Files.readString(path).trim();
    }

    private String generateRandomPassword() {
        int length = 32;
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(characters.charAt(secureRandom.nextInt(characters.length())));
        }
        return password.toString();
    }

    @Bean
    CustomTokenFilter customTokenFilter() {
        return new CustomTokenFilter();
    }

    @Value(value = "${cors.allowedOrigins:*}")
    private String allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        if (allowedOrigins == null || allowedOrigins.equals("*")) {
            allowedOrigins = "http://localhost:8085,http://localhost:5173,http://192.168.178.28:5173,https://tauschboerse.nkwebservices.de";
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;

    }

    @Value("${domain}")
    private String domain;

    public class MagicLinkOneTimeTokenGenerationSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

        private final OneTimeTokenGenerationSuccessHandler redirectHandler = new RedirectOneTimeTokenGenerationSuccessHandler(
                domain);

        // constructor omitted

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
                throws IOException, ServletException {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(domain)
                    .path("/")
                    .queryParam("otttoken", oneTimeToken.getTokenValue());
            String magicLink = builder.toUriString();
            String email = (oneTimeToken.getUsername());
            team.boerse.tauschboerse.User user = userRepository.findByHsMail(email).orElse(null);
            if (user != null && (user.isBanned() != null && user.isBanned())) {
                return;
            }

            boolean calendarExists = user != null && kalenderRepository.findByUserId(user.getId()) != null;
            counterService.incrementCounter("requestLogin");
            MailUtils.sendMail(email, null, "Anmeldelink für die Tauschbörse",
                    "Klicke hier um dich anzumelden:\n<a href='" + magicLink + "'>" + magicLink
                            + "</a>"
                            + (!calendarExists
                                    ? "\n\nDu benötigst eine Kalenderdatei. Lade sie hier direkt herunter:\n<a href='https://aor.cs.hs-rm.de/plans.ics?user_plan=true'>plans.ics herunterladen</a>"
                                    : ""));

            this.redirectHandler.handle(request, response, oneTimeToken);
        }

    }

    @Bean
    public CaptchaFilter captchaFilter() {
        return new CaptchaFilter(captchaController);
    }

    public class CaptchaFilter extends OncePerRequestFilter {

        private final CaptchaController captchaController;

        public CaptchaFilter(CaptchaController captchaController) {
            this.captchaController = captchaController;
        }

        private boolean isCorrectMailFormat(String hsMail) {
            String[] adress = hsMail.split("@");
            return adress[0].contains(".") && adress[1].equals("student.hs-rm.de");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            // Filter nur für POST-Anfragen an "/ott/generate" anwenden
            if ("/ott/generate".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
                try (BufferedReader reader = request.getReader()) {
                    StringBuilder requestBodyBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        requestBodyBuilder.append(line);
                    }
                    String requestBody = requestBodyBuilder.toString();

                    ObjectMapper objectMapper = new ObjectMapper();
                    CaptchaRequest captchaRequest;
                    try {
                        captchaRequest = objectMapper.readValue(requestBody, CaptchaRequest.class);
                    } catch (JsonProcessingException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("Ungültiges JSON-Format");
                        return;
                    }
                    if (!isCorrectMailFormat(captchaRequest.username)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("Ungültige E-Mail-Adresse");
                        return;
                    }

                    // Erstelle das Payload-Format, das der CaptchaController erwartet
                    String payload = "{\"payload\":\"" + captchaRequest.getPow() + "\"}";

                    // Prüfe den "pow"-Wert
                    if (!captchaController.checkSolution(payload)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("Ungültiger Captcha");
                        return;
                    }
                }
            }
            // Anfrage weiterreichen, wenn keine Prüfung nötig ist oder diese erfolgreich
            // war
            filterChain.doFilter(request, response);
        }

        // Hilfsklasse für das Parsen des JSON-Request-Bodys
        public static class CaptchaRequest {
            private String pow;
            private String username;

            public String getPow() {
                return pow;
            }

            public String getUsername() {
                return username;
            }

            public void setPow(String pow) {
                this.pow = pow;
            }

            public void setUsername(String hsMail) {
                this.username = hsMail;
            }
        }
    }
}