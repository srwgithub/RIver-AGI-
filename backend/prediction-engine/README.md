# Prediction Engine

基于 Flask 的深度学习预测微服务，为 Java Spring Boot 后端提供 TensorFlow/PyTorch/scikit-learn 预测能力。

## 功能特性

- **多种算法支持**：LSTM、Transformer、MLP（TensorFlow/Keras）、RandomForest、GradientBoosting、SVM
- **回归与分类**：支持时间序列预测和特征分类任务
- **模型管理**：训练、保存、加载、删除、对比
- **交叉验证**：Walk-forward 时间序列交叉验证
- **特征重要性**：基于树模型的特征重要性分析
- **REST API**：标准化 JSON 接口，方便 Java 后端调用

## 目录结构

```
prediction-engine/
├── app.py                  # Flask 主应用
├── config.py               # 配置文件
├── requirements.txt        # Python 依赖
├── models/                 # 模型实现
│   ├── __init__.py
│   ├── lstm_model.py       # PyTorch LSTM
│   ├── transformer_model.py # PyTorch Transformer
│   ├── mlp_model.py        # TensorFlow/Keras MLP
│   └── sklearn_model.py    # scikit-learn 包装
├── saved_models/           # 训练模型存储目录
└── README.md
```

## 快速开始

### 1. 安装依赖

```bash
cd backend/prediction-engine
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python -m pip install -r requirements-test.txt
```

### 2. 启动服务

```bash
PORT=5001 HOST=127.0.0.1 .venv/bin/python -u app.py
```

Java 后端固定连接 Python 引擎的 `http://127.0.0.1:5001`。也可以通过环境变量配置：

```bash
HOST=0.0.0.0 PORT=5001 DEBUG=true .venv/bin/python app.py
```

### 3. API 端点

#### 查看可用算法

```bash
GET /api/v1/predictions/algorithms
```

#### 训练模型

```bash
POST /api/v1/predictions/train
Content-Type: application/json

{
  "algorithm": "lstm",
  "task_type": "regression",
    "X": [[1.0], [2.0], [3.0], ...],
    "y": [2.0, 3.0, 4.0, ...],
  "params": {
    "hidden_size": 64,
    "num_layers": 2,
    "epochs": 100,
    "batch_size": 32
  }
}
```

#### 预测

```bash
POST /api/v1/predictions/predict
Content-Type: application/json

{
  "model_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "X": [[1.0], [2.0], [3.0]]
}
```

#### 列出模型

```bash
GET /api/v1/predictions/models
```

#### 获取模型详情

```bash
GET /api/v1/predictions/models/{model_id}
```

#### 删除模型

```bash
DELETE /api/v1/predictions/models/{model_id}
```

#### 模型对比

```bash
POST /api/v1/predictions/models/{model_id}/compare
Content-Type: application/json

{
  "compare_model_id": "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy"
}
```

#### 交叉验证

```bash
POST /api/v1/predictions/cross-validate
Content-Type: application/json

{
  "algorithm": "random_forest",
  "task_type": "regression",
    "X": [[1.0, 2.0], [3.0, 4.0], ...],
    "y": [1.0, 2.0, ...],
  "n_splits": 5
}
```

#### 健康检查

```bash
GET /health
```

## 算法说明

| 算法 | 类型 | 任务 | 框架 |
|------|------|------|------|
| `lstm` | 序列模型 | 回归/分类 | PyTorch |
| `transformer` | 序列模型 | 回归/分类 | PyTorch |
| `mlp` | 特征模型 | 回归/分类 | TensorFlow/Keras |
| `random_forest` | 特征模型 | 回归/分类 | scikit-learn |
| `gradient_boosting` | 特征模型 | 回归/分类 | scikit-learn |
| `svm` | 特征模型 | 回归/分类 | scikit-learn |

## 指标说明

### 回归指标
- **MAE**：平均绝对误差
- **RMSE**：均方根误差
- **MAPE**：平均绝对百分比误差
- **R²**：决定系数

### 分类指标
- **Accuracy**：准确率
- **Precision**：精确率
- **Recall**：召回率
- **F1**：F1 分数

## Java 后端集成

在 Java Spring Boot 中调用示例：

```java
RestTemplate restTemplate = new RestTemplate();

// 训练模型
Map<String, Object> trainRequest = Map.of(
    "algorithm", "lstm",
    "task_type", "regression",
    "dataset", dataList,
    "target", targetList,
    "params", Map.of("epochs", 100, "hidden_size", 64)
);

ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://127.0.0.1:5001/api/v1/predictions/train",
    trainRequest,
    Map.class
);

String modelId = (String) response.getBody().get("data").get("model_id");
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `HOST` | `0.0.0.0` | 服务监听地址 |
| `PORT` | `5001` | 服务端口，Java 集成环境固定使用 5001 |
| `DEBUG` | `false` | 调试模式 |
| `MODEL_STORAGE_PATH` | `./saved_models` | 模型存储路径 |

## 技术栈

- Python 3.9+
- Flask 3.0+
- PyTorch 2.0+
- TensorFlow 2.13+
- scikit-learn 1.3+
- NumPy / Pandas
