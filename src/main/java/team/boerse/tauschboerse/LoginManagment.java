package team.boerse.tauschboerse;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.boerse.tauschboerse.captcha.CaptchaController;
import team.boerse.tauschboerse.config.CustomTokenFilter;
import team.boerse.tauschboerse.mail.MailUtils;

@EnableScheduling
@RestController
public class LoginManagment {

	@Autowired
	UserRepository userRepository;

	@Autowired
	CaptchaController captchaController;

	@Autowired
	KalenderRepository kalenderRepository;

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

	@GetMapping("/requestLogin")
	public ResponseEntity<String> requestLogin(@RequestParam String hsMail, @RequestParam String pow,
			HttpServletRequest request,
			HttpServletResponse response) {

		if (!isCorrectMailFormat(hsMail)) {
			return ResponseEntity.badRequest().body("Invalid mail format");
		}

		String payload = "{\"payload\":\"" + pow + "\"}";
		long currentTime = System.currentTimeMillis();
		if (!captchaController.checkSolution(payload)) {
			return ResponseEntity.badRequest().body("Invalid captcha");
		}
		System.out.println("Checking captcha took " + (System.currentTimeMillis() - currentTime) + " ms");

		User user = userRepository.findByHsMail(hsMail).orElse(null);
		if (user != null && (user.isBanned() != null && user.isBanned())) {
			return ResponseEntity.status(403).body("User is banned: " + user.getBanReason());
		}

		String token = UUID.randomUUID().toString();
		tokens.put(token, hsMail);
		removeTimerForTokens.put(token, System.currentTimeMillis() + 1000 * 60 * 10);

		boolean calendarExists = user != null && kalenderRepository.findByUserId(user.getId()) != null;

		MailUtils.sendMail(hsMail, null, "Anmeldelink für die Tauschbörse",
				"Klicke hier um dich anzumelden:\n<a href='" + domain + "/?logintoken=" + token + "'>" + domain
						+ "/?logintoken=" + token + "</a>"
						+ (!calendarExists
								? "\n\nDu benötigst eine Kalenderdatei. Lade sie hier direkt herunter:\n<a href='https://aor.cs.hs-rm.de/plans.ics?user_plan=true'>plans.ics herunterladen</a>"
								: ""));

		return ResponseEntity.ok().build();
	}

	private boolean isCorrectMailFormat(String hsMail) {
		String[] adress = hsMail.split("@");
		return adress[0].contains(".") && adress[1].equals("student.hs-rm.de");
	}

	@GetMapping("/login")
	public ResponseEntity<String> login(@RequestParam String logintoken, HttpServletResponse response) {

		String hsMail = tokens.get(logintoken);
		if (hsMail == null) {
			return ResponseEntity.badRequest().body("Invalid token");
		}
		User user = userRepository.findByHsMail(hsMail).orElse(null);
		String accessToken = UUID.randomUUID().toString();
		boolean newUser = false;
		if (user == null) {
			newUser = true;
			user = new User(hsMail, null, null, null);
		}
		user.getAccessToken().add(accessToken);
		Cookie cookie = new Cookie("sessionToken", accessToken);
		cookie.setMaxAge(60 * 60 * 24 * 30);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		response.setHeader("Set-Cookie", UserUtil.convertCookieToSetCookie(cookie));
		tokens.remove(logintoken);
		removeTimerForTokens.remove(logintoken);
		userRepository.save(user);
		logger.info(String.format("User %s logged in", hsMail));
		if (newUser) {
			logger.info(String.format("New user %s created", hsMail));
			return ResponseEntity.ok().body("New user created");
		}

		return ResponseEntity.ok().build();
	}

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
		if (all) {
			String token = CustomTokenFilter.extractToken(request);
			user.getAccessToken().removeIf(e -> !e.equals(token));
		} else {
			user.getAccessToken().remove(CustomTokenFilter.extractToken(request));
			Cookie cookie = new Cookie("sessionToken", "");
			cookie.setMaxAge(0);
			cookie.setHttpOnly(true);
			cookie.setPath("/");
			response.setHeader("Set-Cookie", UserUtil.convertCookieToSetCookie(cookie));
		}
		userRepository.save(user);

		return ResponseEntity.ok().build();
	}

	@GetMapping("/whoami")
	public String whoami() {
		User user = UserUtil.getUser();
		if (user == null) {
			return "Not logged in";
		}
		return user.getHsMail();
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
		return ResponseEntity.ok().build();
	}

}
