-- =============================================
-- Bridal Shop — Phase 1: Auth & User Roles
-- =============================================

CREATE TYPE user_role AS ENUM ('ADMIN', 'EMPLOYEE', 'CUSTOMER');

-- =============================================
-- AUTO-UPDATE FUNCTION (shared by all triggers)
-- =============================================
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================
-- USERS
-- =============================================
CREATE TABLE users (
                       user_id           SERIAL       PRIMARY KEY,
                       public_id         VARCHAR(20)  NOT NULL UNIQUE,
                       full_name         VARCHAR(150) NOT NULL,
                       email             VARCHAR(150) NOT NULL UNIQUE,
                       password_hash     VARCHAR(255),
                       role              user_role    NOT NULL DEFAULT 'CUSTOMER',
                       google_id         VARCHAR(255) UNIQUE,
                       profile_picture   VARCHAR(500),
                       email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
                       profile_completed BOOLEAN      NOT NULL DEFAULT FALSE,
                       is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Only non-UNIQUE columns need manual indexes
-- UNIQUE columns (public_id, email, google_id) already have auto indexes
CREATE INDEX idx_users_role ON users(role);

CREATE TRIGGER update_users_modtime
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- =============================================
-- CUSTOMER PROFILES
-- =============================================
CREATE TABLE customer_profiles (
                                   customer_profile_id SERIAL      PRIMARY KEY,
                                   user_id             INT         NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
                                   phone               VARCHAR(20),
                                   preferred_contact   VARCHAR(20) DEFAULT 'EMAIL',
                                   notes               TEXT,
                                   created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_profiles_user_id ON customer_profiles(user_id);

CREATE TRIGGER update_customer_profiles_modtime
    BEFORE UPDATE ON customer_profiles
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- =============================================
-- EMPLOYEE PROFILES
-- =============================================
CREATE TABLE employee_profiles (
                                   employee_profile_id SERIAL        PRIMARY KEY,
                                   user_id             INT           NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
                                   phone               VARCHAR(20),
                                   job_title           VARCHAR(100),
                                   employment_type     VARCHAR(20),
                                   salary_type         VARCHAR(20),
                                   base_salary         DECIMAL(12,2),
                                   hire_date           DATE,
    -- Separate from users.is_active
    -- users.is_active   = can this person log in?
    -- is_active here    = are they currently employed?
                                   is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
                                   created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employee_profiles_user_id ON employee_profiles(user_id);

CREATE TRIGGER update_employee_profiles_modtime
    BEFORE UPDATE ON employee_profiles
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- =============================================
-- REFRESH TOKENS
-- =============================================
CREATE TABLE refresh_tokens (
                                token_id   SERIAL       PRIMARY KEY,
                                user_id    INT          NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                                token      VARCHAR(500) NOT NULL UNIQUE,
                                expires_at TIMESTAMP    NOT NULL,
                                revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- token column already indexed via UNIQUE constraint
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);