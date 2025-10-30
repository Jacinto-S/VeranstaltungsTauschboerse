package team.boerse.tauschboerse.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
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
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.lang.NonNull;
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
import lombok.Data;
import team.boerse.tauschboerse.KalenderRepository;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.audit.LoginMethod;
import team.boerse.tauschboerse.audit.SemesterUtil;
import team.boerse.tauschboerse.captcha.CaptchaController;
import team.boerse.tauschboerse.mail.MailUtils;
import team.boerse.tauschboerse.metrics.UserMetricsService;
import team.boerse.tauschboerse.webauthn.JpaPublicKeyCredentialUserEntity;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final UserRepository userRepository;
    private final KalenderRepository kalenderRepository;
    private final CaptchaController captchaController;
    private final AuditService auditService;
    private final UserMetricsService userMetricsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CaptchaFilter captchaFilter,
            CustomTokenFilter customTokenFilter,
            RememberMeServices rememberMeServices,
            UserDetailsService userDetailsService) throws Exception {
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/login/ott", "/ott/generate", "/", "/Impressum und Datenschutz.html",
                        "/index.html",
                        "/requestLogin", "/betaLogin", "/logmeout", "/challenge", "/randomFeedback", "/whoami",
                        "/assets/**")
                .requireCsrfProtectionMatcher(request -> {
                    String path = request.getServletPath();
                    if (request.getMethod().equals("OPTIONS") || request.getMethod().equals("GET")) {
                        return false;
                    }
                    return path.startsWith("/webauthn") || path.startsWith("/login/webauthn");
                }));

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/admin/matching-test/simulate").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin", "/admin.html").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/login/**", "/login/webauthn", "/api/csrf-token", "/ott/generate", "/",
                        "/Impressum und Datenschutz.html",
                        "/index.html", "/evaluation.html",
                        "/requestLogin", "/betaLogin",
                        "/challenge", "/randomFeedback", "/whoami",
                        "/assets/**", "/favicon.ico", "/favicon.png")
                .permitAll().requestMatchers("/acuator/**", "/acuator").hasRole("ADMIN") //
                .anyRequest().authenticated() // Alle anderen Endpunkte erfordern Authentifizierung
        );

        http.addFilterBefore(captchaFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(customTokenFilter, RememberMeAuthenticationFilter.class);

        // CORS konfigurieren
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.oneTimeTokenLogin(
                (ott) -> ott.tokenGenerationSuccessHandler(new MagicLinkOneTimeTokenGenerationSuccessHandler())
                        .successHandler(
                                (request, response, authentication) -> {
                                    request.getServletPath();
                                    if (authentication.getPrincipal() == null) {
                                        return;
                                    }

                                    User spruser = (User) authentication.getPrincipal();

                                    team.boerse.tauschboerse.User user = userRepository
                                            .findByHsMail(spruser.getUsername()).orElse(null);

                                    if (user != null) {
                                        boolean calendarExists = kalenderRepository.findByUserId(user.getId()) != null;
                                        if (!calendarExists) {
                                            response.setStatus(HttpServletResponse.SC_CREATED);
                                        } else {
                                            response.setStatus(HttpServletResponse.SC_OK);
                                        }
                                        try {
                                            Long userId = user.getId();
                                            auditService.logEvent(userId,
                                                    AuditEventType.LOGIN_SUCCESS,
                                                    LoginMethod.TOKEN,
                                                    "Magic-link login successful");
                                            userMetricsService.getOrCreateMetrics(userId,
                                                    SemesterUtil
                                                            .getSemesterForDate(new java.util.Date()));
                                            userMetricsService.recordLogin(userId,
                                                    LoginMethod.TOKEN);
                                            user.setLastActivityDate(new java.util.Date());
                                            userRepository.save(user);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }));

        if (allowedOrigins == null || allowedOrigins.equals("*")) {
            allowedOrigins = "http://localhost:8085,http://localhost:5173,http://192.168.178.28:5173,https://tauschboerse.nkwebservices.de";

        }
        URL url = URI.create(domain).toURL();
        String host = url.getHost();

        Set<String> allowedOriginsSet = Arrays.stream(allowedOrigins.split(","))
                .collect(Collectors.toSet());
        http.webAuthn((webAuthn) -> webAuthn.rpName("Tauschbörse Auth").rpId(host)
                .allowedOrigins(allowedOriginsSet));
        String key = System.getenv("REMEMBERMESECRET") == null ? "defaultRememberMeSecretKey"
                : System.getenv("REMEMBERMESECRET");

        http.rememberMe(
                e -> e
                        .rememberMeServices(rememberMeServices)
                        .userDetailsService(userDetailsService)
                        .alwaysRemember(true)
                        .tokenValiditySeconds(1209600 * 2)
                        .key(key)); // 4

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

    @Bean
    public UserDetailsService users() {

        return username -> {
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

                // Prüfe, ob der Benutzer Admin ist
                String[] roles = (user.getIsAdmin() != null && user.getIsAdmin()) ? new String[] { "USER", "ADMIN" }
                        : new String[] { "USER" };

                var userBuilder = User.builder()
                        .username(user.getHsMail())
                        .roles(roles)
                        .password("{noop}" + "password")
                        .build();
                return userBuilder;

            } else {
                team.boerse.tauschboerse.User user = ouser.get();
                // Prüfe, ob der Benutzer Admin ist
                String[] roles = (user.getIsAdmin() != null && user.getIsAdmin()) ? new String[] { "USER", "ADMIN" }
                        : new String[] { "USER" };

                return User.builder()
                        .username(user.getHsMail())
                        .roles(roles)
                        .password("{noop}" + "password")
                        .build();
            }

        };

    }

    public class CustomTokenBasedRememberMeServices extends TokenBasedRememberMeServices {

        public CustomTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService) {
            super(key, userDetailsService);
        }

        @Override
        protected String retrieveUserName(Authentication authentication) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            }
            if (principal instanceof PublicKeyCredentialUserEntity) {
                return ((PublicKeyCredentialUserEntity) principal).getName();
            }
            if (principal instanceof JpaPublicKeyCredentialUserEntity) {
                return ((JpaPublicKeyCredentialUserEntity) principal).getName();
            }
            return principal.toString();
        }
    }

    @Bean
    CustomTokenFilter customTokenFilter() {
        return new CustomTokenFilter(userRepository, auditService, userMetricsService, rememberMeServices());
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
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "x-csrf-token"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;

    }

    @Value("${domain}")
    private String domain;

    public class MagicLinkOneTimeTokenGenerationSuccessHandler
            implements OneTimeTokenGenerationSuccessHandler {

        private final OneTimeTokenGenerationSuccessHandler redirectHandler = new RedirectOneTimeTokenGenerationSuccessHandler(
                domain);

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
                throws IOException, ServletException {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(domain)
                    .path("/")
                    .queryParam("otttoken", oneTimeToken.getTokenValue());
            String magicLink = builder.toUriString();
            String email = (oneTimeToken.getUsername());
            team.boerse.tauschboerse.User user = userRepository.findByHsMail(email).orElse(null);
            if (user != null && (user.getIsBanned() != null && user.getIsBanned())) {
                return;
            }

            boolean calendarExists = user != null && kalenderRepository.findByUserId(user.getId()) != null;
            try {
                Long userId = null;
                team.boerse.tauschboerse.User u = userRepository.findByHsMail(email).orElse(null);
                if (u != null)
                    userId = u.getId();
                auditService.logEvent(userId, team.boerse.tauschboerse.audit.AuditEventType.LOGIN_REQUEST,
                        "Magic-link requested for " + email);
            } catch (Exception ex) {
            }
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
        protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                @NonNull FilterChain filterChain) throws ServletException, IOException {

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

                    String payload = "{\"payload\":\"" + captchaRequest.getPow() + "\"}";

                    // Prüfe den "pow"-Wert
                    if (!captchaController.checkSolution(payload)) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("Ungültiger Captcha");
                        return;
                    }
                }
            }
            filterChain.doFilter(request, response);
        }

        @Data
        public static class CaptchaRequest {
            private String pow;
            private String username;
        }
    }
}