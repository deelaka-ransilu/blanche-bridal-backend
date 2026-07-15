CREATE TABLE inquiries (
                           id UUID PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           email VARCHAR(255) NOT NULL,
                           phone VARCHAR(50),
                           subject VARCHAR(255),
                           message TEXT NOT NULL,
                           image_url VARCHAR(500),
                           status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                               CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inquiries_email ON inquiries (email);
CREATE INDEX idx_inquiries_status ON inquiries (status);