package team.boerse.tauschboerse;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class UserUtil {

    public static User getUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? (User) attributes.getAttribute("User", ServletRequestAttributes.SCOPE_REQUEST)
                : null;
    }

    private UserUtil() {
        throw new IllegalStateException("Utility class");
    }

}
