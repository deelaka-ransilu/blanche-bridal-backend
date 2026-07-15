CREATE TABLE production_stage_records (
                                          id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                          order_id             UUID          NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
                                          current_stage        VARCHAR(40)   NOT NULL
                                              CHECK (current_stage IN (
                                                                       'DESIGN_FINALIZED','FABRIC_SOURCED_CUT','BASE_STRUCTURE_STITCHED',
                                                                       'DETAILING_EMBELLISHMENT_ADDED','FITTING_ADJUSTMENT','QUALITY_CHECK',
                                                                       'READY_FOR_PICKUP'
                                                  )),
                                          pending_stage        VARCHAR(40)
                                              CHECK (pending_stage IS NULL OR pending_stage IN (
                                                                                                'DESIGN_FINALIZED','FABRIC_SOURCED_CUT','BASE_STRUCTURE_STITCHED',
                                                                                                'DETAILING_EMBELLISHMENT_ADDED','FITTING_ADJUSTMENT','QUALITY_CHECK',
                                                                                                'READY_FOR_PICKUP'
                                                  )),
                                          proposed_by          UUID          REFERENCES users(id) ON DELETE SET NULL,
                                          status               VARCHAR(20)   NOT NULL DEFAULT 'NONE'
                                              CHECK (status IN ('NONE','PENDING_APPROVAL','APPROVED','REJECTED')),
                                          assigned_employee    UUID          REFERENCES users(id) ON DELETE SET NULL,
                                          reviewed_by          UUID          REFERENCES users(id) ON DELETE SET NULL,
                                          notes                TEXT,
                                          created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
                                          updated_at           TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_production_stage_records_order ON production_stage_records(order_id);
CREATE INDEX idx_production_stage_records_assigned_employee ON production_stage_records(assigned_employee);
CREATE INDEX idx_production_stage_records_status ON production_stage_records(status);