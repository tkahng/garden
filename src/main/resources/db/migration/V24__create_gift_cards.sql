CREATE TABLE checkout.gift_cards (
    id                UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code              VARCHAR(32)    NOT NULL,
    initial_balance   NUMERIC(19,4)  NOT NULL,
    current_balance   NUMERIC(19,4)  NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'usd',
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    expires_at        TIMESTAMPTZ,
    note              TEXT,
    purchaser_user_id UUID,
    recipient_email   VARCHAR(256),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX gift_cards_code_uq ON checkout.gift_cards (LOWER(code));
CREATE TRIGGER gift_cards_updated_at BEFORE UPDATE ON checkout.gift_cards
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE checkout.gift_card_transactions (
    id           UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    gift_card_id UUID           NOT NULL REFERENCES checkout.gift_cards(id),
    delta        NUMERIC(19,4)  NOT NULL,
    order_id     UUID,
    note         TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX gct_gift_card_id_idx ON checkout.gift_card_transactions (gift_card_id, created_at);

ALTER TABLE checkout.orders
    ADD COLUMN gift_card_id     UUID REFERENCES checkout.gift_cards(id),
    ADD COLUMN gift_card_amount NUMERIC(19,4);
