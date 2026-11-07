ALTER TABLE suspensions
    ALTER COLUMN violation_type DROP NOT NULL,
    ADD CONSTRAINT suspension_requires_violation_type
        CHECK (action <> 'SUSPEND' OR violation_type IS NOT NULL);
