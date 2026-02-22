package com.b2bwholesale.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class indexController {

    @GetMapping("/api")
    public String hello() {
        return "";
    }
}
