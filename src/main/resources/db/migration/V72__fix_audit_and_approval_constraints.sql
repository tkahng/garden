-- Prevent duplicate pendency rows for the same rule on the same quote
ALTER TABLE b2b.quote_approval_pendencies
    ADD CONSTRAINT uq_quote_approval_pendency UNIQUE (quote_id, rule_id);

-- Add missing FK for admin_user_id in impersonation_tokens
ALTER TABLE auth.impersonation_tokens
    ADD CONSTRAINT fk_impersonation_admin_user
    FOREIGN KEY (admin_user_id) REFERENCES auth.users(id) ON DELETE SET NULL;
