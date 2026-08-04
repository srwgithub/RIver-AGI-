# RIver AGI 数据智能分析与需求预测平台

RIver AGI 是面向企业数据采集、标注、质量管理、需求预测、趋势分析、模型优化和安全审计的一体化平台。

本文档是本地开发、测试和交付部署的完整说明，覆盖环境准备、依赖安装、配置、启动、使用、验收和故障排查。

## 1. 系统组成

平台由三个运行服务组成：

| 服务 | 技术 | 地址 | 作用 |
| --- | --- | --- | --- |
| 前端 | Vue 3、Vite、Element Plus、ECharts | `http://127.0.0.1:3000` | 页面和交互 |
| Java 后端 | Spring Boot 3.4、Java、MyBatis-Plus、Spring Security | `http://127.0.0.1:8080` | 业务接口、权限、审计和数据持久化 |
| Python 引擎 | Flask、PyTorch、TensorFlow/Keras、scikit-learn | `http://127.0.0.1:5001` | LSTM、Transformer、MLP 及机器学习模型训练和预测 |

合同研发内容对应模块：

1. 数据采集与标注平台：上传、解析、清洗、配置、派发、标注、校验和导出。
2. 标注质量管理中心：审核、抽检、一致性检查、纠偏、多轮标注、仲裁、绩效和评分。
3. 市场需求预测引擎：时间序列、回归、分类、序列预测、深度学习、模型版本和 A/B 测试。
4. 趋势分析与可视化：趋势诊断、预测对比、异常检测、根因分析、OLAP、What-If 和报表。
5. 预测结果评估与优化：准确率、偏差分析、性能监控、自动调优、手动调优、Retraining 和回滚。
6. 数据管理与安全审计：权限分级、敏感数据扫描、风险查看、审计追溯、备份和恢复。

## 2. 目录结构

```text
RIver AGI系统/
├── backend/
│   ├── src/main/java/                 # Java 业务后端源码
│   ├── src/main/resources/            # Spring 配置和数据库迁移
│   ├── prediction-engine/             # Python 深度学习引擎
│   │   ├── app.py
│   │   ├── models/
│   │   ├── requirements.txt
│   │   └── .venv/                     # 本地 Python 虚拟环境
│   ├── database/                      # MySQL 初始化脚本
│   ├── target/                        # Maven 打包产物
│   ├── .env.local                     # 本机私密配置，不提交 Git
│   └── start-local.sh                 # 启动 Python 引擎和 Java 后端
├── frontend/
│   ├── src/                           # Vue 页面、路由和 API 调用
│   ├── package.json
│   └── vite.config.js
├── test-data/                         # 联调和验收数据
└── qa/                                # Python 和浏览器自动化说明
```

## 3. 环境要求

### 必需环境

- macOS、Linux 或 Windows WSL2
- Java 21 或更高版本
- Maven 3.9 或更高版本
- Node.js 20 或更高版本
- npm 10 或更高版本
- Python 3.9-3.11，推荐 Python 3.10
- MySQL 8.0 或更高版本

### 可选环境

- Docker Desktop：用于启动 MySQL、Redis、RabbitMQ 和 MinIO
- Google Chrome：用于 Playwright 浏览器验收
- GPU、CUDA 和 cuDNN：仅在需要 GPU 训练时安装；CPU 可以运行全部功能

检查版本：

```bash
java -version
mvn -version
node -v
npm -v
python3 --version
mysql --version
```

如果系统找不到 Maven，可使用项目机器上的 Maven 路径，或把 Maven 的 `bin` 目录加入 `PATH`：

```bash
export PATH="/Users/yidaoliu/tools/apache-maven-3.9.9/bin:$PATH"
```

## 4. MySQL 配置

创建数据库：

```sql
CREATE DATABASE river_agi
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

如果是全新数据库，可执行：

```bash
cd backend
mysql -uroot -p river_agi < database/init_mysql.sql
```

当前 MySQL 连接由 `backend/.env.local` 控制。该文件只保存在本机，不要提交到 Git：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/river_agi?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=请填写你的MySQL密码
DB_DRIVER=com.mysql.cj.jdbc.Driver
JWT_SECRET=请填写至少32位的随机密钥
FLYWAY_ENABLED=true
SQL_INIT_MODE=never
```

已有数据库也可以直接启动。Java 服务会通过 Flyway 检查并执行未完成的数据库迁移。

## 5. DeepSeek API 配置

DeepSeek 只用于 AI 对话和相关智能解释，不是普通预测算法或 Python 深度学习训练的必需条件。

在 `backend/.env.local` 添加：

```dotenv
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_API_KEY=你的DeepSeek_API_Key
DEEPSEEK_MODEL=deepseek-chat
```

也可以只在当前终端会话注入：

```bash
export DEEPSEEK_API_KEY='你的DeepSeek_API_Key'
```

安全要求：

- 不要把 Key 写入源码、README、截图或 Git 提交。
- 不要把 `.env.local` 上传到公共仓库。
- Key 泄露后应立即在 DeepSeek 控制台撤销并重新生成。
- 未配置 Key 时，平台基础数据、标注、预测和审计功能仍可使用，AI 对话会提示未配置。

## 6. Python 虚拟环境和深度学习依赖

首次安装：

```bash
cd backend/prediction-engine
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install -r requirements-test.txt
deactivate
```

Windows PowerShell 激活方式：

```powershell
cd backend\prediction-engine
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

验证依赖：

```bash
cd backend
prediction-engine/.venv/bin/python -c "import flask, torch, tensorflow, sklearn; print('Python engine dependencies OK')"
```

Python 引擎支持：

| 模型 | 框架 | 用途 |
| --- | --- | --- |
| LSTM | PyTorch | 时间序列和序列回归 |
| Transformer | PyTorch | 序列预测 |
| MLP | TensorFlow/Keras | 回归和分类 |
| Random Forest | scikit-learn | 回归和分类 |
| Gradient Boosting | scikit-learn | 回归和分类 |
| SVM | scikit-learn | 回归和分类 |

## 7. 安装前端依赖

```bash
cd frontend
npm ci
```

如果没有 `package-lock.json` 或需要更新依赖，可以使用：

```bash
npm install
```

## 8. 编译和启动

### 方式 A：本地 MySQL，推荐

先确认 MySQL 已启动，并确认 `backend/.env.local` 已填写完整。

终端一：启动 Python 引擎和 Java 后端：

```bash
cd "/Users/yidaoliu/Documents/RIver AGI系统/backend"
chmod +x start-local.sh
set -a
source .env.local
set +a
/Users/yidaoliu/tools/apache-maven-3.9.9/bin/mvn -q -DskipTests package
./start-local.sh
```

`start-local.sh` 会自动：

- 固定 Python 引擎端口为 `5001`
- 设置 `DL_ENGINE_ENABLED=true`
- 设置 Java 到 Python 引擎的地址为 `http://127.0.0.1:5001`
- 启动 Python 引擎
- 启动 Java JAR

终端二：启动 Vue 前端：

```bash
cd "/Users/yidaoliu/Documents/RIver AGI系统/frontend"
npm run dev -- --host 0.0.0.0
```

访问：

```text
http://127.0.0.1:3000
```

默认测试账号：

```text
用户名：admin
密码：admin123
```

### 方式 B：分别手动启动

启动 Python：

```bash
cd backend
PORT=5001 HOST=127.0.0.1 prediction-engine/.venv/bin/python -u prediction-engine/app.py
```

编译并启动 Java：

```bash
cd backend
set -a; source .env.local; set +a
export DL_ENGINE_ENABLED=true
export DL_ENGINE_URL=http://127.0.0.1:5001
/Users/yidaoliu/tools/apache-maven-3.9.9/bin/mvn -q -DskipTests package
java -jar target/river-agi-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

启动前端：

```bash
cd frontend
npm run dev -- --host 0.0.0.0
```

### 方式 C：Docker 启动基础依赖

项目提供 `backend/docker-compose.yml`，可启动 MySQL、Redis、RabbitMQ、MinIO 以及容器化 Java 服务：

```bash
cd backend
docker compose up -d mysql redis rabbitmq minio
docker compose ps
```

确认依赖健康后，再按“方式 A”启动本地 Python 和 Java。Docker Compose 中的 MySQL 默认账号与本地 `.env.local` 不同，使用 Docker 时请按 `docker-compose.yml` 中的账号密码调整配置。

停止依赖：

```bash
docker compose down
```

不要随意执行 `docker compose down -v`，该命令会删除 Docker 数据卷。

## 9. 服务健康检查

```bash
curl http://127.0.0.1:3000/
curl http://127.0.0.1:5001/health
curl http://127.0.0.1:8080/actuator/health
```

Java 业务健康检查需要登录 Token；未登录访问返回 `401/403` 属于正常安全行为：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

登录返回 `accessToken` 后：

```bash
curl http://127.0.0.1:8080/api/v1/predictions/health \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

期望深度学习状态包含：

```json
{
  "enabled": true,
  "reachable": true,
  "engineUrl": "http://127.0.0.1:5001"
}
```

## 10. 使用流程

### 数据采集与标注

```text
多源数据导入
  → 解析和预览
  → 自动清洗预览
  → 应用清洗并生成数据集
  → 配置标签和规则
  → 创建并派发标注任务
  → 进入独立标注工作台
  → 在线校验和提交
  → 质量审核、抽检、纠偏、仲裁
  → 导出最终标注结果
```

### 需求预测和模型优化

```text
选择已解析数据集
  → 新建预测任务
  → 选择时间序列/回归/分类/序列模型
  → 选择 TensorFlow 或 PyTorch 引擎
  → 训练并查看进度
  → 生成预测结果
  → 查看 MAE/RMSE/MAPE/R²
  → 偏差分析
  → 自动调优或手动调优
  → Retraining
  → 模型版本对比和 A/B 测试
  → 发布、回滚或切换生产模型
```

### 安全与运维

```text
创建角色
  → 选择权限并保存授权
  → 使用对应账号验证权限
  → 对数据集执行安全扫描
  → 查看风险和敏感字段
  → 查询审计日志和合规报告
  → 创建备份
  → 恢复备份并验证数据
```

## 11. 页面入口

| 页面 | 地址 |
| --- | --- |
| 首页看板 | `/dashboard` |
| 多源数据导入 | `/collection-annotation` |
| 标注任务配置 | `/collection-annotation/config` |
| 标注工作台 | `/annotation-platform` |
| 标注质量中心 | `/annotation-quality` |
| 标注质量规则后台 | `/annotation-quality/rules` |
| 市场需求预测引擎 | `/prediction-engine` |
| 模型管理后台 | `/prediction-engine/models` |
| 预测结果评估 | `/prediction-evaluation` |
| 模型优化 | `/model-optimization` |
| 趋势分析看板 | `/trend-dashboard` |
| 安全审计中心 | `/security-audit` |
| 权限管理后台 | `/security-admin` |
| 备份恢复中心 | `/security-audit/backup` |

## 12. 测试和验收

### 前端编译

```bash
cd frontend
npm run build
```

### Python 单元测试

```bash
cd backend
prediction-engine/.venv/bin/pytest -q
```

### 浏览器自动化

前端依赖已包含 Playwright。若本机已安装 Google Chrome，可使用 Chrome 执行页面验收；也可以安装 Playwright 浏览器：

```bash
cd frontend
npx playwright install chromium
```

验收重点：

1. 上传真实 CSV，确认解析状态和数据预览。
2. 执行清洗预览和清洗应用，确认生成新数据集。
3. 配置标签、派发任务、进入工作台并提交标注。
4. 执行自动校验、抽检、审核、仲裁和导出。
5. 使用真实数据执行 LSTM/MLP 训练、预测、评估、A/B 和发布。
6. 创建备份并执行恢复验证。
7. 创建角色、授权权限，并用对应用户验证访问生效。
8. 执行安全扫描、查看风险和审计追溯。

项目测试数据位于 `test-data/`，包括 CSV、JSON、图片和 SQL 回填脚本。

## 13. 常见问题

### 前端 3000 无法访问

```bash
cd frontend
npm run dev -- --host 0.0.0.0
```

### Java 返回 401 或 403

先登录获取 Token。前端登录后会自动把 Token 放入请求头；直接用浏览器访问受保护 API 返回 401/403 是正常的。

### 深度学习显示未启用

确认 Java 启动时存在：

```bash
export DL_ENGINE_ENABLED=true
export DL_ENGINE_URL=http://127.0.0.1:5001
```

并确认 Python：

```bash
curl http://127.0.0.1:5001/health
```

### Java 启动提示 JWT_SECRET 太短

`JWT_SECRET` 至少需要 32 个字符：

```dotenv
JWT_SECRET=请替换为至少32位的随机字符串
```

### MySQL 连接失败

确认 MySQL 正在监听 3306、数据库名称正确，并检查 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。本地 MySQL 和 Docker MySQL 的账号密码以当前配置文件为准。

### 预测偏差页面没有样本

偏差检测需要预测日期对应的真实 `actual_value`。如果未来预测日期还没有实际数据，页面显示空状态是正常业务结果，不代表接口故障。

### MinIO 是否必须启动

当前本地文件上传和导出使用后端本地存储，MinIO 是对象存储扩展依赖。需要对象存储部署时启动 MinIO，并配置 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET`。

## 14. 生产部署建议

- 使用独立的 MySQL 用户，不使用 root。
- 使用随机且长度足够的 `JWT_SECRET` 和 `ENCRYPTION_MASTER_KEY`。
- 通过系统环境变量或密钥管理服务注入 `DEEPSEEK_API_KEY`。
- 前端使用 `npm run build` 后由 Nginx 或 Spring Boot 静态目录提供。
- Python 引擎和 Java 后端使用 systemd、Docker 或进程管理器托管。
- 将 `uploads/`、`backups/` 和 `saved_models/` 纳入持久化备份。
- 启用 HTTPS、访问日志、错误告警和定期恢复演练。
- 99.5% 可用率需要上线后持续监控和停机统计，不能由一次本地测试直接证明。

## 15. 交付检查清单

- [ ] Java、Maven、Node.js、Python 和 MySQL 版本满足要求。
- [ ] `backend/.env.local` 已配置且未提交敏感信息。
- [ ] MySQL 数据库和迁移已完成。
- [ ] Python 虚拟环境依赖安装完成。
- [ ] 前端依赖安装完成。
- [ ] Java、Python、前端三个服务健康检查通过。
- [ ] `npm run build` 通过。
- [ ] Python `pytest` 通过。
- [ ] 真实登录后的五条业务链路完成验收。
- [ ] 已执行备份恢复演练和权限生效验证。
- [ ] 已准备生产环境密钥、备份策略和监控方案。

## 16. 许可证

MIT License
