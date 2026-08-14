ALTER TABLE time_slot_config
    ADD CONSTRAINT uq_time_slot_config_day_slot UNIQUE (day_of_week, slot_time);