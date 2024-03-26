package team.boerse.tauschboerse;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import team.boerse.tauschboerse.mail.MailUtils;

@EnableScheduling
@RestController
public class WebEndpointExample {

	@Autowired
	UserRepository userRepository;

	HashMap<String, String> tokens = new HashMap<>();
	HashMap<String, Long> removeTimerForTokens = new HashMap<>();
	HashMap<InetAddress, Integer> requestCounter = new HashMap<>();
	HashMap<InetAddress, Long> lastRequest = new HashMap<>();

	@Value("${amountOfRequest:5}")
	String amountOfRequest = "5";

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

	@GetMapping("/requestLogin")
	public ResponseEntity<String> requestLogin(@RequestParam String hsMail, HttpServletRequest request,
			HttpServletResponse response) {

		if (!isCorrectMailFormat(hsMail)) {
			return ResponseEntity.badRequest().body("Invalid mail format");
		}

		try {
			InetAddress ip = InetAddress.getByName(request.getRemoteAddr());
			if (requestCounter.containsKey(ip)) {
				requestCounter.put(ip, requestCounter.get(ip) + 1);
				lastRequest.put(ip, System.currentTimeMillis());
				if (requestCounter.get(ip) > Integer.parseInt(amountOfRequest)) {
					if (lastRequest.get(ip) + 1000 * 60 * 60 * 6 > System.currentTimeMillis()) {
						return ResponseEntity.badRequest().body("Too many requests");
					}
				}
			} else {
				requestCounter.put(ip, 1);
			}

		} catch (UnknownHostException e) {
			return ResponseEntity.badRequest().body("Invalid IP");
		}

		String token = UUID.randomUUID().toString();
		tokens.put(token, hsMail);
		removeTimerForTokens.put(token, System.currentTimeMillis() + 1000 * 60 * 10);

		MailUtils.sendMail(hsMail, "Login", "Click here to login:\n http://localhost:5173/?logintoken=" + token);

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
			user = new User(hsMail, null, accessToken, null, null);
		}
		user.setAccessToken(accessToken);
		Cookie cookie = new Cookie("sessionToken", user.getAccessToken());
		cookie.setMaxAge(60 * 60 * 24);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		response.setHeader("Set-Cookie", UserUtil.convertCookieToSetCookie(cookie));
		tokens.remove(logintoken);
		removeTimerForTokens.remove(logintoken);
		userRepository.save(user);
		if (newUser) {
			return ResponseEntity.ok().body("New user created");
		}

		return ResponseEntity.ok().build();
	}

	@GetMapping("/logmeout")
	public ResponseEntity<String> logout(HttpServletResponse response) {
		User user = UserUtil.getUser();
		if (user == null) {
			return ResponseEntity.badRequest().body("User not logged in");
		}
		user.setAccessToken(null);
		userRepository.save(user);

		Cookie cookie = new Cookie("sessionToken", "");
		cookie.setMaxAge(0);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		response.setHeader("Set-Cookie", UserUtil.convertCookieToSetCookie(cookie));
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

}
