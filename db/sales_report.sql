CREATE TABLE admins
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    created_at datetime NULL,
    email      VARCHAR(255) NOT NULL,
    is_active  BIT(1) NULL,
    name       VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE audit_trail
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    action        VARCHAR(255) NOT NULL,
    amount        DECIMAL(12, 2) NULL,
    created_at    datetime NULL,
    `description` LONGTEXT NULL,
    entity_id     BIGINT NULL,
    entity_type   VARCHAR(255) NOT NULL,
    ip_address    VARCHAR(255) NULL,
    new_status    VARCHAR(255) NULL,
    old_status    VARCHAR(255) NULL,
    performed_by  VARCHAR(255) NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE bulk_pricing
(
    id               BIGINT AUTO_INCREMENT NOT NULL,
    created_at       datetime NULL,
    discount_percent DECIMAL(38, 2) NOT NULL,
    is_active        BIT(1) NULL,
    max_quantity     INT NULL,
    min_quantity     INT            NOT NULL,
    product_id       BIGINT         NOT NULL,
    tier_price       DECIMAL(38, 2) NULL,
    business_id      BIGINT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE business
(
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    business_name       VARCHAR(255) NULL,
    business_type       VARCHAR(255) NULL,
    city                VARCHAR(255) NULL,
    country             VARCHAR(255) NULL,
    created_at          datetime NULL,
    `description`       LONGTEXT NULL,
    email               VARCHAR(255) NULL,
    industry            VARCHAR(255) NULL,
    owner_name          VARCHAR(255) NULL,
    password            VARCHAR(255) NULL,
    phone               VARCHAR(255) NULL,
    registration_number VARCHAR(255) NULL,
    state               VARCHAR(255) NULL,
    street              VARCHAR(255) NULL,
    username            VARCHAR(255) NULL,
    zip_code            VARCHAR(255) NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE categories
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    created_at    datetime NULL,
    `description` VARCHAR(255) NULL,
    image_url     VARCHAR(255) NULL,
    is_active     BIT(1) NULL,
    name          VARCHAR(255) NOT NULL,
    business_id   BIGINT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE customers
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    created_at   datetime NULL,
    credit_limit DECIMAL(38, 2) NOT NULL,
    email        VARCHAR(255)   NOT NULL,
    is_active    BIT(1) NULL,
    name         VARCHAR(255)   NOT NULL,
    phone        VARCHAR(255) NULL,
    reg_number   VARCHAR(255) NULL,
    risk_level   ENUM           NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE invoices
(
    id                BIGINT AUTO_INCREMENT NOT NULL,
    balance_due       DECIMAL(12, 2) NULL,
    business_id       BIGINT NULL,
    buyer_business_id BIGINT NULL,
    created_at        datetime NULL,
    discount_amount   DECIMAL(12, 2) NULL,
    due_date          date NULL,
    invoice_number    VARCHAR(255)   NOT NULL,
    notes             LONGTEXT NULL,
    order_id          BIGINT NULL,
    paid_amount       DECIMAL(12, 2) NULL,
    paid_date         datetime NULL,
    status            ENUM           NOT NULL,
    subtotal          DECIMAL(12, 2) NOT NULL,
    tax_amount        DECIMAL(12, 2) NULL,
    tax_rate          DECIMAL(5, 2) NULL,
    total_amount      DECIMAL(12, 2) NOT NULL,
    updated_at        datetime NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE order_items
(
    id               BIGINT AUTO_INCREMENT NOT NULL,
    discount_percent DECIMAL(38, 2) NULL,
    line_total       DECIMAL(38, 2) NULL,
    product_id       BIGINT NULL,
    quantity         INT            NOT NULL,
    unit_price       DECIMAL(38, 2) NOT NULL,
    order_id         BIGINT         NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    approval_type        VARCHAR(255) NULL,
    auto_approval_reason VARCHAR(255) NULL,
    business_id          BIGINT NULL,
    customer_id          BIGINT NULL,
    discount_amount      DECIMAL(38, 2) NULL,
    final_amount         DECIMAL(38, 2) NULL,
    notes                LONGTEXT NULL,
    order_date           datetime NULL,
    order_number         VARCHAR(255)   NOT NULL,
    rejection_reason     VARCHAR(255) NULL,
    status               ENUM           NOT NULL,
    total_amount         DECIMAL(38, 2) NOT NULL,
    updated_at           datetime NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE payments
(
    id               BIGINT AUTO_INCREMENT NOT NULL,
    amount           DECIMAL(12, 2) NOT NULL,
    created_at       datetime NULL,
    invoice_id       BIGINT NULL,
    notes            LONGTEXT NULL,
    payment_date     datetime NULL,
    payment_method   ENUM           NOT NULL,
    payment_number   VARCHAR(255)   NOT NULL,
    processed_by     VARCHAR(255) NULL,
    reference_number VARCHAR(255) NULL,
    status           ENUM           NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE products
(
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    base_price          DECIMAL(38, 2) NOT NULL,
    category            VARCHAR(255) NULL,
    created_at          datetime NULL,
    `description`       LONGTEXT NULL,
    image_url           VARCHAR(255) NULL,
    is_active           BIT(1) NULL,
    is_deleted          BIT(1) NULL,
    moq                 INT            NOT NULL,
    name                VARCHAR(255)   NOT NULL,
    sku                 VARCHAR(255)   NOT NULL,
    stock               INT NULL,
    unit                VARCHAR(255) NULL,
    updated_at          datetime NULL,
    business_id         BIGINT NULL,
    image_url2          LONGTEXT NULL,
    last_updated_by     VARCHAR(255) NULL,
    low_stock_threshold INT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE reviews
(
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    comment              LONGTEXT NULL,
    created_at           datetime NULL,
    helpful_count        INT NULL,
    is_verified_purchase BIT(1) NULL,
    product_id           BIGINT NULL,
    quality_rating       INT NULL,
    rating               INT NOT NULL,
    reviewer_business_id BIGINT NULL,
    reviewer_name        VARCHAR(255) NULL,
    seller_responded_at  datetime NULL,
    seller_response      LONGTEXT NULL,
    shipping_rating      INT NULL,
    updated_at           datetime NULL,
    value_rating         INT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE rfqs
(
    id                 BIGINT AUTO_INCREMENT NOT NULL,
    buyer_business_id  BIGINT NULL,
    created_at         datetime NULL,
    message            LONGTEXT NULL,
    offered_discount   DECIMAL(5, 2) NULL,
    offered_price      DECIMAL(12, 2) NULL,
    offered_total      DECIMAL(12, 2) NULL,
    order_id           BIGINT NULL,
    product_id         BIGINT NULL,
    requested_quantity INT          NOT NULL,
    responded_at       datetime NULL,
    rfq_number         VARCHAR(255) NOT NULL,
    seller_business_id BIGINT NULL,
    seller_notes       LONGTEXT NULL,
    status             VARCHAR(255) NOT NULL,
    updated_at         datetime NULL,
    valid_until        date NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

CREATE TABLE users
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    created_at datetime NULL,
    email      VARCHAR(255) NOT NULL,
    risk_level ENUM         NOT NULL,
    username   VARCHAR(255) NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (id)
);

ALTER TABLE admins
    ADD CONSTRAINT UK47bvqemyk6vlm0w7crc3opdd4 UNIQUE (email);

ALTER TABLE customers
    ADD CONSTRAINT UK8bvfj2bqi39hmontpdtdq8oly UNIQUE (reg_number);

ALTER TABLE payments
    ADD CONSTRAINT UKc6nxg52ow66u8ut91bytspy64 UNIQUE (payment_number);

ALTER TABLE products
    ADD CONSTRAINT UKfhmd06dsmj6k0n90swsh8ie9g UNIQUE (sku);

ALTER TABLE rfqs
    ADD CONSTRAINT UKfmbwk1b7aiu831255dy7y7i2h UNIQUE (rfq_number);

ALTER TABLE business
    ADD CONSTRAINT UKktb1t97n9aeupitfjsdpjcmuq UNIQUE (email);

ALTER TABLE invoices
    ADD CONSTRAINT UKl1x55mfsay7co0r3m9ynvipd5 UNIQUE (invoice_number);

ALTER TABLE business
    ADD CONSTRAINT UKllnrx427rqh3qkoxqmmacxmrw UNIQUE (username);

ALTER TABLE orders
    ADD CONSTRAINT UKnthkiu7pgmnqnu86i2jyoe2v7 UNIQUE (order_number);

ALTER TABLE users
    ADD CONSTRAINT UKr43af9ap4edm43mmtq01oddj6 UNIQUE (username);

ALTER TABLE categories
    ADD CONSTRAINT UKt8o6pivur7nn124jehx7cygw5 UNIQUE (name);

ALTER TABLE invoices
    ADD CONSTRAINT FK21val3x76rfggage4pg5cwmuh FOREIGN KEY (buyer_business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FK21val3x76rfggage4pg5cwmuh ON invoices (buyer_business_id);

ALTER TABLE invoices
    ADD CONSTRAINT FK4ko3y00tkkk2ya3p6wnefjj2f FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE NO ACTION;

CREATE INDEX FK4ko3y00tkkk2ya3p6wnefjj2f ON invoices (order_id);

ALTER TABLE orders
    ADD CONSTRAINT FK6yv9sbeyxjkhkrdb2opee19tg FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FK6yv9sbeyxjkhkrdb2opee19tg ON orders (business_id);

ALTER TABLE products
    ADD CONSTRAINT FKauoh901lilsjba0bkqchky0m1 FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKauoh901lilsjba0bkqchky0m1 ON products (business_id);

ALTER TABLE order_items
    ADD CONSTRAINT FKbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE NO ACTION;

CREATE INDEX FKbioxgbv59vetrxe0ejfubep1w ON order_items (order_id);

ALTER TABLE invoices
    ADD CONSTRAINT FKfgr7cv1s1hlnviy7380gf0e8t FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKfgr7cv1s1hlnviy7380gf0e8t ON invoices (business_id);

ALTER TABLE reviews
    ADD CONSTRAINT FKfxos5jr8gtwf0ldsuyvpuon1h FOREIGN KEY (reviewer_business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKfxos5jr8gtwf0ldsuyvpuon1h ON reviews (reviewer_business_id);

ALTER TABLE rfqs
    ADD CONSTRAINT FKhiu9qmbq61r63s8kwegw0y77y FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE NO ACTION;

CREATE INDEX FKhiu9qmbq61r63s8kwegw0y77y ON rfqs (product_id);

ALTER TABLE rfqs
    ADD CONSTRAINT FKi9ccgjrw82yuqjs8lir1itld6 FOREIGN KEY (buyer_business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKi9ccgjrw82yuqjs8lir1itld6 ON rfqs (buyer_business_id);

ALTER TABLE categories
    ADD CONSTRAINT FKko9recucddispyq1tr9m4d023 FOREIGN KEY (business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKko9recucddispyq1tr9m4d023 ON categories (business_id);

ALTER TABLE rfqs
    ADD CONSTRAINT FKnty2x89scl0fffehnie5s2fpe FOREIGN KEY (seller_business_id) REFERENCES business (id) ON DELETE NO ACTION;

CREATE INDEX FKnty2x89scl0fffehnie5s2fpe ON rfqs (seller_business_id);

ALTER TABLE order_items
    ADD CONSTRAINT FKocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE NO ACTION;

CREATE INDEX FKocimc7dtr037rh4ls4l95nlfi ON order_items (product_id);

ALTER TABLE reviews
    ADD CONSTRAINT FKpl51cejpw4gy5swfar8br9ngi FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE NO ACTION;

CREATE INDEX FKpl51cejpw4gy5swfar8br9ngi ON reviews (product_id);

ALTER TABLE orders
    ADD CONSTRAINT FKpxtb8awmi0dk6smoh2vp1litg FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE NO ACTION;

CREATE INDEX FKpxtb8awmi0dk6smoh2vp1litg ON orders (customer_id);

ALTER TABLE payments
    ADD CONSTRAINT FKrbqec6be74wab8iifh8g3i50i FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE NO ACTION;

CREATE INDEX FKrbqec6be74wab8iifh8g3i50i ON payments (invoice_id);