package team.boerse.tauschboerse.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;

public class CustomTokenFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        User us = null;
        if (token != null) {
            us = userRepository.findByAccessToken(token);
            String sessionToken = us == null ? null : us.getAccessToken();
            if (us == null || (us.isBanned() != null && us.isBanned())) {
                Cookie cookie = new Cookie("sessionToken", null);
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);

                filterChain.doFilter(request, response);
                return;
            }

            if (sessionToken == null) {
                filterChain.doFilter(request, response);
                return;
            }

        }
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        attributes.setAttribute("User", us, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
        filterChain.doFilter(request, response);
    }

    private String extractToken(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("sessionToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
