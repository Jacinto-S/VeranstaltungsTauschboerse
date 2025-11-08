package team.boerse.tauschboerse.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    private HttpServletRequest currentRequestOrNull() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    public void logEvent(AuditEventType eventType, String details) {
        User user = UserUtil.getUser();
        Long userId = user != null ? user.getId() : null;
        logEvent(userId, eventType, null, details);
    }

    public void logEvent(Long userId, AuditEventType eventType, String details) {
        logEvent(userId, eventType, null, details);
    }

    public void logEvent(Long userId, AuditEventType eventType, LoginMethod loginMethod, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setEventType(eventType);
        log.setLoginMethod(loginMethod);
        log.setEventDetails(details);
        log.setTimestamp(new Date());
        log.setSemester(getCurrentSemester());

        HttpServletRequest req = currentRequestOrNull();
        if (req != null) {
            log.setIpAddress(req.getRemoteAddr());
            String userAgent = req.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 2000) {
                userAgent = userAgent.substring(0, 2000);
            }
            log.setUserAgent(userAgent);
        }

        auditLogRepository.save(log);
    }

    public void logError(String details, Exception e) {
        AuditLog log = new AuditLog();
        User user = UserUtil.getUser();
        log.setUserId(user != null ? user.getId() : null);
        log.setEventType(AuditEventType.ERROR);
        log.setEventDetails(details);
        log.setTimestamp(new Date());
        log.setSemester(getCurrentSemester());

        if (e != null) {
            log.setStackTrace(getStackTrace(e));
        }

        HttpServletRequest req = currentRequestOrNull();
        if (req != null) {
            log.setIpAddress(req.getRemoteAddr());
        }

        auditLogRepository.save(log);
    }

    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    public Page<AuditLog> getAuditLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    public Page<AuditLog> getAuditLogsByFilters(
            Long userId,
            AuditEventType eventType,
            String semester,
            LoginMethod loginMethod,
            Date startDate,
            Date endDate,
            boolean includeDebug,
            Pageable pageable) {
        return auditLogRepository.findByFilters(userId, eventType, semester, loginMethod, startDate, endDate,
                includeDebug, pageable);
    }

    public String getCurrentSemester() {
        return SemesterUtil.getCurrentSemester();
    }

    private String getStackTrace(Exception e) {
        if (e == null)
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
