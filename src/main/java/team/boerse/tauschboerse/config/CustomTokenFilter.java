package team.boerse.tauschboerse.config;

import java.io.IOException;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.audit.LoginMethod;
import team.boerse.tauschboerse.audit.SemesterUtil;
import team.boerse.tauschboerse.metrics.UserMetricsService;
import team.boerse.tauschboerse.webauthn.JpaPublicKeyCredentialUserEntity;

public class CustomTokenFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserMetricsService userMetricsService;
    private final RememberMeServices rememberMeServices;

    @Value("${domain}")
    private String domain;

    private static final String REMEMBER_ME_GENERATED_ATTR = "rememberMeCookieGeneratedForWebAuthn";

    public CustomTokenFilter(UserRepository userRepository,
            AuditService auditService,
            UserMetricsService userMetricsService,
            RememberMeServices rememberMeServices) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.userMetricsService = userMetricsService;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        User us = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = null;
        String username = null;
        if (authentication != null && authentication.getPrincipal() != null) {
            if (authentication
                    .getPrincipal() instanceof org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity) {
                principal = ((org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity) authentication
                        .getPrincipal());
                username = ((org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity) principal)
                        .getName();
            } else if (authentication.getPrincipal() instanceof UserDetails) {
                principal = (UserDetails) authentication.getPrincipal();
                username = ((UserDetails) principal).getUsername();
            } else if (authentication.getPrincipal() instanceof JpaPublicKeyCredentialUserEntity) {
                principal = (JpaPublicKeyCredentialUserEntity) authentication.getPrincipal();
                username = ((JpaPublicKeyCredentialUserEntity) principal).getName();
            } else if (authentication.getPrincipal() instanceof String) {
                principal = (String) authentication.getPrincipal();
                username = (String) principal;
            } else {
                principal = authentication.getPrincipal();
                username = (String) principal;
            }
        }

        var session = request.getSession(false);
        if (session != null) {
            String queryString = request.getQueryString();
            if (queryString != null && (queryString.contains("continue") || queryString.contains("error"))) {
                session.removeAttribute("SPRING_SECURITY_SAVED_REQUEST");
                response.sendRedirect(domain);
                return;
            }
        }

        if (principal != null) {
            us = userRepository.findByHsMail(username).orElse(null);

            if (us == null || (us.getIsBanned() != null && us.getIsBanned())) {
                filterChain.doFilter(request, response);
                return;
            }
            ServletRequestAttributes attributes = new ServletRequestAttributes(request);
            attributes.setAttribute("User", us, RequestAttributes.SCOPE_REQUEST);
            RequestContextHolder.setRequestAttributes(attributes);

        } else {
            // Beta-Login per Session-Token Cookie erlauben (nur in Dev via /betaLogin
            // gesetzt)
            String token = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("sessionToken".equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
            if (token != null && !token.isEmpty()) {
                try {
                    us = userRepository.findByAccessToken(token);
                } catch (Exception e) {
                    us = null;
                }

                if (us != null && !(us.getIsBanned() != null && us.getIsBanned())) {
                    ServletRequestAttributes attributes = new ServletRequestAttributes(request);
                    attributes.setAttribute("User", us, RequestAttributes.SCOPE_REQUEST);
                    RequestContextHolder.setRequestAttributes(attributes);

                    var authorities = new ArrayList<GrantedAuthority>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                    if (Boolean.TRUE.equals(us.getIsAdmin())) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }

                    Authentication auth = new UsernamePasswordAuthenticationToken(us.getHsMail(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        if (us == null) {
            filterChain.doFilter(request, response);
            return;

        }
        boolean isWebAuthnPrincipal = principal instanceof ImmutablePublicKeyCredentialUserEntity
                || principal instanceof JpaPublicKeyCredentialUserEntity;

        if (isWebAuthnPrincipal && us != null && !(authentication instanceof RememberMeAuthenticationToken)) {
            try {
                if (session == null) {
                    session = request.getSession(true);
                }
                if (session.getAttribute(REMEMBER_ME_GENERATED_ATTR) != null) {
                    filterChain.doFilter(request, response);
                    return;
                }
                rememberMeServices.loginSuccess(request, response, authentication);
                try {
                    Long userId = us != null ? us.getId() : null;
                    auditService.logEvent(userId, AuditEventType.LOGIN_SUCCESS,
                            LoginMethod.PASSKEY, "WebAuthn/Passkey login");
                    userMetricsService.getOrCreateMetrics(userId,
                            SemesterUtil.getSemesterForDate(new java.util.Date()));
                    userMetricsService.recordLogin(userId, LoginMethod.PASSKEY);
                    try {
                        userMetricsService.recordPasskeyUsage(userId);
                    } catch (Exception ignore) {
                    }
                    try {
                        if (us.getUsesPasskeys() == null || !us.getUsesPasskeys()) {
                            us.setUsesPasskeys(true);
                            userRepository.save(us);
                        }
                    } catch (Exception ignore) {
                    }
                    userMetricsService.recordActivity(userId);
                } catch (Exception e) {
                }
                session.setAttribute(REMEMBER_ME_GENERATED_ATTR, Boolean.TRUE);
            } catch (Exception e) {
            }

        }

        filterChain.doFilter(request, response);
    }
}
