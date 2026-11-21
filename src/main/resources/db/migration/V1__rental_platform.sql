CREATE TABLE admin_user (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_admin_username(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE app_user (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 display_name VARCHAR(50) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL, UNIQUE KEY uk_user_username(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE apartment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL, city VARCHAR(50) NOT NULL, monthly_rent INT NOT NULL,
 address VARCHAR(200) NOT NULL DEFAULT '', description TEXT NULL,
 image_url VARCHAR(300) NOT NULL DEFAULT '/images/apartment.svg',
 CONSTRAINT chk_apartment_rent CHECK(monthly_rent>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE room (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, apartment_id BIGINT NOT NULL,
 room_number VARCHAR(20) NOT NULL, monthly_rent INT NOT NULL,
 published BOOLEAN NOT NULL DEFAULT FALSE,
 title VARCHAR(120) NOT NULL DEFAULT '', area DECIMAL(8,2) NOT NULL DEFAULT 25.00,
 bedrooms INT NOT NULL DEFAULT 1, orientation VARCHAR(20) NOT NULL DEFAULT '南',
 description TEXT NULL, image_url VARCHAR(300) NOT NULL DEFAULT '/images/room.svg',
 tags VARCHAR(500) NOT NULL DEFAULT '',
 UNIQUE KEY uk_room_apartment_number(apartment_id,room_number),
 KEY idx_room_listing(published,monthly_rent,id),
 CONSTRAINT fk_room_apartment FOREIGN KEY(apartment_id) REFERENCES apartment(id) ON DELETE RESTRICT,
 CONSTRAINT chk_room_rent CHECK(monthly_rent>0),
 CONSTRAINT chk_room_area CHECK(area>0),
 CONSTRAINT chk_room_bedrooms CHECK(bedrooms BETWEEN 0 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment_slot (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, room_id BIGINT NOT NULL,
 start_time DATETIME(6) NOT NULL, end_time DATETIME(6) NOT NULL,
 capacity INT NOT NULL, reserved_count INT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL,
 KEY idx_slot_room_time(room_id,start_time,id),
 CONSTRAINT fk_slot_room FOREIGN KEY(room_id) REFERENCES room(id) ON DELETE RESTRICT,
 CONSTRAINT chk_slot_time CHECK(start_time<end_time),
 CONSTRAINT chk_slot_capacity CHECK(capacity>0 AND reserved_count>=0 AND reserved_count<=capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, room_id BIGINT NOT NULL,
 slot_id BIGINT NULL, user_id BIGINT NULL,
 contact_name VARCHAR(50) NOT NULL, contact_phone VARCHAR(11) NOT NULL,
 appointment_time DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME(6) NOT NULL,
 KEY idx_appointment_room_time(room_id,appointment_time,id),
 KEY idx_appointment_status_time(status,appointment_time,id),
 KEY idx_appointment_user_time(user_id,appointment_time,id),
 CONSTRAINT fk_appointment_room FOREIGN KEY(room_id) REFERENCES room(id) ON DELETE RESTRICT,
 CONSTRAINT fk_appointment_slot FOREIGN KEY(slot_id) REFERENCES appointment_slot(id) ON DELETE RESTRICT,
 CONSTRAINT fk_appointment_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT chk_appointment_status CHECK(status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment_request (
 request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 appointment_id BIGINT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_request_appointment(appointment_id),
 CONSTRAINT fk_request_appointment FOREIGN KEY(appointment_id) REFERENCES appointment(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment_review_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 appointment_id BIGINT NOT NULL, admin_id BIGINT NOT NULL,
 from_status VARCHAR(20) NOT NULL, to_status VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_review_appointment(appointment_id), KEY idx_review_admin_time(admin_id,created_at,id),
 CONSTRAINT fk_review_appointment FOREIGN KEY(appointment_id) REFERENCES appointment(id) ON DELETE RESTRICT,
 CONSTRAINT fk_review_admin FOREIGN KEY(admin_id) REFERENCES admin_user(id) ON DELETE RESTRICT,
 CONSTRAINT chk_review_transition CHECK(from_status='PENDING' AND to_status IN ('CONFIRMED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE favorite (
 user_id BIGINT NOT NULL, room_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(user_id,room_id),
 CONSTRAINT fk_favorite_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_favorite_room FOREIGN KEY(room_id) REFERENCES room(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE lease_contract (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, room_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 start_date DATE NOT NULL, end_date DATE NOT NULL,
 monthly_rent DECIMAL(10,2) NOT NULL, deposit DECIMAL(10,2) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 KEY idx_lease_room_period(room_id,status,start_date,end_date),
 KEY idx_lease_user(user_id,created_at,id),
 CONSTRAINT fk_lease_room FOREIGN KEY(room_id) REFERENCES room(id) ON DELETE RESTRICT,
 CONSTRAINT fk_lease_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT chk_lease_dates CHECK(start_date<end_date),
 CONSTRAINT chk_lease_money CHECK(monthly_rent>0 AND deposit>=0),
 CONSTRAINT chk_lease_status CHECK(status IN ('DRAFT','ACTIVE','TERMINATED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE bill (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, lease_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 period VARCHAR(7) NOT NULL, amount DECIMAL(10,2) NOT NULL, due_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'UNPAID', paid_at DATETIME(6) NULL,
 payment_channel VARCHAR(20) NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_bill_lease_period(lease_id,period), KEY idx_bill_user_status(user_id,status,due_date,id),
 CONSTRAINT fk_bill_lease FOREIGN KEY(lease_id) REFERENCES lease_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_bill_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT chk_bill_amount CHECK(amount>0),
 CONSTRAINT chk_bill_status CHECK(status IN ('UNPAID','PAID','OVERDUE','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE notification (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, content VARCHAR(500) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME(6) NOT NULL,
 KEY idx_notification_user(user_id,created_at,id),
 CONSTRAINT fk_notification_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
