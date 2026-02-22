package com.b2bwholesale.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;


//Disable JPA auto configuration
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class}
)
public class B2BWholesaleManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(B2BWholesaleManagementSystemApplication.class, args);
    }

}
