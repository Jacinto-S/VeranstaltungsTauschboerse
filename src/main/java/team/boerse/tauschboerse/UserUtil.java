package team.boerse.tauschboerse;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.Cookie;

public class UserUtil {
    /**
     * Konvertiert ein Cookie-Objekt in einen Set-Cookie-Header.
     * Diese Methode wird benötigt, da die Standard-Cookie-Klasse das Setzen von
     * SameSite nicht unterstützt.
     *
     * @param cookie Das Cookie-Objekt, das konvertiert werden soll.
     * @return Der Set-Cookie-Header als String.
     */
    public static String convertCookieToSetCookie(Cookie cookie) {
        StringBuilder setCookieHeader = new StringBuilder();
        setCookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());

        if (cookie.getMaxAge() >= 0) {
            setCookieHeader.append("; Max-Age=").append(cookie.getMaxAge());
        }

        if (cookie.getPath() != null) {
            setCookieHeader.append("; Path=").append(cookie.getPath());
        }

        if (cookie.getDomain() != null) {
            setCookieHeader.append("; Domain=").append(cookie.getDomain());
        }

        if (cookie.getSecure()) {
            setCookieHeader.append("; Secure");
        }

        if (cookie.isHttpOnly()) {
            setCookieHeader.append("; HttpOnly");
        }

        setCookieHeader.append("; SameSite=Strict");

        return setCookieHeader.toString();
    }

    public static User getUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        User user = attributes != null ? (User) attributes.getAttribute("User", ServletRequestAttributes.SCOPE_REQUEST)
                : null;
        return user;
    }

}
