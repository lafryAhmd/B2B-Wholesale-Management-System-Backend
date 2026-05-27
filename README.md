# B2B Wholesale Management System - Backend

This is the backend API for the **B2B Wholesale Management System**, a full-stack wholesale business management platform. The backend is developed using **Spring Boot** and provides REST APIs for business registration, product management, order handling, inventory tracking, invoice generation, payment management, RFQs, reviews, and sales reporting.

---

## Project Overview

The backend system is designed to support wholesale businesses by providing a centralized API for managing business operations. It allows administrators to approve businesses and products, sellers to manage inventory and orders, and buyers to place bulk orders, request quotations, make payments, and view invoices.

---

## Technologies Used

- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Hibernate
- MySQL
- Maven
- Lombok

---

## Main Features

- Business registration and login
- Admin approval for registered businesses
- Product management with approval status
- Product category management
- Bulk pricing management
- Order creation and order status management
- Inventory and stock alert management
- Invoice generation and verification
- Payment recording and refund handling
- Late fee rule management
- RFQ management
- Product and business review management
- Sales report generation

---

## System Modules

### Admin Management

- View all registered businesses
- View pending businesses
- Approve or reject businesses
- Approve or reject products
- Manage late fee rules
- View sales reports

### Business Management

- Register a business account
- Login to the system
- Manage business profile
- View business-related products, orders, invoices, and payments

### Product Management

- Add new products
- Update product details
- Delete products
- Search products
- Filter products by category
- Approve or reject products
- Enable or disable products

### Category Management

- Add product categories
- View all categories
- Update category details
- Delete categories

### Bulk Pricing Management

- Add quantity-based pricing tiers
- Update pricing tiers
- Delete pricing tiers
- Calculate product price based on order quantity

### Order Management

- Create customer orders
- View all orders
- View pending and approved orders
- Approve or reject orders
- View orders by customer
- View orders by business

### Inventory Management

- View inventory details
- Update stock quantity
- Set minimum stock threshold
- View low-stock products
- Track stock movement history
- View stock alert history

### Invoice Management

- Generate invoices for orders
- View invoices
- View invoices by order, business, buyer, or status
- Check overdue invoices
- Cancel invoices
- Sign and verify invoices
- View invoice statistics

### Payment Management

- Pay invoices
- Record manual payments
- View payment records
- Refund payments
- View invoice audit trail

### RFQ Management

RFQ means **Request for Quotation**.

- Create RFQs
- View RFQs
- View RFQs by buyer or seller
- Respond to RFQs
- Accept or reject RFQs

### Review Management

- Add product reviews
- View product reviews
- Filter reviews
- View review summaries
- Add business responses to reviews
- Mark reviews as helpful

### Sales Report Management

- Generate sales reports
- View sales report history
- Track revenue, orders, average order value, and active customers

---

## API Endpoint Summary

### Business APIs

```http
POST /api/business/register
POST /api/business/login