CREATE TABLE appointments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID REFERENCES users(id) ON DELETE SET NULL,
    product_id       UUID REFERENCES products(id) ON DELETE SET NULL,
    appointment_date DATE NOT NULL,
    time_slot        VARCHAR(10) NOT NULL,
    type             VARCHAR(20) NOT NULL
        CHECK (type IN ('FITTING','RENTAL_PICKUP','PURCHASE')),
    status           VARCHAR(20) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED')),
    google_event_id  VARCHAR(255),
    notes            TEXT,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE time_slot_config (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    day_of_week  INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    slot_time    VARCHAR(10) NOT NULL,
    is_active    BOOLEAN DEFAULT TRUE,
    UNIQUE (day_of_week, slot_time)
);

INSERT INTO time_slot_config (day_of_week, slot_time) VALUES
(1,'10:00'),(1,'11:00'),(1,'12:00'),(1,'14:00'),(1,'15:00'),(1,'16:00'),(1,'17:00'),
(2,'10:00'),(2,'11:00'),(2,'12:00'),(2,'14:00'),(2,'15:00'),(2,'16:00'),(2,'17:00'),
(3,'10:00'),(3,'11:00'),(3,'12:00'),(3,'14:00'),(3,'15:00'),(3,'16:00'),(3,'17:00'),
(4,'10:00'),(4,'11:00'),(4,'12:00'),(4,'14:00'),(4,'15:00'),(4,'16:00'),(4,'17:00'),
(5,'10:00'),(5,'11:00'),(5,'12:00'),(5,'14:00'),(5,'15:00'),(5,'16:00'),(5,'17:00'),
(6,'10:00'),(6,'11:00'),(6,'12:00'),(6,'14:00'),(6,'15:00'),(6,'16:00');