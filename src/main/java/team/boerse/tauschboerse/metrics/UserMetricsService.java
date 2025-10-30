package team.boerse.tauschboerse.metrics;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.audit.LoginMethod;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserMetricsService {

    private final UserMetricsRepository userMetricsRepository;
    private final UserRepository userRepository;

    public UserMetrics createMetrics(Long userId, String registrationSemester) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        UserMetrics metrics = new UserMetrics(user, registrationSemester);
        return userMetricsRepository.save(metrics);
    }

    public UserMetrics getOrCreateMetrics(Long userId, String registrationSemester) {
        Optional<UserMetrics> existing = userMetricsRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return createMetrics(userId, registrationSemester);
    }

    public UserMetrics getMetrics(Long userId) {
        return userMetricsRepository.findByUserId(userId).orElse(null);
    }

    public void recordLogin(Long userId, LoginMethod loginMethod) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            metrics.setTotalLogins(metrics.getTotalLogins() + 1);
            metrics.setTotalLoginAttempts(metrics.getTotalLoginAttempts() + 1);
            metrics.setLastLogin(new Date());
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordPasskeyUsage(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null && !metrics.getUsesPasskeys()) {
            metrics.setUsesPasskeys(true);
            metrics.setFirstPasskeyUse(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordCalendarUpload(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            if (!metrics.getHasUploadedCalendar()) {
                metrics.setHasUploadedCalendar(true);
                metrics.setFirstCalendarUpload(new Date());
            }
            metrics.setTotalCalendarUploads(metrics.getTotalCalendarUploads() + 1);
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordOfferCreation(Long userId, int offersCreated) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            if (!metrics.getHasCreatedOffer()) {
                metrics.setHasCreatedOffer(true);
                metrics.setFirstOfferCreated(new Date());
            }
            metrics.setTotalOffersCreated(metrics.getTotalOffersCreated() + offersCreated);
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordOfferAcceptance(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            metrics.setTotalOffersAccepted(metrics.getTotalOffersAccepted() + 1);
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordSuccessfulMatch(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            if (!metrics.getWasSuccessfullyMatched()) {
                metrics.setWasSuccessfullyMatched(true);
                metrics.setFirstSuccessfulMatch(new Date());
            }
            metrics.setTotalMatches(metrics.getTotalMatches() + 1);
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordLoginAttempt(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            metrics.setTotalLoginAttempts(metrics.getTotalLoginAttempts() + 1);
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }

    public void recordActivity(Long userId) {
        UserMetrics metrics = getMetrics(userId);
        if (metrics != null) {
            metrics.setLastActivity(new Date());
            metrics.setUpdatedAt(new Date());
            userMetricsRepository.save(metrics);
        }
    }
}
