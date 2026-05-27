package com.b2bwholesale.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class B2BWholesaleManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(B2BWholesaleManagementSystemApplication.class, args);
    }

}
