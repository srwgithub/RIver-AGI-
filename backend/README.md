# Java 后端与 Python 引擎

完整的项目搭建、配置、启动、使用和验收文档请阅读项目根目录的 [README.md](../README.md)。

## 快速启动

```bash
cd "/Users/yidaoliu/Documents/RIver AGI系统/backend"
set -a; source .env.local; set +a
/Users/yidaoliu/tools/apache-maven-3.9.9/bin/mvn -q -DskipTests package
./start-local.sh
```

`start-local.sh` 会启动：

- Python 深度学习引擎：`http://127.0.0.1:5001`
- Java Spring Boot 后端：`http://127.0.0.1:8080`

前端需要单独启动：

```bash
cd ../frontend
npm run dev -- --host 0.0.0.0
```

## Python 虚拟环境

```bash
cd prediction-engine
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install -r requirements-test.txt
deactivate
```

验证：

```bash
cd ..
prediction-engine/.venv/bin/python -c "import torch, tensorflow, flask; print('OK')"
prediction-engine/.venv/bin/pytest -q
```

## Java 配置

本地 MySQL、JWT 和 DeepSeek 配置放在 `.env.local`，不要提交到 Git。至少需要：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/river_agi?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=你的MySQL密码
JWT_SECRET=至少32位的随机字符串
DEEPSEEK_API_KEY=你的DeepSeek_API_Key
DL_ENGINE_ENABLED=true
DL_ENGINE_URL=http://127.0.0.1:5001
```

完整环境变量说明、MySQL 初始化、Docker 依赖、真实业务流程和交付清单均以根目录 README 为准。
