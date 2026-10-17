-- DBV-33: every connection to a target must be encrypted unless the operator opted out.
-- Existing targets are switched to required as well: a target that was silently backed up in
-- clear text is what this change is meant to end, so the upgrade guide tells an operator whose
-- server cannot speak TLS to opt that target out (ADR-041).
ALTER TABLE database_targets
    ADD COLUMN tls_required BOOLEAN NOT NULL DEFAULT TRUE;

-- Rollback:
-- ALTER TABLE database_targets DROP COLUMN tls_required;
