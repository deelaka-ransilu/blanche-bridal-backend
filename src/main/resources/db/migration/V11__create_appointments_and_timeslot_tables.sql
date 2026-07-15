-- V11__create_appointments_and_timeslot_tables.sql

CREATE TABLE time_slot_config (
                                  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                  day_of_week  INTEGER       NOT NULL,
                                  slot_time    VARCHAR(10)   NOT NULL,
                                  is_active    BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_timeslot_day ON time_slot_config(day_of_week);

CREATE TABLE appointments (
                              id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id           UUID          REFERENCES users(id) ON DELETE SET NULL,
                              product_id        UUID          REFERENCES products(id) ON DELETE SET NULL,
                              appointment_date  DATE          NOT NULL,
                              time_slot         VARCHAR(10)   NOT NULL,
                              type              VARCHAR(20)   NOT NULL
                                  CHECK (type IN ('FITTING', 'RENTAL_PICKUP', 'PURCHASE')),
                              status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
                              google_event_id   VARCHAR(255),
                              notes             TEXT,
                              created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
                              updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointments_user ON appointments(user_id);
CREATE INDEX idx_appointments_product ON appointments(product_id);
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_appointments_status ON appointments(status);