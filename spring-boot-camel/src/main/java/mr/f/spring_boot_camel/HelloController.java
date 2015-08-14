package mr.f.spring_boot_camel;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HelloController {
	@RequestMapping(value = "/rs/hello")
	public String index() {
		return "Hello from REST!";
	}
}
