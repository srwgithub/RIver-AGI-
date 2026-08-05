-- Persist realized values so forecast deviation analysis can match by date.
ALTER TABLE prediction_result
    ADD COLUMN actual_value DOUBLE NULL AFTER predicted_value;
