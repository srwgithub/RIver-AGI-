-- Test data for the current TIME_SERIES prediction task (task id 3).
-- Run this script in the river_agi MySQL database, then click
-- "一键偏差检测" on the prediction bias analysis page.
UPDATE prediction_result
SET actual_value = CASE prediction_date
    WHEN '2026-07-01' THEN 900.0
    WHEN '2026-07-02' THEN 1040.0
    WHEN '2026-07-03' THEN 960.0
    WHEN '2026-07-04' THEN 1120.0
    WHEN '2026-07-05' THEN 1005.0
    WHEN '2026-07-06' THEN 1180.0
    WHEN '2026-07-07' THEN 980.0
    WHEN '2026-07-08' THEN 1260.0
    WHEN '2026-07-09' THEN 1150.0
    WHEN '2026-07-10' THEN 1090.0
    WHEN '2026-07-11' THEN 1320.0
    WHEN '2026-07-12' THEN 1210.0
    WHEN '2026-07-13' THEN 1160.0
    WHEN '2026-07-14' THEN 1430.0
    WHEN '2026-07-15' THEN 1300.0
    WHEN '2026-07-16' THEN 1250.0
    WHEN '2026-07-17' THEN 1510.0
    WHEN '2026-07-18' THEN 1400.0
    WHEN '2026-07-19' THEN 1330.0
    WHEN '2026-07-20' THEN 1600.0
    WHEN '2026-07-21' THEN 1480.0
    WHEN '2026-07-22' THEN 1420.0
    WHEN '2026-07-23' THEN 1690.0
    WHEN '2026-07-24' THEN 1560.0
    WHEN '2026-07-25' THEN 1510.0
    WHEN '2026-07-26' THEN 1780.0
    WHEN '2026-07-27' THEN 1650.0
    WHEN '2026-07-28' THEN 1580.0
    WHEN '2026-07-29' THEN 1870.0
    WHEN '2026-07-30' THEN 1740.0
    ELSE actual_value
END
WHERE task_id = 3
  AND prediction_date BETWEEN '2026-07-01' AND '2026-07-30';

SELECT task_id, prediction_date, predicted_value, actual_value,
       ROUND((actual_value - predicted_value) / NULLIF(actual_value, 0) * 100, 2) AS deviation_percent
FROM prediction_result
WHERE task_id = 3
ORDER BY prediction_date;
