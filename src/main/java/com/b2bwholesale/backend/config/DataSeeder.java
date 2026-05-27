package com.b2bwholesale.backend.config;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.*;
import com.b2bwholesale.backend.repositories.AdminRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository,
                                   BusinessRepository businessRepository,
                                   ProductRepository productRepository,
                                   CustomerRepository customerRepository,
                                   BulkPricingRepository bulkPricingRepository,
                                   AdminRepository adminRepository) {
        return args -> {
            // Seed default admin
            if (adminRepository.count() == 0) {
                Admin admin = new Admin();
                admin.setName("System Admin");
                admin.setEmail("admin@gmail.com");
                admin.setPassword("123admin");
                adminRepository.save(admin);
                System.out.println("=== Default admin created: admin@gmail.com / 123admin ===");
            }

            // Seed admin as business too (so admin can login from normal login page)
            if (businessRepository.findByEmail("admin@gmail.com").isEmpty()) {
                Business adminBiz = new Business();
                adminBiz.setName("System Admin");
                adminBiz.setEmail("admin@gmail.com");
                adminBiz.setPassword("123admin");
                adminBiz.setBusinessType("Admin");
                adminBiz.setUsername("admin");
                businessRepository.save(adminBiz);
                System.out.println("=== Admin business account created ===");
            }

            if (customerRepository.count() == 0) {
                // 1. Seed Users
                if (userRepository.count() == 0) {
                    User testUser = new User(null, "testuser", "testuser@example.com", RiskLevel.LOW, null);
                    User riskyUser = new User(null, "riskyuser", "riskyuser@example.com", RiskLevel.HIGH, null);
                    userRepository.saveAll(List.of(testUser, riskyUser));
                }

                // 2. Seed Customers (Buyers) - 3 customers with different risk levels
                Customer customer1 = new Customer();
                customer1.setName("Metro Retail Group");
                customer1.setRegNumber("REG-001");
                customer1.setEmail("metro@example.com");
                customer1.setPhone("+94-77-1234567");
                customer1.setCreditLimit(new BigDecimal("50000.00"));
                customer1.setRiskLevel(RiskLevel.LOW);

                Customer customer2 = new Customer();
                customer2.setName("QuickBuy Electronics");
                customer2.setRegNumber("REG-002");
                customer2.setEmail("quickbuy@example.com");
                customer2.setPhone("+94-77-2345678");
                customer2.setCreditLimit(new BigDecimal("25000.00"));
                customer2.setRiskLevel(RiskLevel.MEDIUM);

                Customer customer3 = new Customer();
                customer3.setName("Risky Traders Inc.");
                customer3.setRegNumber("REG-003");
                customer3.setEmail("riskytraders@example.com");
                customer3.setPhone("+94-77-3456789");
                customer3.setCreditLimit(new BigDecimal("5000.00"));
                customer3.setRiskLevel(RiskLevel.HIGH);

                customerRepository.saveAll(List.of(customer1, customer2, customer3));

                // 3. Seed Businesses (Vendors/Sellers)
                if (businessRepository.count() == 0) {
                    Business acmeCorp = new Business();
                    acmeCorp.setName("Acme Corp");
                    acmeCorp.setEmail("acme@example.com");
                    acmeCorp.setRegNumber("BIZ-001");
                    acmeCorp.setBusinessType("Electronics");
                    acmeCorp.setCity("Colombo");

                    Business techSupplies = new Business();
                    techSupplies.setName("Tech Supplies Co.");
                    techSupplies.setEmail("tech@example.com");
                    techSupplies.setRegNumber("BIZ-002");
                    techSupplies.setBusinessType("Tools & Hardware");
                    techSupplies.setCity("Kandy");

                    List<Business> businesses = businessRepository.saveAll(List.of(acmeCorp, techSupplies));

                    // 4. Seed Products linked to Businesses
                    Product p1 = new Product();
                    p1.setName("Acme Widget A");
                    p1.setDescription("High-quality industrial widget for manufacturing");
                    p1.setSku("WID-A-001");
                    p1.setBasePrice(new BigDecimal("15.99"));
                    p1.setStock(500);
                    p1.setMoq(10);
                    p1.setUnit("piece");
                    p1.setCategory("Electronics");
                    p1.setBusiness(businesses.get(0));

                    Product p2 = new Product();
                    p2.setName("Acme Widget B");
                    p2.setDescription("Premium widget with extended warranty");
                    p2.setSku("WID-B-002");
                    p2.setBasePrice(new BigDecimal("25.50"));
                    p2.setStock(200);
                    p2.setMoq(5);
                    p2.setUnit("piece");
                    p2.setCategory("Electronics");
                    p2.setBusiness(businesses.get(0));

                    Product p3 = new Product();
                    p3.setName("Tech Gadget Pro");
                    p3.setDescription("Professional-grade gadget for enterprise use");
                    p3.setSku("TGP-001");
                    p3.setBasePrice(new BigDecimal("199.99"));
                    p3.setStock(100);
                    p3.setMoq(2);
                    p3.setUnit("piece");
                    p3.setCategory("Tools & Hardware");
                    p3.setBusiness(businesses.get(1));

                    List<Product> products = productRepository.saveAll(List.of(p1, p2, p3));

                    // 5. Seed Bulk Pricing Tiers for each product
                    if (bulkPricingRepository.count() == 0) {
                        // Product 1: Acme Widget A ($15.99 base)
                        // Tier 1: 10-49 units = 5% off
                        BulkPricing bp1a = new BulkPricing();
                        bp1a.setProductId(products.get(0).getId());
                        bp1a.setMinQuantity(10);
                        bp1a.setMaxQuantity(49);
                        bp1a.setDiscountPercent(new BigDecimal("5.00"));

                        // Tier 2: 50-99 units = 10% off
                        BulkPricing bp1b = new BulkPricing();
                        bp1b.setProductId(products.get(0).getId());
                        bp1b.setMinQuantity(50);
                        bp1b.setMaxQuantity(99);
                        bp1b.setDiscountPercent(new BigDecimal("10.00"));

                        // Tier 3: 100+ units = 15% off
                        BulkPricing bp1c = new BulkPricing();
                        bp1c.setProductId(products.get(0).getId());
                        bp1c.setMinQuantity(100);
                        bp1c.setMaxQuantity(null);
                        bp1c.setDiscountPercent(new BigDecimal("15.00"));

                        // Product 2: Acme Widget B ($25.50 base)
                        // Tier 1: 5-24 units = 3% off
                        BulkPricing bp2a = new BulkPricing();
                        bp2a.setProductId(products.get(1).getId());
                        bp2a.setMinQuantity(5);
                        bp2a.setMaxQuantity(24);
                        bp2a.setDiscountPercent(new BigDecimal("3.00"));

                        // Tier 2: 25-49 units = 8% off
                        BulkPricing bp2b = new BulkPricing();
                        bp2b.setProductId(products.get(1).getId());
                        bp2b.setMinQuantity(25);
                        bp2b.setMaxQuantity(49);
                        bp2b.setDiscountPercent(new BigDecimal("8.00"));

                        // Tier 3: 50+ units = fixed $22.00/unit
                        BulkPricing bp2c = new BulkPricing();
                        bp2c.setProductId(products.get(1).getId());
                        bp2c.setMinQuantity(50);
                        bp2c.setMaxQuantity(null);
                        bp2c.setDiscountPercent(new BigDecimal("12.00"));
                        bp2c.setTierPrice(new BigDecimal("22.00"));

                        // Product 3: Tech Gadget Pro ($199.99 base)
                        // Tier 1: 2-9 units = 2% off
                        BulkPricing bp3a = new BulkPricing();
                        bp3a.setProductId(products.get(2).getId());
                        bp3a.setMinQuantity(2);
                        bp3a.setMaxQuantity(9);
                        bp3a.setDiscountPercent(new BigDecimal("2.00"));

                        // Tier 2: 10-24 units = 7% off
                        BulkPricing bp3b = new BulkPricing();
                        bp3b.setProductId(products.get(2).getId());
                        bp3b.setMinQuantity(10);
                        bp3b.setMaxQuantity(24);
                        bp3b.setDiscountPercent(new BigDecimal("7.00"));

                        // Tier 3: 25+ units = 12% off
                        BulkPricing bp3c = new BulkPricing();
                        bp3c.setProductId(products.get(2).getId());
                        bp3c.setMinQuantity(25);
                        bp3c.setMaxQuantity(null);
                        bp3c.setDiscountPercent(new BigDecimal("12.00"));

                        bulkPricingRepository.saveAll(List.of(
                                bp1a, bp1b, bp1c,
                                bp2a, bp2b, bp2c,
                                bp3a, bp3b, bp3c
                        ));
                    }
                }

                System.out.println("=== Sample data seeded: 3 Customers, 2 Businesses, 3 Products, 9 Pricing Tiers ===");
            }
        };
    }
}
