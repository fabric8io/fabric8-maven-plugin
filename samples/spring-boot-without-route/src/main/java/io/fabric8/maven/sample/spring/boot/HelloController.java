package io.fabric8.maven.sample.spring.boot;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping("/")
    public String index() {
        return "Demo of Fabric8 Maven Plugin !!";
    }

}
