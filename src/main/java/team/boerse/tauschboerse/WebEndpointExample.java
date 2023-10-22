package team.boerse.tauschboerse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebEndpointExample {

	@GetMapping("/hello")
	public String hello(@RequestParam(defaultValue = "World!") String name) {
		return "Hello " + name;
	}
}
