-- List-level percentage-off / markup rules for price lists

ALTER TABLE b2b.price_lists
    ADD COLUMN adjustment_type  VARCHAR(30),
    ADD COLUMN adjustment_value NUMERIC(8, 4),
    ADD CONSTRAINT chk_price_list_adjustment
        CHECK (
            (adjustment_type IS NULL AND adjustment_value IS NULL)
            OR
            (adjustment_type IN ('PERCENTAGE_OFF', 'MARKUP_PERCENTAGE')
             AND adjustment_value IS NOT NULL
             AND adjustment_value > 0)
        );
