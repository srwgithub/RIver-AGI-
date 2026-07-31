# RIver AGI - 对话式数据智能分析平台

## 项目简介

RIver AGI 是一个对话式数据智能分析平台，提供数据上传、质量分析、安全扫描、智能标注、需求预测等功能。

## 合同七大研发板块

| 合同研发板块 | 系统实现重点 | 主要交付结果 |
|---|---|---|
| 数据采集与标注平台 | 多源数据导入、清洗、文本/图片/视频标注、任务分配、协同标注、自定义标签、AI预标注 | 数据采集标注平台及说明文档 |
| 标注质量管理模块 | 审核、一致性检查、抽检、纠偏、多轮标注、仲裁、标注员绩效、质量评分 | 标注质量管理模块及规则后台 |
| 市场需求预测引擎 | 时间序列、回归、分类、序列预测、模型训练、模型版本、A/B测试 | 需求预测引擎及模型管理后台 |
| 趋势分析与可视化 | 趋势分析、对比分析、异常检测、根因分析、自定义报表和仪表盘 | 趋势看板及可视化报表 |
| 预测结果评估与优化 | 准确率评估、偏差分析、性能监控、自动调优、模型迭代、自动重训 | 预测评估中心及模型优化系统 |
| 数据管理与安全审计 | 权限分级、敏感数据识别、加密脱敏、操作日志、审计追溯、备份恢复 | 审计中心及安全管理后台 |
| 系统部署与运维支撑 | 测试/生产部署、监控、告警、日志分析、故障处理、应急预案、培训 | 部署文档、运维手册、培训材料 |

## 技术栈

- **后端**: Spring Boot 3.4.5 + Java 21
- **前端**: Vue 3 + Element Plus + ECharts
- **数据库**: MySQL 8
- **缓存**: Redis 7
- **消息队列**: RabbitMQ 3
- **对象存储**: MinIO
- **AI**: Spring AI + DeepSeek

## 快速开始

### 方式一：使用启动脚本（推荐）

```bash
cd "RIver AGI系统"
chmod +x start.sh
./start.sh
```

### 方式二：手动启动

#### 1. 启动依赖服务 (Docker)

```bash
cd backend
docker compose up -d
docker compose ps
```

这会启动 MySQL、Redis、RabbitMQ 和 MinIO。默认账号密码与 `application.yml` 保持一致。

#### 2. 编译后端

```bash
cd backend
mvn clean compile
```

#### 3. 运行后端

```bash
cd backend
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

#### 4. 访问系统

Vue3 前端已编译到 Spring Boot 的 `static` 目录，后端启动后访问 http://localhost:8080

## API 测试

```bash
cd "RIver AGI系统"
chmod +x test_api.sh
./test_api.sh
```

## API 文档

启动后端后访问: http://localhost:8080/swagger-ui.html

## 默认账号

- 用户名: admin
- 密码: admin123

## 核心功能

### 第一阶段验收标准

1. ✅ 登录系统
2. ✅ 上传 Excel/CSV 文件
3. ✅ 查看数据预览和字段识别结果
4. ✅ 查看缺失值、重复值和异常值
5. ✅ 查看手机号、身份证等敏感字段风险
6. ✅ 对数据进行自动标注或人工修改
7. ✅ 通过对话询问数据问题
8. ✅ 自动生成至少三种图表
9. ✅ 对时间序列字段进行需求预测
10. ✅ 查看预测结果和误差指标
11. ✅ 导出分析报告
12. ✅ 查看操作审计记录

### 核心业务流程

```text
上传数据 → AI识别 → 数据质量分析 → 自动标注
→ 安全风险检测 → 图表展示 → 需求预测 → AI解释和报告
```

## 项目结构

```
backend/
├── src/main/java/com/river/agi/
│   ├── auth/          # 认证模块
│   ├── dataset/       # 数据集管理
│   ├── analysis/      # 数据分析
│   ├── annotation/    # 数据标注
│   ├── prediction/    # 需求预测
│   ├── security/      # 安全审计
│   ├── chart/         # 图表报告
│   ├── chat/          # AI对话
│   ├── common/        # 公共模块
│   └── config/        # 配置模块
├── src/main/resources/
│   └── application.yml
└── database/
    └── init.sql

frontend/
├── src/
│   ├── views/         # 页面组件
│   ├── components/    # 公共组件
│   ├── router/        # 路由配置
│   ├── utils/         # 工具函数
│   └── App.vue
├── vite.config.js
└── package.json
```

## 环境变量

后端支持以下环境变量：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_URL | jdbc:mysql://localhost:3306/river_agi | 数据库连接 |
| DB_USERNAME | river_agi | 数据库用户名 |
| DB_PASSWORD | river_agi_password | 数据库密码 |
| REDIS_HOST | localhost | Redis地址 |
| MINIO_ENDPOINT | http://localhost:9000 | MinIO地址 |
| DEEPSEEK_API_KEY | - | DeepSeek API Key |
| JWT_SECRET | river-agi-jwt-secret-key | JWT密钥 |

## 数据库初始化

数据库表结构和初始数据在 `database/init.sql` 中定义，启动 Docker Compose 时自动执行。

## 开发说明

### 代码规范

- 所有接口使用 `/api/v1` 前缀
- 所有响应统一格式: `{ code, message, data, traceId }`
- 所有核心表包含: id, tenant_id, created_by, created_at, updated_at, deleted
- 使用 MyBatis-Plus 进行数据访问
- 使用 Spring Security + JWT 进行认证

### 安全规则

- 禁止前端直接访问数据库
- AI 工具调用必须有白名单和权限校验
- 敏感数据访问必须写入审计日志
- 禁止 AI 直接执行 SQL

## License

MIT License
