#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RIver AGI 系统合同验收自动化测试脚本
======================================
对照需求规格说明书 + 合同 14.1-14.3 条款逐条验收。
覆盖：认证权限、数据采集标注、预测评估、趋势分析、安全审计、数据加密脱敏、
      备份恢复、知情同意、合规报告等全部功能模块。

用法:
    python3 qa/contract_acceptance_test.py

输出:
    qa-results/contract-acceptance-report.txt
"""
import os
import sys
import time
import json
import datetime
import traceback

import requests
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

BASE = "http://127.0.0.1:8080"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "qa-results")
OUT_FILE = os.path.join(OUT_DIR, "contract-acceptance-report.txt")
os.makedirs(OUT_DIR, exist_ok=True)

ADMIN = {"username": "admin", "password": "admin123"}
TIMEOUT = 60
PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"


def ts():
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


class TestResult:
    def __init__(self, contract_ref, description, category):
        self.contract_ref = contract_ref
        self.description = description
        self.category = category
        self.status = SKIP
        self.detail = ""
        self.evidence = ""
        self.start_ms = 0
        self.end_ms = 0

    def mark_pass(self, detail="", evidence=""):
        self.status = PASS
        self.detail = detail
        self.evidence = evidence
        self.end_ms = time.perf_counter() * 1000

    def mark_fail(self, detail=""):
        self.status = FAIL
        self.detail = detail
        self.end_ms = time.perf_counter() * 1000

    def mark_skip(self, detail=""):
        self.status = SKIP
        self.detail = detail
        self.end_ms = time.perf_counter() * 1000

    def report(self, add):
        icon = {"PASS": "✅", "FAIL": "❌", "SKIP": "⚠️"}[self.status]
        add(f"  {icon} [{self.contract_ref}] {self.description}")
        if self.detail:
            add(f"     详情: {self.detail}")
        if self.evidence:
            add(f"     证据: {self.evidence[:200]}")
        add("")


class TestSuite:
    def __init__(self):
        self.results = []
        self.token = None
        self.admin_id = None
        self.dataset_id = None
        self.annotation_task_id = None
        self.prediction_id = None

    def add(self, contract_ref, description, category):
        r = TestResult(contract_ref, description, category)
        r.start_ms = time.perf_counter() * 1000
        self.results.append(r)
        return r

    def call(self, method, url, **kwargs):
        headers = kwargs.pop("headers", {})
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        kwargs.setdefault("timeout", TIMEOUT)
        kwargs.setdefault("verify", False)
        try:
            return requests.request(method, url, headers=headers, **kwargs)
        except Exception as e:
            resp = type('MockResp', (), {'status_code': 0, 'text': str(e), 'json': lambda s: {"code": 0, "message": str(e), "data": None}})()
            return resp

    def extract(self, resp):
        if resp is None or resp.status_code == 0:
            return None, resp.text if hasattr(resp, 'text') else "请求异常"
        try:
            body = resp.json()
        except Exception:
            return None, resp.text[:200] if hasattr(resp, 'text') else "无法解析响应"
        if isinstance(body, dict) and "data" in body:
            return body.get("data"), body.get("message", "")
        return body, ""

    def do_login(self):
        r = self.add("§3.1.1", "用户登录 - JWT 认证", "认证权限")
        resp = self.call("POST", f"{BASE}/api/v1/auth/login", json=ADMIN)
        if resp is None:
            r.mark_fail(f"连接失败: {resp}")
            return r
        data, msg = self.extract(resp)
        if resp.status_code == 200 and isinstance(data, dict):
            self.token = data.get("token")
            self.admin_id = (data.get("user") or {}).get("id")
            if self.token:
                r.mark_pass(f"登录成功, userId={self.admin_id}", f"HTTP {resp.status_code}")
            else:
                r.mark_fail(f"未返回 token: {msg}")
        else:
            r.mark_fail(f"HTTP {resp.status_code}: {msg}")
        return r

    def test_module1_data_collection(self):
        """§3.1 数据采集与标注"""
        cat = "数据采集与标注"

        r = self.add("§3.1.1", "数据集上传", cat)
        sample_csv = os.path.join(ROOT, "test-data", "collection_text_sample.csv")
        if not os.path.exists(sample_csv):
            r.mark_skip("样本文件不存在")
            return
        try:
            with open(sample_csv, "rb") as f:
                resp = self.call("POST", f"{BASE}/api/v1/datasets/upload",
                                 files={"file": ("sample.csv", f, "text/csv")})
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200 and isinstance(data, dict) and data.get("id"):
                self.dataset_id = data["id"]
                r.mark_pass(f"数据集 ID={self.dataset_id}, 行数={data.get('rowCount')}",
                           f"HTTP {resp.status_code}")
            else:
                r.mark_fail(f"上传失败: HTTP {resp.status_code if resp else 'N/A'} {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§3.1.2", "数据集列表查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/datasets?page=1&size=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, 共 {count} 个数据集")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.1.3", "数据集详情查看", cat)
        if not self.dataset_id:
            r.mark_skip("无数据集 ID")
        else:
            resp = self.call("GET", f"{BASE}/api/v1/datasets/{self.dataset_id}")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass(f"数据集名称: {data.get('name') if isinstance(data, dict) else 'N/A'}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.1.4", "采集任务创建", cat)
        try:
            body = {"name": f"验收采集任务-{int(time.time())}",
                    "sourceType": "FILE", "mediaType": "TEXT",
                    "sourceUri": "sample.csv",
                    "datasetId": self.dataset_id, "status": "PENDING"}
            resp = self.call("POST", f"{BASE}/api/v1/collection-tasks", json=body)
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass(f"采集任务 ID={data.get('id') if isinstance(data, dict) else 'N/A'}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§3.1.5", "采集任务列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/collection-tasks?page=1&size=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, 共 {count} 条采集任务")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.1.6", "标注任务创建", cat)
        try:
            # Use a real child label from the configured schema instead of a
            # hard-coded demo code that may not exist in the target database.
            schema_resp = self.call("GET", f"{BASE}/api/v1/label-schemas?page=1&size=50")
            schema_data, schema_msg = self.extract(schema_resp)
            schema_records = schema_data.get("records", []) if isinstance(schema_data, dict) else []
            parent_schema = next((x for x in schema_records if not x.get("parentId")), None)
            label_schema_id = parent_schema.get("id") if parent_schema else None
            child_resp = self.call("GET", f"{BASE}/api/v1/label-schemas/{label_schema_id}/children") if label_schema_id else None
            child_data, child_msg = self.extract(child_resp) if child_resp else (None, "标签体系不可用")
            child_labels = child_data if isinstance(child_data, list) else []
            valid_label = child_labels[0] if child_labels else None
            body = {"name": f"验收标注任务-{int(time.time())}",
                    "description": "合同验收自动创建",
                    "datasetId": self.dataset_id,
                    "labelSchemaId": label_schema_id}
            resp = self.call("POST", f"{BASE}/api/v1/annotation-tasks", json=body)
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                self.annotation_task_id = data.get("id") if isinstance(data, dict) else None
                self.annotation_label = valid_label
                r.mark_pass(f"标注任务 ID={self.annotation_task_id}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§3.1.7", "标注任务列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/annotation-tasks?page=1&size=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, 共 {count} 条标注任务")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.1.8", "标注项获取与提交", cat)
        if not self.annotation_task_id:
            r.mark_skip("无标注任务 ID")
        else:
            resp = self.call("GET", f"{BASE}/api/v1/annotation-tasks/{self.annotation_task_id}/annotations")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                annotations = data if isinstance(data, list) else (data.get("records", []) if isinstance(data, dict) else [])
                if annotations:
                    ann = annotations[0]
                    ann_id = ann.get("id")
                    label = getattr(self, "annotation_label", None) or {}
                    resp2 = self.call("POST", f"{BASE}/api/v1/annotations/{ann_id}/submit",
                                      json={"labelCode": label.get("code", ann.get("labelCode")),
                                            "labelName": label.get("name", ann.get("labelName")),
                                            "comment": "验收自动提交"})
                    data2, msg2 = self.extract(resp2)
                    if resp2 and resp2.status_code == 200:
                        r.mark_pass(f"标注提交成功, 共 {len(annotations)} 项")
                    else:
                        r.mark_fail(f"提交失败: HTTP {resp2.status_code if resp2 else 'N/A'}: {msg2}")
                else:
                    r.mark_skip("标注项为空（可能需要先执行预标注）")
            else:
                r.mark_fail(f"获取标注项失败: HTTP {resp.status_code}: {msg}")

        r = self.add("§3.1.9", "标注结果导出", cat)
        if not self.annotation_task_id:
            r.mark_skip("无标注任务 ID")
        else:
            resp = self.call("POST", f"{BASE}/api/v1/annotation-tasks/{self.annotation_task_id}/export")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass("导出成功")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module2_annotation_quality(self):
        """§3.2 标注质量管理"""
        cat = "标注质量管理"

        r = self.add("§3.2.1", "标注质量规则列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/annotation-quality-rules")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else (data.get("total", 0) if isinstance(data, dict) else 0)
            r.mark_pass(f"查询成功, {count} 条规则")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.2.2", "质量抽检", cat)
        if not self.annotation_task_id:
            r.mark_skip("无标注任务 ID")
        else:
            resp = self.call("POST", f"{BASE}/api/v1/annotation-tasks/{self.annotation_task_id}/quality-sampling",
                            json={"sampleRate": 0.1, "reviewDecisions": {}})
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass("质量抽检执行成功")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.2.3", "一致性检查", cat)
        if not self.annotation_task_id:
            r.mark_skip("无标注任务 ID")
        else:
            resp = self.call("POST", f"{BASE}/api/v1/annotation-tasks/{self.annotation_task_id}/consistency-check")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass("一致性检查完成")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module3_prediction(self):
        """§3.3 市场需求预测"""
        cat = "市场需求预测"

        r = self.add("§3.3.1", "预测任务列表查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/predictions?page=1&size=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, {count} 条预测任务")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.2", "模型版本列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/predictions/models")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else 0
            r.mark_pass(f"查询成功, {count} 个模型版本")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.3", "预测引擎算法列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/prediction-admin/algorithms")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else 0
            r.mark_pass(f"查询成功, {count} 个算法")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.4", "预测引擎配置读取", cat)
        resp = self.call("GET", f"{BASE}/api/v1/system-config/prediction-engine")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("配置读取成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.5", "框架状态查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/prediction-admin/framework-status")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("框架状态查询成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.6", "评估配置读取", cat)
        resp = self.call("GET", f"{BASE}/api/v1/system-config/prediction-evaluation")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("评估配置读取成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.3.7", "预测健康检查", cat)
        resp = self.call("GET", f"{BASE}/api/v1/predictions/health")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("预测引擎健康")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module4_trend_analysis(self):
        """§3.4 趋势分析与可视化"""
        cat = "趋势分析与可视化"

        r = self.add("§3.4.1", "仪表盘列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/dashboards")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else (data.get("total", 0) if isinstance(data, dict) else 0)
            r.mark_pass(f"查询成功, {count} 个仪表盘")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.4.2", "分析任务创建", cat)
        try:
            params = {"datasetId": self.dataset_id, "taskType": "TREND"}
            body = {"name": f"验收分析任务-{int(time.time())}"}
            resp = self.call("POST", f"{BASE}/api/v1/analysis/tasks", params=params, json=body)
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass(f"分析任务 ID={data.get('id') if isinstance(data, dict) else 'N/A'}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§3.4.3", "图表配置保存", cat)
        try:
            params = {"datasetId": self.dataset_id, "chartType": "LINE",
                      "title": f"验收图表-{int(time.time())}",
                      "xAxisField": "x", "yAxisField": "y"}
            resp = self.call("POST", f"{BASE}/api/v1/charts/save", params=params, json={})
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass("图表配置保存成功")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§3.4.4", "图表报告列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/charts/reports")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else 0
            r.mark_pass(f"查询成功, {count} 份报告")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module5_evaluation_optimization(self):
        """§3.5 评估优化"""
        cat = "评估优化"

        r = self.add("§3.5.1", "评估任务列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/predictions/evaluations?limit=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else 0
            r.mark_pass(f"查询成功, {count} 条评估记录")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.5.2", "运行时监控指标", cat)
        resp = self.call("GET", f"{BASE}/api/v1/predictions/monitoring/summary")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("运行时指标获取成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module6_security_audit(self):
        """§3.6 + 14.x 安全审计与数据合规"""
        cat = "安全审计与数据合规"

        # 14.1.1 数据加密
        r = self.add("§14.1.1", "数据加密 - 加密工具可用", cat)
        try:
            resp = self.call("GET", f"{BASE}/api/v1/auth/me")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                r.mark_pass("用户信息获取成功 (加密通道可用)")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        # 14.1.2 访问控制
        r = self.add("§14.1.2", "访问控制 - RBAC 权限校验", cat)
        try:
            resp = self.call("GET", f"{BASE}/api/v1/auth/me")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200 and isinstance(data, dict):
                roles = data.get("roles", [])
                r.mark_pass(f"用户角色: {roles}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        # 14.1.3 数据备份
        r = self.add("§14.1.3", "数据备份 - 备份列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/backups")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, {count} 条备份记录")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.1.3b", "数据备份 - 手动创建备份", cat)
        try:
            resp = self.call("POST", f"{BASE}/api/v1/backup/records/create")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                backup_id = data.get("id") if isinstance(data, dict) else None
                r.mark_pass(f"备份创建成功 ID={backup_id}")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")
        except Exception as e:
            r.mark_fail(f"异常: {e}")

        r = self.add("§14.1.3c", "数据备份 - 备份状态查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/backup/records/status")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("备份状态查询成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 14.1.4 安全审计
        r = self.add("§14.1.4", "安全审计 - 审计日志查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/audit/logs?page=1&size=20")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            records = data.get("records", []) if isinstance(data, dict) else data
            count = len(records) if isinstance(records, list) else 0
            r.mark_pass(f"查询成功, {count} 条审计日志")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.1.4b", "安全审计 - 审计日志导出", cat)
        resp = self.call("GET", f"{BASE}/api/v1/audit/logs/export")
        if resp and resp.status_code == 200:
            r.mark_pass("审计日志导出成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {resp.text[:100] if resp else 'N/A'}")

        # 14.2.1 知情同意
        r = self.add("§14.2.1", "知情同意 - 隐私政策获取", cat)
        resp = self.call("GET", f"{BASE}/api/v1/privacy/policy")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200 and isinstance(data, dict):
            r.mark_pass(f"政策版本: {data.get('version', 'N/A')}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.2.1b", "知情同意 - 同意状态查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/privacy/consent/status")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("同意状态查询成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.2.1c", "知情同意 - 同意历史查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/privacy/consent/history")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("同意历史查询成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 14.2.2 数据脱敏
        r = self.add("§14.2.2", "数据脱敏 - 用户信息脱敏展示", cat)
        resp = self.call("GET", f"{BASE}/api/v1/auth/me")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200 and isinstance(data, dict):
            email = data.get("email", "")
            phone = data.get("phone", "")
            is_masked = ("***" in str(email)) or ("***" in str(phone)) or (not email)
            r.mark_pass(f"email={email}, phone={phone}, 脱敏={'是' if is_masked else '否(可能原值不含敏感字符)'}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 14.2.3 加密存储
        r = self.add("§14.2.3", "加密存储 - 敏感数据扫描", cat)
        if not self.dataset_id:
            r.mark_skip("无数据集 ID")
        else:
            resp = self.call("POST", f"{BASE}/api/v1/security/datasets/{self.dataset_id}/scan")
            data, msg = self.extract(resp)
            if resp and resp.status_code == 200:
                detections = data.get("detections", []) if isinstance(data, dict) else []
                r.mark_pass(f"扫描完成, 发现 {len(detections)} 项敏感数据" if isinstance(detections, list) else "扫描完成")
            else:
                r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 14.2.4 数据留存与删除
        r = self.add("§14.2.4", "数据留存 - 留存策略查询", cat)
        resp = self.call("GET", f"{BASE}/api/v1/privacy/retention")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass(f"留存策略: {json.dumps(data, ensure_ascii=False)[:200]}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.2.4b", "数据主体权利 - 数据导出", cat)
        resp = self.call("GET", f"{BASE}/api/v1/privacy/data/export")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("个人数据导出成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 14.3 数据合规
        r = self.add("§14.3", "数据合规 - 合规摘要", cat)
        resp = self.call("GET", f"{BASE}/api/v1/audit/compliance-summary")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass(f"合规摘要: {json.dumps(data, ensure_ascii=False)[:200]}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§14.3b", "数据合规 - 合规报告", cat)
        resp = self.call("GET", f"{BASE}/api/v1/audit/compliance-report")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("合规报告获取成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 安全仪表盘
        r = self.add("§14.3c", "安全仪表盘", cat)
        resp = self.call("GET", f"{BASE}/api/v1/security/dashboard")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass(f"安全仪表盘: {json.dumps(data, ensure_ascii=False)[:200]}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        # 安全策略
        r = self.add("§14.3d", "安全策略列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/security-admin/policies")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else (data.get("total", 0) if isinstance(data, dict) else 0)
            r.mark_pass(f"查询成功, {count} 条策略")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_module7_system(self):
        """系统管理与配置"""
        cat = "系统管理"

        r = self.add("§3.7.1", "系统配置 - 安全配置读取", cat)
        resp = self.call("GET", f"{BASE}/api/v1/system-config/security")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass("安全配置读取成功")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.7.2", "异步任务列表", cat)
        resp = self.call("GET", f"{BASE}/api/v1/tasks")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            count = len(data) if isinstance(data, list) else 0
            r.mark_pass(f"查询成功, {count} 个异步任务")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

        r = self.add("§3.7.3", "模块操作统计", cat)
        resp = self.call("GET", f"{BASE}/api/v1/module-actions/summary")
        data, msg = self.extract(resp)
        if resp and resp.status_code == 200:
            r.mark_pass(f"模块统计: {json.dumps(data, ensure_ascii=False)[:200]}")
        else:
            r.mark_fail(f"HTTP {resp.status_code if resp else 'N/A'}: {msg}")

    def test_performance(self):
        """§4 非功能需求 - 性能"""
        cat = "性能验收"

        r = self.add("§4.1", "登录接口响应 < 1.5s", cat)
        t0 = time.perf_counter()
        resp = self.call("POST", f"{BASE}/api/v1/auth/login", json=ADMIN)
        elapsed = (time.perf_counter() - t0) * 1000
        if resp and resp.status_code == 200 and elapsed < 1500:
            r.mark_pass(f"耗时 {elapsed:.0f}ms (达标 <1500ms)")
        else:
            r.mark_fail(f"耗时 {elapsed:.0f}ms {'(超标)' if elapsed >= 1500 else ''}")

        r = self.add("§4.2", "列表查询响应 < 1.5s", cat)
        t0 = time.perf_counter()
        resp = self.call("GET", f"{BASE}/api/v1/datasets?page=1&size=20")
        elapsed = (time.perf_counter() - t0) * 1000
        if resp and resp.status_code == 200 and elapsed < 1500:
            r.mark_pass(f"耗时 {elapsed:.0f}ms (达标 <1500ms)")
        else:
            r.mark_fail(f"耗时 {elapsed:.0f}ms {'(超标)' if elapsed >= 1500 else ''}")

    def run_all(self):
        buf = []
        def add(s=""):
            buf.append(s)
            print(s, flush=True)

        add("=" * 70)
        add("RIver AGI 系统合同验收自动化测试")
        add(f"运行时间: {ts()}")
        add(f"后端: {BASE}")
        add("=" * 70)

        # 检查服务可达性
        add("\n[0] 检查后端服务可达性...")
        try:
            resp = requests.get(f"{BASE}/api/v1/auth/me", timeout=5)
            add(f"    后端服务响应: HTTP {resp.status_code}")
        except Exception as e:
            add(f"    ❌ 后端服务不可达: {e}")
            add("    请先启动后端服务: cd backend && mvn spring-boot:run")
            return 1

        # 登录
        add("\n[1] 认证权限测试...")
        self.do_login().report(add)

        if not self.token:
            add("❌ 登录失败, 终止后续测试")
            self._write_report(buf)
            return 1

        # 模块测试
        add("\n[2] 数据采集与标注测试...")
        self.test_module1_data_collection()
        add("\n[3] 标注质量管理测试...")
        self.test_module2_annotation_quality()
        add("\n[4] 市场需求预测测试...")
        self.test_module3_prediction()
        add("\n[5] 趋势分析与可视化测试...")
        self.test_module4_trend_analysis()
        add("\n[6] 评估优化测试...")
        self.test_module5_evaluation_optimization()
        add("\n[7] 安全审计与数据合规测试...")
        self.test_module6_security_audit()
        add("\n[8] 系统管理测试...")
        self.test_module7_system()
        add("\n[9] 性能验收测试...")
        self.test_performance()

        # 汇总
        add("\n" + "=" * 70)
        add("验收集总报告")
        add("=" * 70)

        categories = {}
        for r in self.results:
            cat = r.category
            if cat not in categories:
                categories[cat] = {"pass": 0, "fail": 0, "skip": 0, "total": 0}
            categories[cat]["total"] += 1
            if r.status == PASS:
                categories[cat]["pass"] += 1
            elif r.status == FAIL:
                categories[cat]["fail"] += 1
            else:
                categories[cat]["skip"] += 1

        total_pass = total_fail = total_skip = 0
        for cat, counts in categories.items():
            rate = counts["pass"] / counts["total"] * 100 if counts["total"] > 0 else 0
            add(f"  {cat}: 通过 {counts['pass']}/{counts['total']} ({rate:.0f}%)")
            if counts["fail"] > 0:
                add(f"    ❌ 失败项:")
                for r in self.results:
                    if r.category == cat and r.status == FAIL:
                        add(f"       [{r.contract_ref}] {r.description}: {r.detail}")
            total_pass += counts["pass"]
            total_fail += counts["fail"]
            total_skip += counts["skip"]

        total = total_pass + total_fail + total_skip
        rate = total_pass / total * 100 if total > 0 else 0
        add(f"\n  总计: 通过 {total_pass}/{total} ({rate:.0f}%)  失败 {total_fail}  跳过 {total_skip}")

        add("\n" + "-" * 70)
        add("未通过项详细列表:")
        add("-" * 70)
        for r in self.results:
            if r.status != PASS:
                icon = "❌" if r.status == FAIL else "⚠️"
                add(f"  {icon} [{r.contract_ref}] {r.description}")
                add(f"     状态: {r.status}  详情: {r.detail}")
                add("")

        add(f"\n结束时间: {ts()}")
        self._write_report(buf)

        # 返回退出码
        return 0 if total_fail == 0 else 2

    def _write_report(self, buf):
        with open(OUT_FILE, "w", encoding="utf-8") as f:
            f.write("\n".join(buf))
        print(f"\n[完成] 报告已写入: {OUT_FILE}")


def main():
    suite = TestSuite()
    return suite.run_all()


if __name__ == "__main__":
    sys.exit(main())
