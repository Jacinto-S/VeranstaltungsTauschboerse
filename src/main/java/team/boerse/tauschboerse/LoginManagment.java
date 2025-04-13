package team.boerse.tauschboerse;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.captcha.CaptchaController;

@EnableScheduling
@RestController
public class LoginManagment {

	@Autowired
	UserRepository userRepository;

	@Autowired
	CaptchaController captchaController;

	@Autowired
	KalenderRepository kalenderRepository;

	@Autowired
	private final CounterService counterService = null;

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
	public ResponseEntity<String> betaLogin(HttpServletResponse response,
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
		}
		String accessToken = UUID.randomUUID().toString();
		user.getAccessToken().add(accessToken);
		Cookie cookie = new Cookie("sessionToken", accessToken);
		cookie.setMaxAge(60 * 60 * 24 * 30);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		response.setHeader("Set-Cookie", UserUtil.convertCookieToSetCookie(cookie));
		userRepository.save(user);
		logger.info(String.format("User %s logged in", hsMail));
		return ResponseEntity.ok().build();
	}

	@GetMapping("/logmeout")
	public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response,
			@RequestParam(defaultValue = "false", required = false) boolean all) {
		User user = UserUtil.getUser();
		if (user == null) {
			return ResponseEntity.badRequest().body("User not logged in");
		}
		// Delete Spring Security Session
		request.getSession().invalidate();
		// remove remember me cookie
		response.addCookie(new Cookie("remember-me", null));

		// invalidate all sessions if all is true for this user

		counterService.incrementCounter("loggedOut");

		return ResponseEntity.ok().build();
	}

	@GetMapping("/whoami")
	public ResponseEntity<String> whoami(HttpServletRequest request, HttpServletResponse response) {
		User user = UserUtil.getUser();
		if (user == null) {
			counterService.incrementCounter("NotLoggedInUserOpenedPage");
			return ResponseEntity.ok().body("User not logged in");
		}
		request.getSession();
		counterService.incrementCounter("LoggedInUserOpenedPage");
		return ResponseEntity.ok(user.getHsMail());
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
		if (privateMail != null) {
			counterService.incrementCounter("updatedPrivateMail");
		} else {
			counterService.incrementCounter("deletedPrivateMail");
		}

		return ResponseEntity.ok().build();
	}

	@GetMapping("/api/csrf-token")
	public CsrfToken csrf(CsrfToken token) {
		return token;
	}
}
