-- 用途：预测任务生成预测结果后，为相同预测日期回填真实值，验证偏差分析闭环。
-- 使用方法：把 @task_id 改成你新建的预测任务 ID，再执行本文件。
SET @task_id = 7;

UPDATE prediction_result
SET actual_value = CASE prediction_date
    WHEN '2026-06-12' THEN 365.0
    WHEN '2026-06-13' THEN 430.0
    WHEN '2026-06-14' THEN 380.0
    WHEN '2026-06-15' THEN 455.0
    WHEN '2026-06-16' THEN 390.0
    ELSE actual_value
END
WHERE task_id = @task_id
  AND prediction_date IN ('2026-06-12', '2026-06-13', '2026-06-14', '2026-06-15', '2026-06-16');

SELECT id, task_id, prediction_date, predicted_value, actual_value
FROM prediction_result
WHERE task_id = @task_id
ORDER BY prediction_date;
