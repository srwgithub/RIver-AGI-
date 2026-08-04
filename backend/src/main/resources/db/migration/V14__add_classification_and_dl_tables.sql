-- RIver AGI 迁移脚本 V9
-- 添加分类预测、深度学习模型注册表、特征重要性、交叉验证和算法配置表
-- 使用条件式 ALTER 避免与已有列冲突

-- 分类预测结果表
CREATE TABLE IF NOT EXISTS classification_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sample_index INT NOT NULL,
    predicted_class VARCHAR(200) NOT NULL,
    confidence DOUBLE DEFAULT 0,
    actual_class VARCHAR(200),
    is_correct TINYINT DEFAULT 0,
    class_probabilities JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_class_result_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 深度学习模型注册表
CREATE TABLE IF NOT EXISTS dl_model_registry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id VARCHAR(100) NOT NULL UNIQUE,
    model_name VARCHAR(200) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    task_type VARCHAR(20) NOT NULL COMMENT 'REGRESSION/CLASSIFICATION/SEQUENCE',
    model_path VARCHAR(500),
    model_size BIGINT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'READY',
    parameters JSON,
    metrics JSON,
    feature_importance JSON,
    training_samples INT DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预测特征重要性表
CREATE TABLE IF NOT EXISTS prediction_feature_importance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_version_id BIGINT NOT NULL,
    feature_name VARCHAR(200) NOT NULL,
    importance_score DOUBLE DEFAULT 0,
    rank_index INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fi_model_version (model_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 交叉验证结果表
CREATE TABLE IF NOT EXISTS cross_validation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    cv_folds INT DEFAULT 5,
    cv_strategy VARCHAR(50) DEFAULT 'WALK_FORWARD',
    mean_mae DOUBLE DEFAULT 0,
    mean_rmse DOUBLE DEFAULT 0,
    mean_mape DOUBLE DEFAULT 0,
    mean_r2 DOUBLE DEFAULT 0,
    std_mae DOUBLE DEFAULT 0,
    std_rmse DOUBLE DEFAULT 0,
    fold_metrics JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cv_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预测算法配置扩展表
CREATE TABLE IF NOT EXISTS prediction_algorithm_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    algorithm_type VARCHAR(50) NOT NULL,
    algorithm_name VARCHAR(100) NOT NULL,
    algorithm_family VARCHAR(30) NOT NULL COMMENT 'TIME_SERIES/CLASSIFICATION/DEEP_LEARNING',
    task_type VARCHAR(20) NOT NULL COMMENT 'REGRESSION/CLASSIFICATION/SEQUENCE',
    default_params JSON,
    is_default BOOLEAN DEFAULT FALSE,
    is_enabled BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_algo_type (algorithm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入分类和深度学习算法配置
INSERT IGNORE INTO prediction_algorithm_config (algorithm_type, algorithm_name, algorithm_family, task_type, default_params, is_default, is_enabled, priority) VALUES
('LOGISTIC_REGRESSION_CLASSIFIER', '逻辑回归分类', 'CLASSIFICATION', 'CLASSIFICATION', '{"learning_rate":0.01,"max_iterations":1000,"regularization":0.001}', TRUE, TRUE, 1),
('DECISION_TREE_CLASSIFIER', '决策树分类', 'CLASSIFICATION', 'CLASSIFICATION', '{"max_depth":10,"min_samples_split":2,"min_samples_leaf":1}', TRUE, TRUE, 2),
('RANDOM_FOREST_CLASSIFIER', '随机森林分类', 'CLASSIFICATION', 'CLASSIFICATION', '{"num_trees":100,"max_depth":10,"min_samples_split":2}', TRUE, TRUE, 3),
('LSTM_DL', 'LSTM深度预测', 'DEEP_LEARNING', 'SEQUENCE', '{"hidden_size":64,"num_layers":2,"epochs":100,"batch_size":32,"learning_rate":0.001}', FALSE, TRUE, 4),
('TRANSFORMER_DL', 'Transformer深度预测', 'DEEP_LEARNING', 'SEQUENCE', '{"d_model":128,"num_heads":4,"num_layers":2,"epochs":100,"batch_size":32}', FALSE, TRUE, 5),
('MLP_DL', 'MLP深度预测', 'DEEP_LEARNING', 'REGRESSION', '{"hidden_layers":[128,64,32],"epochs":100,"batch_size":32,"learning_rate":0.001}', FALSE, TRUE, 6);

-- 条件式 ALTER: 为 model_version 添加 task_type 列（如不存在）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN task_type VARCHAR(20) DEFAULT ''REGRESSION'' COMMENT ''任务类型: REGRESSION/CLASSIFICATION/SEQUENCE''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'task_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 条件式 ALTER: 为 prediction_task 添加 task_type 列（如不存在）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN task_type VARCHAR(20) DEFAULT ''REGRESSION'' COMMENT ''任务类型: REGRESSION/CLASSIFICATION/SEQUENCE''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'task_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 条件式 ALTER: 为 prediction_task 添加 dl_model_id 列（如不存在）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN dl_model_id VARCHAR(100) DEFAULT NULL COMMENT ''关联的Python深度学习模型ID''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'dl_model_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;