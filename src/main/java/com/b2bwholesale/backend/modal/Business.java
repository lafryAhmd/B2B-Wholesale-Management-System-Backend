package com.b2bwholesale.backend.modal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "business")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Business {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false, unique = true)
    private String name;

    private String email;

    private String phone;

    @Column(name = "registration_number", unique = true)
    private String regNumber;

    @Column(name = "business_type")
    private String businessType;

    private String street;

    private String city;

    @Column(name = "zip_code")
    private String zipCode;

    private String username;

    private String password;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "role", nullable = false)
    private String role = "BUSINESS";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
