CREATE TABLE custom_quotes (
                               id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                               custom_design_request_id UUID         NOT NULL REFERENCES custom_design_requests(id) ON DELETE CASCADE,
                               version                 INT           NOT NULL,
                               fabric_amount           NUMERIC(10,2) NOT NULL,
                               labor_amount            NUMERIC(10,2) NOT NULL,
                               embellishment_amount    NUMERIC(10,2) NOT NULL,
                               alterations_amount      NUMERIC(10,2) NOT NULL,
                               other_amount            NUMERIC(10,2) NOT NULL,
                               other_note              TEXT,
                               total_amount            NUMERIC(10,2) NOT NULL,
                               split_type              VARCHAR(20)   NOT NULL
                                   CHECK (split_type IN ('FIFTY_FIFTY', 'FULL_UPFRONT')),
                               status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                               rejection_reason        TEXT,
                               valid_until             TIMESTAMP     NOT NULL,
                               created_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_custom_quotes_design_request ON custom_quotes(custom_design_request_id);