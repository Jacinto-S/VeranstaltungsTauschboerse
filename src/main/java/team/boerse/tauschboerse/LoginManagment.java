package team.boerse.tauschboerse;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@EnableScheduling
@RestController
@RequiredArgsConstructor
public class LoginManagment {

	private final UserRepository userRepository;
	private final team.boerse.tauschboerse.audit.AuditService auditService;
	private final team.boerse.tauschboerse.metrics.UserMetricsService userMetricsService;
	private final RememberMeServices rememberMeServices;
	private final UserDetailsService userDetailsService;

	HashMap<String, String> tokens = new HashMap<>();
	HashMap<String, Long> removeTimerForTokens = new HashMap<>();
	HashMap<InetAddress, Integer> requestCounter = new HashMap<>();
	HashMap<InetAddress, Long> lastRequest = new HashMap<>();

	Logger logger = LoggerFactory.getLogger(LoginManagment.class);

	@Scheduled(fixedDelay = 60000)
	public void removeExpiredTokens() {
		long currentTime = System.currentTimeMillis();
		List<String> expiredTokens = new ArrayList<>();

		for (Map.Entry<String, Long> entry : removeTimerForTokens.entrySet()) {
			if (entry.getValue() < currentTime) {
				expiredTokens.add(entry.getKey());
			}
		}

		for (String token : expiredTokens) {
			tokens.remove(token);
			removeTimerForTokens.remove(token);
		}

		List<InetAddress> expiredIPs = new ArrayList<>();
		for (Map.Entry<InetAddress, Long> entry : lastRequest.entrySet()) {
			if (entry.getValue() > currentTime + 1000 * 60 * 60 * 6) {
				expiredIPs.add(entry.getKey());
			}
		}
		for (InetAddress ip : expiredIPs) {
			requestCounter.remove(ip);
			lastRequest.remove(ip);
		}

	}

	@Value("${domain}")
	String domain;

	@GetMapping("/betaLogin")
	public ResponseEntity<String> betaLogin(HttpServletRequest request, HttpServletResponse response,
			@RequestParam(required = false, defaultValue = "1") String number) {
		if (!(domain.contains("localhost") || domain.contains("172"))) {
			return ResponseEntity.badRequest().body("Not allowed");
		}

		int num = Integer.parseInt(number);
		if (num < 1 || num > 100) {
			return ResponseEntity.badRequest().body("Invalid number");
		}
		String hsMail = "maximilia" + num + ".musterata" + num + "@student.hs-rm.de";

		User user = userRepository.findByHsMail(hsMail).orElse(null);
		if (user == null) {
			user = new User(hsMail, null, false, "");
			userRepository.save(user);
		}

		UserDetails userDetails = userDetailsService.loadUserByUsername(hsMail);
		var auth = new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
				userDetails.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(auth);
		try {
			rememberMeServices.loginSuccess(request, response, auth);
		} catch (Exception e) {
			logger.warn("Failed to set remember-me cookie on betaLogin", e);
		}
		logger.info(String.format("User %s logged in", hsMail));
		try {
			User u = userRepository.findByHsMail(hsMail).orElse(null);
			Long userId = u != null ? u.getId() : null;
			auditService.logEvent(userId, team.boerse.tauschboerse.audit.AuditEventType.LOGIN_SUCCESS,
					team.boerse.tauschboerse.audit.LoginMethod.BETA, "Beta login");
			userMetricsService.getOrCreateMetrics(userId,
					team.boerse.tauschboerse.audit.SemesterUtil.getSemesterForDate(new java.util.Date()));
			userMetricsService.recordLogin(userId, team.boerse.tauschboerse.audit.LoginMethod.BETA);
			if (u != null) {
				u.setLastActivityDate(new java.util.Date());
				userRepository.save(u);
			}
		} catch (Exception ex) {
			logger.warn("Failed to record audit/metrics for betaLogin", ex);
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/logmeout")
	public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response,
			@RequestParam(defaultValue = "false", required = false) boolean all) {
		User user = UserUtil.getUser();
		if (user == null) {
			return ResponseEntity.badRequest().body("User not logged in");
		}

		SecurityContextHolder.clearContext();

		try {
			request.getSession().invalidate();
		} catch (IllegalStateException e) {
			logger.debug("Session already invalidated", e);
		}

		try {
			rememberMeServices.loginFail(request, response);
		} catch (Exception e) {
			logger.debug("Error removing remember-me on server side", e);
		}

		String ctxPath = request.getContextPath();
		ResponseCookie cookie = ResponseCookie
				.from("remember-me", "")
				.maxAge(0)
				.path(ctxPath == null || ctxPath.isEmpty() ? "/" : ctxPath)
				.httpOnly(true)
				.secure(request.isSecure())
				.sameSite("Strict")
				.build();
		response.addHeader("Set-Cookie", cookie.toString());

		try {
			auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.LOGOUT,
					"User logged out");
			userMetricsService.recordActivity(user.getId());
		} catch (Exception ex) {
			logger.warn("Failed to write audit/metrics on logout", ex);
		}

		return ResponseEntity.ok().build();
	}

	@GetMapping("/whoami")
	public ResponseEntity<?> whoami(HttpServletRequest request, HttpServletResponse response) {
		User user = UserUtil.getUser();
		if (user == null) {
			return ResponseEntity.ok().body("{\"hsMail\": \"User not logged in\", \"isAdmin\": false}");
		}
		request.getSession();
		Map<String, Object> userInfo = new HashMap<>();
		userInfo.put("hsMail", user.getHsMail());
		userInfo.put("isAdmin", Boolean.TRUE.equals(user.getIsAdmin()));
		return ResponseEntity.ok(userInfo);
	}

	@GetMapping("/updatePrivateMail")
	public ResponseEntity<String> updatePrivateMail(@RequestParam(required = false) String privateMail) {
		User user = UserUtil.getUser();
		if (user == null) {
			return ResponseEntity.badRequest().body("User not logged in");
		}
		logger.info(String.format("User %s updated private mail to %s", user.getHsMail(),
				privateMail == null ? "null" : privateMail));
		user.setPrivateMail(privateMail);
		userRepository.save(user);

		try {
			String eventDetails = privateMail != null ? "Private email updated to: " + privateMail
					: "Private email deleted";
			auditService.logEvent(user.getId(), team.boerse.tauschboerse.audit.AuditEventType.PRIVATE_MAIL_CHANGED,
					eventDetails);
		} catch (Exception ex) {
			logger.warn("Failed to write audit log for PRIVATE_MAIL_CHANGED", ex);
		}

		return ResponseEntity.ok().build();
	}

	@GetMapping("/api/csrf-token")
	public CsrfToken csrf(CsrfToken token) {
		return token;
	}
}
