-- 任务 7 的实际值回填测试数据。
-- 在 MySQL Workbench 或其他 MySQL 客户端执行，执行后刷新趋势分析页面。
UPDATE prediction_result
SET actual_value = CASE prediction_date
    WHEN '2026-06-12' THEN 365.0
    WHEN '2026-06-13' THEN 430.0
    WHEN '2026-06-14' THEN 380.0
    WHEN '2026-06-15' THEN 455.0
    WHEN '2026-06-16' THEN 390.0
    ELSE actual_value
END
WHERE task_id = 7
  AND prediction_date IN ('2026-06-12', '2026-06-13', '2026-06-14', '2026-06-15', '2026-06-16');

SELECT id, task_id, prediction_date, predicted_value, actual_value
FROM prediction_result
WHERE task_id = 7
ORDER BY prediction_date;
