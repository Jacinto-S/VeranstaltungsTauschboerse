package team.boerse.tauschboerse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebEndpointExample {

	@GetMapping("/hello")
	public String hello() {
		return "Hello World!";
	}
}
