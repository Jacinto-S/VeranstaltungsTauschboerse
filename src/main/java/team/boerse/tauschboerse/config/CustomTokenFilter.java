package team.boerse.tauschboerse.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.web.exchanges.HttpExchange.Principal;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.webauthn.JpaPublicKeyCredentialUserEntity;

public class CustomTokenFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;
    private static final String REMEMBER_ME_GENERATED_ATTR = "rememberMeCookieGeneratedForWebAuthn";

    @Autowired
    private RememberMeServices rememberMeServices;

    @Value("${domain}")
    private String domain;

    @SuppressWarnings("null")
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
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
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String[] credentials = new String(java.util.Base64.getDecoder().decode(authHeader.substring(6)))
                    .split(":");

            if (credentials.length >= 2 && !("prometheus".equals(credentials[0]))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized access");
                return;
            }
        }
        var session = request.getSession(true);
        if (session != null) {
            // if path contains ?continue or ?error
            String queryString = request.getQueryString();
            if (queryString != null && (queryString.contains("continue") || queryString.contains("error"))) {
                session.removeAttribute("SPRING_SECURITY_SAVED_REQUEST");
                response.sendRedirect(domain);
                return;
            }
        }

        if (principal != null) {
            us = userRepository.findByHsMail(username).orElse(null);

            if (us == null || (us.isBanned() != null && us.isBanned())) {
                filterChain.doFilter(request, response);
                return;
            }
            ServletRequestAttributes attributes = new ServletRequestAttributes(request);
            attributes.setAttribute("User", us, RequestAttributes.SCOPE_REQUEST);
            RequestContextHolder.setRequestAttributes(attributes);

        }
        if (us == null) {
            filterChain.doFilter(request, response);
            return;

        }
        boolean isWebAuthnPrincipal = principal instanceof org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
                || principal instanceof JpaPublicKeyCredentialUserEntity;

        if (isWebAuthnPrincipal && us != null && !(authentication instanceof RememberMeAuthenticationToken)
                && session != null && session.getAttribute(REMEMBER_ME_GENERATED_ATTR) == null) {
            try {
                rememberMeServices.loginSuccess(request, response, authentication);
                // Mark session to prevent repeated calls
                session.setAttribute(REMEMBER_ME_GENERATED_ATTR, Boolean.TRUE);
            } catch (Exception e) {
            }

        }

        filterChain.doFilter(request, response);
    }
}
