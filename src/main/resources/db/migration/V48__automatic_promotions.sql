ALTER TABLE checkout.discounts
    ADD COLUMN automatic BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE checkout.discounts
    ALTER COLUMN code DROP NOT NULL;

DROP INDEX checkout.discounts_code_uq;
CREATE UNIQUE INDEX discounts_code_uq ON checkout.discounts (UPPER(code)) WHERE code IS NOT NULL;

ALTER TABLE checkout.discounts
    ADD CONSTRAINT chk_discount_code_or_auto CHECK (automatic = TRUE OR code IS NOT NULL);
