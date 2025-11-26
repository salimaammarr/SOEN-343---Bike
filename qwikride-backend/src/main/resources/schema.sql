ALTER TABLE users ADD COLUMN IF NOT EXISTS pricing_plan VARCHAR(255) DEFAULT 'FREE';
ALTER TABLE pricing_plan_versions ADD COLUMN IF NOT EXISTS subscription_price NUMERIC(19, 2) DEFAULT 0;

-- Fix for LedgerEntry constraints to allow plan subscriptions (which have no bike or stations)
ALTER TABLE ledger_entries ALTER COLUMN bike_id DROP NOT NULL;
ALTER TABLE ledger_entries ALTER COLUMN start_station_id DROP NOT NULL;
ALTER TABLE ledger_entries ALTER COLUMN end_station_id DROP NOT NULL;
