CREATE TABLE customer_profiles (
                                   id                 UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                                   customer_id        UUID      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                                   admin_notes        TEXT,
                                   design_image_urls  TEXT[]    NOT NULL DEFAULT '{}',
                                   created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                   updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE customer_measurements (
                                       id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                       public_id              VARCHAR(20)   NOT NULL UNIQUE,
                                       customer_id            UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       recorded_by            UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                                       notes                  TEXT,
                                       measured_at            TIMESTAMP     NOT NULL,
                                       created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
                                       updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
                                       height_with_shoes      NUMERIC(5,2),
                                       hollow_to_hem          NUMERIC(5,2),
                                       full_bust              NUMERIC(5,2),
                                       under_bust             NUMERIC(5,2),
                                       natural_waist          NUMERIC(5,2),
                                       full_hip               NUMERIC(5,2),
                                       shoulder_width         NUMERIC(5,2),
                                       torso_length           NUMERIC(5,2),
                                       thigh_circumference    NUMERIC(5,2),
                                       waist_to_knee          NUMERIC(5,2),
                                       waist_to_floor         NUMERIC(5,2),
                                       armhole                NUMERIC(5,2),
                                       bicep_circumference    NUMERIC(5,2),
                                       elbow_circumference    NUMERIC(5,2),
                                       wrist_circumference    NUMERIC(5,2),
                                       sleeve_length          NUMERIC(5,2),
                                       upper_bust             NUMERIC(5,2),
                                       bust_apex_distance     NUMERIC(5,2),
                                       shoulder_to_bust_point NUMERIC(5,2),
                                       neck_circumference     NUMERIC(5,2),
                                       train_length           NUMERIC(5,2)
);

CREATE INDEX idx_customer_measurements_customer ON customer_measurements(customer_id);