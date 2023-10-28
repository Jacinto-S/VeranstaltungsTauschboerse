package team.boerse.tauschboerse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebEndpointExample {

	@Autowired
	UserRepository userRepository;

	@GetMapping("/hello")
	public String hello(@RequestParam(defaultValue = "World!") String name) {

		userRepository.deleteAll();

		User myuser = userRepository.findByHsMail("privateMail@student.hs-rm.de").orElse(null);
		if (myuser == null) {
			User user = new User("privateMail@student.hs-rm.de", "", "Max", "Mustermann", "Informatik", "", false,
					false, false, false, "");
			userRepository.save(user);
			myuser = userRepository.findByHsMail("privateMail@student.hs-rm.de").get();
		}
		if (myuser != null) {
			return "Hello " + myuser.getVorname() + " " + myuser.getNachname();
		}

		return "Hello, Something went wrong!";
	}
}
