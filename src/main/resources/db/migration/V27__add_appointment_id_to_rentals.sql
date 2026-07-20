-- V27__add_appointment_id_to_rentals.sql
ALTER TABLE rentals ADD COLUMN appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL;
CREATE INDEX idx_rentals_appointment ON rentals(appointment_id);