SET search_path = public;

-- V33 introduced email campaign relationships. PostgreSQL does not create
-- child-side indexes for foreign keys, so add the missing lookup indexes in a
-- forward-only migration without rewriting the already-applied V33 script.
CREATE INDEX IF NOT EXISTS email_templates_created_by_fk_idx
    ON email_templates (created_by);
CREATE INDEX IF NOT EXISTS email_templates_updated_by_fk_idx
    ON email_templates (updated_by);
CREATE INDEX IF NOT EXISTS email_campaigns_created_by_fk_idx
    ON email_campaigns (created_by);
CREATE INDEX IF NOT EXISTS email_campaigns_template_fk_idx
    ON email_campaigns (template_id);
CREATE INDEX IF NOT EXISTS email_campaign_recipients_organization_fk_idx
    ON email_campaign_recipients (organization_id);
CREATE INDEX IF NOT EXISTS email_campaign_recipients_employee_fk_idx
    ON email_campaign_recipients (employee_id);
CREATE INDEX IF NOT EXISTS email_delivery_attempts_organization_fk_idx
    ON email_delivery_attempts (organization_id);

SET search_path = public;
