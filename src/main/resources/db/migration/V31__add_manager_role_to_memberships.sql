-- Expand company membership role to include MANAGER tier
ALTER TABLE b2b.company_memberships
    DROP CONSTRAINT company_memberships_role_check;

ALTER TABLE b2b.company_memberships
    ADD CONSTRAINT company_memberships_role_check
        CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'));
