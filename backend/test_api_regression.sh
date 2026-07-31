#!/bin/bash

# RIver AGI 核心 API 回归测试脚本
# 覆盖：认证、数据集、分析、安全扫描、预测、图表、报告、标注、审计等核心流程

set -u

BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"
TOKEN=""
PASSED=0
FAILED=0
FAILED_CASES=()

log_pass() {
    PASSED=$((PASSED + 1))
    echo "[PASS] $1"
}

log_fail() {
    FAILED=$((FAILED + 1))
    FAILED_CASES+=("$1")
    echo "[FAIL] $1 (detail: $2)"
}

call_api() {
    local method="$1"
    local path="$2"
    local data="${3:-}"
    local auth_token="${4:-$TOKEN}"
    local url="$BASE_URL$path"
    local response
    local http_code
    local body

    response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
        -H "Authorization: Bearer $auth_token" \
        ${data:+-H "Content-Type: application/json"} \
        ${data:+-d "$data"})

    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | sed '$d')
    echo "$http_code|$body"
}

check_ok() {
    local name="$1"
    local result="$2"
    local expected_code="${3:-200}"
    local code
    code=$(echo "$result" | cut -d'|' -f1)
    local body
    body=$(echo "$result" | cut -d'|' -f2-)
    if [ "$code" = "$expected_code" ]; then
        # The current API contract uses business code 200 for success.
        # Keep compatibility with older fixtures that used code 0.
        if echo "$body" | grep -Eq '"code":(0|200)'; then
            log_pass "$name"
            return 0
        else
            log_fail "$name" "业务码非 0: $body"
            return 1
        fi
    else
        log_fail "$name" "HTTP $code (expected $expected_code)"
        return 1
    fi
}

check_error_code() {
    local name="$1"
    local result="$2"
    local expected_code="$3"
    local code
    code=$(echo "$result" | cut -d'|' -f1)
    if [ "$code" = "$expected_code" ]; then
        log_pass "$name"
        return 0
    else
        log_fail "$name" "HTTP $code (expected $expected_code)"
        return 1
    fi
}

echo "========================================"
echo "  RIver AGI 核心 API 回归测试"
echo "  BASE_URL: $BASE_URL"
echo "========================================"

# ---------- 1. 认证 ----------
echo ""
echo "[1/11] 认证模块"

RESULT=$(call_api "POST" "/v1/auth/login" '{"username":"admin","password":"admin123"}' "")
check_ok "登录 (admin/admin123)" "$RESULT" "200"
TOKEN=$(echo "$RESULT" | cut -d'|' -f2- | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    TOKEN=$(echo "$RESULT" | cut -d'|' -f2- | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$TOKEN" ]; then
    echo "[ERROR] 无法获取 Token，后续测试终止"
    echo "$RESULT"
    exit 1
fi
echo "       Token acquired: ${TOKEN:0:20}..."

# 错误密码
RESULT=$(call_api "POST" "/v1/auth/login" '{"username":"admin","password":"wrong"}' "")
check_error_code "错误密码应返回 401 或 403" "$RESULT" "401"

# 非法 token
RESULT=$(call_api "GET" "/v1/datasets?page=1&size=5" "" "invalid-token")
check_error_code "非法 token 应返回 401" "$RESULT" "401"

# ---------- 2. 数据集 ----------
echo ""
echo "[2/11] 数据集模块"

RESULT=$(call_api "GET" "/v1/datasets?page=1&size=10")
check_ok "获取数据集列表" "$RESULT" "200"

# Keep the fixture deterministic and verify the fields required by later flows.
DATASET_ID="${TEST_DATASET_ID:-1}"
RESULT=$(call_api "GET" "/v1/datasets/$DATASET_ID")
check_ok "验证测试数据集详情 ($DATASET_ID)" "$RESULT" "200"
RESULT=$(call_api "GET" "/v1/datasets/$DATASET_ID/fields")
check_ok "验证测试数据集字段 ($DATASET_ID)" "$RESULT" "200"
if ! echo "$RESULT" | grep -q 'demand' || ! echo "$RESULT" | grep -q 'date'; then
    log_fail "测试数据集字段完整性" "数据集 $DATASET_ID 必须包含 demand 和 date 字段"
    exit 1
fi
echo "       使用已校验数据集 ID: $DATASET_ID"

# ---------- 3. 数据分析 ----------
echo ""
echo "[3/11] 数据分析模块"

RESULT=$(call_api "POST" "/v1/analysis/profile?datasetId=$DATASET_ID")
check_ok "数据画像" "$RESULT" "200"

RESULT=$(call_api "POST" "/v1/analysis/quality?datasetId=$DATASET_ID")
check_ok "数据质量分析" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/analysis/tasks?datasetId=$DATASET_ID")
check_ok "获取分析任务列表" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/analysis/tasks/count")
check_ok "分析任务计数" "$RESULT" "200"

# ---------- 4. 安全扫描 ----------
echo ""
echo "[4/11] 安全扫描模块"

RESULT=$(call_api "POST" "/v1/security/datasets/$DATASET_ID/scan")
check_ok "执行安全扫描" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/security/datasets/$DATASET_ID/risks")
check_ok "查询数据集风险" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/security/dashboard")
check_ok "安全扫描概览" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/security/scans/count")
check_ok "安全扫描计数" "$RESULT" "200"

# ---------- 5. AI 对话 ----------
echo ""
echo "[5/11] AI 对话模块"

RESULT=$(call_api "POST" "/v1/chat/messages" '{"message":"请分析该数据集的质量状况","datasetId":'$DATASET_ID'}')
check_ok "AI 对话（质量分析）" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/chat/sessions")
check_ok "获取对话会话列表" "$RESULT" "200"

# ---------- 6. 预测 ----------
echo ""
echo "[6/11] 预测模块"

RESULT=$(call_api "GET" "/v1/predictions/algorithms")
check_ok "获取预测算法列表" "$RESULT" "200"

RESULT=$(call_api "POST" "/v1/predictions" '{"datasetId":'$DATASET_ID',"targetField":"demand","timeField":"date","modelType":"HOLT_WINTERS"}')
check_ok "创建预测任务 (Holt-Winters)" "$RESULT" "200"

PREDICT_ID=$(echo "$RESULT" | cut -d'|' -f2- | grep -oE '"id":[0-9]+' | head -n 1 | cut -d':' -f2)

if [ -n "$PREDICT_ID" ]; then
    RESULT=$(call_api "GET" "/v1/predictions/$PREDICT_ID")
    check_ok "查询预测任务详情" "$RESULT" "200"

    RESULT=$(call_api "GET" "/v1/predictions/$PREDICT_ID/results")
    check_ok "查询预测结果" "$RESULT" "200"

    # 检查模型指标接口
    RESULT=$(call_api "GET" "/v1/predictions/$PREDICT_ID/metrics")
    check_ok "查询预测评估指标 (MAE/RMSE/MAPE)" "$RESULT" "200"

    RESULT=$(call_api "GET" "/v1/predictions/$PREDICT_ID/comparison")
    check_ok "查询预测与实际对比" "$RESULT" "200"

    RESULT=$(call_api "POST" "/v1/predictions/$PREDICT_ID/bias-detection")
    check_ok "预测偏差检测" "$RESULT" "200"

    RESULT=$(call_api "POST" "/v1/predictions/$PREDICT_ID/auto-retrain")
    check_ok "预测自动重训" "$RESULT" "200"
fi

RESULT=$(call_api "GET" "/v1/predictions?page=1&size=10")
check_ok "预测任务分页列表" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/predictions/count")
check_ok "预测任务计数" "$RESULT" "200"

# 列出所有可用算法
RESULT=$(call_api "GET" "/v1/predictions/algorithms")
check_ok "预测算法列表" "$RESULT" "200"

# ---------- 7. 图表与报告 ----------
echo ""
echo "[7/11] 图表与报告模块"

RESULT=$(call_api "POST" "/v1/charts/recommend?datasetId=$DATASET_ID")
check_ok "推荐图表类型" "$RESULT" "200"

RESULT=$(call_api "POST" "/v1/charts/generate?datasetId=$DATASET_ID&chartType=BAR&xAxisField=category&yAxisField=sales")
check_ok "生成柱状图" "$RESULT" "200"

RESULT=$(call_api "POST" "/v1/charts/reports?datasetId=$DATASET_ID&reportType=FULL")
check_ok "生成完整报告" "$RESULT" "200"

RESULT_BODY=$(echo "$RESULT" | cut -d'|' -f2-)
REPORT_ID=$(echo "$RESULT_BODY" | grep -oE '"id":[0-9]+' | head -n 1 | cut -d':' -f2)

if [ -n "$REPORT_ID" ]; then
    RESULT=$(call_api "GET" "/v1/charts/reports/$REPORT_ID")
    check_ok "查询报告详情" "$RESULT" "200"

    # 校验报告包含质量、安全、预测章节
    if echo "$RESULT_BODY" | grep -q 'predictionOverview'; then
        log_pass "报告包含预测章节 (predictionOverview)"
    else
        log_fail "报告包含预测章节" "predictionOverview 字段缺失"
    fi

    if echo "$RESULT_BODY" | grep -q 'recommendations'; then
        log_pass "报告包含建议列表 (recommendations)"
    else
        log_fail "报告包含建议列表" "recommendations 字段缺失"
    fi

    # 确保没有任何固定硬编码结论
    if echo "$RESULT_BODY" | grep -q '数据质量优秀，可直接用于分析$'; then
        log_fail "报告不应再含固定硬编码结论" "仍存在固定文案"
    else
        log_pass "报告使用动态生成结论"
    fi
fi

RESULT=$(call_api "GET" "/v1/charts/reports?page=1&size=10")
check_ok "报告分页列表" "$RESULT" "200"

# ---------- 8. 标注 ----------
echo ""
echo "[8/11] 标注模块"

RESULT=$(call_api "POST" "/v1/annotation-tasks" '{"datasetId":'$DATASET_ID',"name":"回归测试标注任务"}')
check_ok "创建标注任务" "$RESULT" "200"

ANNOT_ID=$(echo "$RESULT" | cut -d'|' -f2- | grep -oE '"id":[0-9]+' | head -n 1 | cut -d':' -f2)

if [ -n "$ANNOT_ID" ]; then
    RESULT=$(call_api "POST" "/v1/annotation-tasks/$ANNOT_ID/assign" '{"annotatorIds":[1,2]}')
    check_ok "分配标注员" "$RESULT" "200"

    RESULT=$(call_api "POST" "/v1/annotation-tasks/$ANNOT_ID/quality-sampling" '{"sampleRate":0.2}')
    check_ok "标注质量抽检" "$RESULT" "200"

    RESULT=$(call_api "POST" "/v1/annotation-tasks/$ANNOT_ID/consistency-check")
    check_ok "标注一致性检查" "$RESULT" "200"

    RESULT=$(call_api "GET" "/v1/annotation-tasks/$ANNOT_ID/quality-metrics")
    check_ok "标注质量指标" "$RESULT" "200"

    RESULT=$(call_api "POST" "/v1/annotation-tasks/$ANNOT_ID/pre-annotate" "")
    check_ok "规则/AI 预标注" "$RESULT" "200"
fi

RESULT=$(call_api "GET" "/v1/annotation-tasks?page=1&size=10")
check_ok "标注任务列表" "$RESULT" "200"

# ---------- 9. 审计 ----------
echo ""
echo "[9/11] 审计日志模块"

RESULT=$(call_api "GET" "/v1/audit/logs?page=1&size=10")
check_ok "审计日志列表" "$RESULT" "200"

# ---------- 10. 备份 ----------
echo ""
echo "[10/11] 备份模块"

RESULT=$(call_api "GET" "/v1/backups")
check_ok "备份任务列表" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/backups/status")
check_ok "备份状态" "$RESULT" "200"

# ---------- 11. 仪表盘统计 ----------
echo ""
echo "[11/11] 仪表盘统计"

RESULT=$(call_api "GET" "/v1/analysis/tasks/count")
check_ok "分析任务总数" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/security/scans/count")
check_ok "安全扫描总数" "$RESULT" "200"

RESULT=$(call_api "GET" "/v1/predictions/count")
check_ok "预测任务总数" "$RESULT" "200"

# ---------- 汇总 ----------
echo ""
echo "========================================"
echo "  回归测试汇总"
echo "========================================"
echo "通过: $PASSED"
echo "失败: $FAILED"
TOTAL=$((PASSED + FAILED))
echo "总计: $TOTAL"

if [ "$FAILED" -gt 0 ]; then
    echo ""
    echo "失败用例详情:"
    for c in "${FAILED_CASES[@]}"; do
        echo "  - $c"
    done
    exit 1
fi

echo ""
echo "🎉 所有回归测试通过！"
exit 0
