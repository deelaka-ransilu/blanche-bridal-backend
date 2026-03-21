-- =============================================
-- Default ADMIN user + employee profile
-- Password: Admin@1234 — change after first login!
-- =============================================
INSERT INTO users (
    public_id, full_name, email,
    password_hash, role,
    email_verified, profile_completed, is_active
) VALUES (
             'usr_ADMIN00001',
             'Shop Admin',
             'admin@bridalshop.com',
             '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
             'ADMIN',
             TRUE, TRUE, TRUE
         );

INSERT INTO employee_profiles (user_id, job_title, is_active)
VALUES (1, 'System Administrator', TRUE);