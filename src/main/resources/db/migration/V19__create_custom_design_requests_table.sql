CREATE TABLE custom_design_requests (
                                        id UUID PRIMARY KEY,
                                        appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
                                        occasion_type VARCHAR(20) NOT NULL,
                                        occasion_date DATE NOT NULL,
                                        style_preferences TEXT,
                                        reference_images TEXT,
                                        created_at TIMESTAMP NOT NULL DEFAULT now(),
                                        updated_at TIMESTAMP NOT NULL DEFAULT now()
);