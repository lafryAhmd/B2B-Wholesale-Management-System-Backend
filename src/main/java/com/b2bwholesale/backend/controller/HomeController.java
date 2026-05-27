package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Business;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class HomeController {

    @Autowired
    private BusinessRepository businessRepository;

    @GetMapping("/")
    public String home() {
        return "homepage";
    }

    @GetMapping("/index.html")
    public String index() {
        return "homepage";
    }

    @GetMapping("/login.html")
    public String login() {
        return "login";
    }

    @GetMapping("/register.html")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerBusiness(@ModelAttribute Business b) {
        businessRepository.save(b);
        return "redirect:/register.html?registered=true";
    }

    @GetMapping("/dashboard.html")
    public String dashboard() {
        return "dashboard";
    }
}
