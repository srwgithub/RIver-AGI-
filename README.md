# RIver AGI 第一版 MVP

当前版本采用“单体 IDEA 项目 + Spring Boot 内置 Vue 3”架构。Vue3 编译后放入 Spring Boot 的 `static` 目录，由同一个 Spring Boot 进程提供页面和 API，不需要分别启动前端服务。

## 环境

- Java 21+
- Maven 3.9+
- Node.js 20+

## 启动系统

```bash
cd backend
mvn spring-boot:run
```

访问地址：`http://localhost:8080`

## 当前功能

- CSV、XLSX 文件上传
- 字段类型识别
- 缺失值和重复值检查
- 手机号、身份证、邮箱等敏感信息识别
- 数据预览
- 图表类型推荐
- 对话式分析入口

## 后续

下一步接入 PostgreSQL、MinIO、RabbitMQ、Spring AI + DeepSeek，再增加用户权限、标注任务、预测任务、审计日志和报告导出。前端源码位于 `backend/src/main/frontend`，构建结果位于 `backend/src/main/resources/static`。
