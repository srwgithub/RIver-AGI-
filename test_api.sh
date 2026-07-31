#!/bin/bash

echo "========================================"
echo "      RIver AGI API 测试"
echo "========================================"

BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"

# 1. 测试登录
echo ""
echo "1. 测试登录接口..."
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

echo "   响应: $LOGIN_RESP"

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
    echo "   登录成功! Token 获取成功"
else
    echo "   登录失败!"
    exit 1
fi

# 2. 测试获取数据集列表
echo ""
echo "2. 测试数据集列表..."
DATASETS_RESP=$(curl -s -X GET "$BASE_URL/v1/datasets?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $DATASETS_RESP"

# 3. 测试数据分析 - 数据画像
echo ""
echo "3. 测试数据画像接口..."
ANALYSIS_RESP=$(curl -s -X POST "$BASE_URL/v1/analysis/profile?datasetId=1" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $ANALYSIS_RESP"

# 4. 测试安全扫描
echo ""
echo "4. 测试安全扫描接口..."
SCAN_RESP=$(curl -s -X POST "$BASE_URL/v1/security/datasets/1/scan" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $SCAN_RESP"

# 5. 测试AI对话
echo ""
echo "5. 测试AI对话接口..."
CHAT_RESP=$(curl -s -X POST "$BASE_URL/v1/chat/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"message":"分析数据质量","datasetId":1}')
echo "   响应: $CHAT_RESP"

# 6. 测试预测
echo ""
echo "6. 测试预测接口..."
PREDICT_RESP=$(curl -s -X POST "$BASE_URL/v1/predictions" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"datasetId":1,"targetField":"sales","timeField":"date","modelType":"LIGHTGBM"}')
echo "   响应: $PREDICT_RESP"

# 7. 测试图表推荐
echo ""
echo "7. 测试图表推荐接口..."
CHARTS_RESP=$(curl -s -X POST "$BASE_URL/v1/charts/recommend?datasetId=1" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $CHARTS_RESP"

# 8. 测试报告生成
echo ""
echo "8. 测试报告生成接口..."
REPORT_RESP=$(curl -s -X POST "$BASE_URL/v1/charts/reports?datasetId=1" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $REPORT_RESP"

# 9. 测试审计日志
echo ""
echo "9. 测试审计日志接口..."
AUDIT_RESP=$(curl -s -X GET "$BASE_URL/v1/audit/logs?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
echo "   响应: $AUDIT_RESP"

# 10. 测试标注任务
echo ""
echo "10. 测试标注任务接口..."
ANNOTATION_RESP=$(curl -s -X POST "$BASE_URL/v1/annotation-tasks" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"datasetId":1,"name":"测试标注任务"}')
echo "   响应: $ANNOTATION_RESP"

# 11. 测试仪表盘统计
echo ""
echo "11. 测试仪表盘统计接口..."
DASHBOARD_RESP=$(curl -s -X GET "$BASE_URL/v1/analysis/tasks/count" \
    -H "Authorization: Bearer $TOKEN")
echo "   分析任务数: $DASHBOARD_RESP"

SCAN_COUNT_RESP=$(curl -s -X GET "$BASE_URL/v1/security/scans/count" \
    -H "Authorization: Bearer $TOKEN")
echo "   安全扫描数: $SCAN_COUNT_RESP"

PREDICT_COUNT_RESP=$(curl -s -X GET "$BASE_URL/v1/predictions/count" \
    -H "Authorization: Bearer $TOKEN")
echo "   预测任务数: $PREDICT_COUNT_RESP"

echo ""
echo "========================================"
echo "      API 测试完成!"
echo "========================================"
