#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
任务 B：E2E 全链路业务验收
两条链路：
  链路1（采集标注全流程）：登录→上传数据集→创建采集任务→创建标注任务→分配标注员→
        获取标注项→提交标注→质量抽检→一致性检查→导出
  链路2（预测评估全流程）：登录→列出数据集→创建预测任务→获取结果→评估→偏差检测→模型比较
每步记录：HTTP 状态码、关键字段是否存在、耗时。允许部分步骤失败，记录失败原因。
结果写入 qa-results/e2e-full-chain.txt。
"""
import os
import sys
import time
import json
import datetime

import requests

BASE = "http://127.0.0.1:8080"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "qa-results")
OUT_FILE = os.path.join(OUT_DIR, "e2e-full-chain.txt")
SAMPLE_CSV = os.path.join(ROOT, "test-data", "collection_text_sample.csv")
os.makedirs(OUT_DIR, exist_ok=True)

ADMIN = {"username": "admin", "password": "admin123"}
TIMEOUT = 60


def ts():
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


class Step:
    def __init__(self, name):
        self.name = name
        self.method = ""
        self.url = ""
        self.status = 0
        self.elapsed_ms = 0.0
        self.ok = False
        self.key_fields = {}
        self.reason = ""
        self.data = None

    def done(self, ok, reason=""):
        self.ok = ok
        self.reason = reason

    def report(self, add):
        flag = "PASS" if self.ok else "FAIL"
        add(f"  [{flag}] {self.name}")
        add(f"        {self.method} {self.url}")
        add(f"        状态码={self.status}  耗时={self.elapsed_ms:.0f}ms")
        if self.key_fields:
            add(f"        关键字段={self._safe(self.key_fields)}")
        if self.reason:
            add(f"        说明={self.reason}")
        add("")

    @staticmethod
    def _safe(obj):
        try:
            s = json.dumps(obj, ensure_ascii=False)
            return s if len(s) <= 300 else s[:300] + "...(截断)"
        except Exception:
            return str(obj)[:300]


def call(method, url, token=None, **kwargs):
    headers = kwargs.pop("headers", {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    kwargs.setdefault("timeout", TIMEOUT)
    return requests.request(method, url, headers=headers, **kwargs)


def extract_data(resp):
    """统一从 {code,message,data} 包络里取 data。"""
    try:
        body = resp.json()
    except Exception:
        return None, resp.text[:200]
    if isinstance(body, dict) and "data" in body:
        return body.get("data"), body.get("message", "")
    return body, ""


def login(add):
    st = Step("登录")
    st.method, st.url = "POST", "/api/v1/auth/login"
    t0 = time.perf_counter()
    try:
        r = call("POST", BASE + "/api/v1/auth/login", json=ADMIN)
        st.status = r.status_code
        st.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        st.data = data
        token = data.get("token") if isinstance(data, dict) else None
        uid = (data.get("user") or {}).get("id") if isinstance(data, dict) else None
        st.key_fields = {"token": bool(token), "userId": uid}
        if token:
            st.done(True)
        else:
            st.done(False, f"未返回 token: {msg}")
    except Exception as e:
        st.elapsed_ms = (time.perf_counter() - t0) * 1000
        st.done(False, f"异常 {type(e).__name__}: {e}")
    return st


def chain1_annotation(add, admin_id):
    add("=" * 70)
    add("链路1：采集标注全流程")
    add("=" * 70)
    steps = []

    s = login(add); steps.append(s)
    if not s.ok:
        s.report(add)
        return steps
    token = s.data.get("token")

    # 2. 上传数据集
    s = Step("上传数据集")
    s.method, s.url = "POST", "/api/v1/datasets/upload"
    t0 = time.perf_counter()
    dataset_id = None
    try:
        with open(SAMPLE_CSV, "rb") as f:
            r = call("POST", BASE + "/api/v1/datasets/upload", token=token,
                     files={"file": ("collection_text_sample.csv", f, "text/csv")})
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        dataset_id = data.get("id") if isinstance(data, dict) else None
        s.key_fields = {"datasetId": dataset_id, "name": data.get("name") if isinstance(data, dict) else None,
                        "rowCount": data.get("rowCount") if isinstance(data, dict) else None,
                        "status": data.get("status") if isinstance(data, dict) else None}
        s.done(dataset_id is not None, "ok" if dataset_id else f"未返回 datasetId: {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 3. 创建采集任务
    s = Step("创建采集任务")
    s.method, s.url = "POST", "/api/v1/collection-tasks"
    t0 = time.perf_counter()
    collection_id = None
    try:
        body = {"name": "E2E采集任务-%d" % int(time.time()),
                "sourceType": "FILE", "mediaType": "TEXT",
                "sourceUri": "collection_text_sample.csv",
                "datasetId": dataset_id, "status": "PENDING"}
        r = call("POST", BASE + "/api/v1/collection-tasks", token=token, json=body)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        collection_id = data.get("id") if isinstance(data, dict) else None
        s.key_fields = {"collectionTaskId": collection_id}
        s.done(r.status_code == 200 and collection_id is not None,
               "ok" if collection_id else f"创建失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 4. 创建标注任务
    s = Step("创建标注任务")
    s.method, s.url = "POST", "/api/v1/annotation-tasks"
    t0 = time.perf_counter()
    annotation_task_id = None
    try:
        body = {"name": "E2E标注任务-%d" % int(time.time()),
                "description": "E2E全链路验收自动创建",
                "datasetId": dataset_id}
        r = call("POST", BASE + "/api/v1/annotation-tasks", token=token, json=body)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        annotation_task_id = data.get("id") if isinstance(data, dict) else None
        s.key_fields = {"annotationTaskId": annotation_task_id,
                        "status": data.get("status") if isinstance(data, dict) else None}
        s.done(r.status_code == 200 and annotation_task_id is not None,
               "ok" if annotation_task_id else f"创建失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 5. 分配标注员
    s = Step("分配标注员")
    s.method, s.url = "POST", f"/api/v1/annotation-tasks/{annotation_task_id}/assign"
    t0 = time.perf_counter()
    try:
        if not annotation_task_id:
            raise RuntimeError("依赖的标注任务ID缺失")
        r = call("POST", BASE + f"/api/v1/annotation-tasks/{annotation_task_id}/assign",
                 token=token, json={"annotatorIds": [admin_id]})
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"assignedAnnotators": data.get("assignedAnnotators") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"分配失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 6. 获取标注项
    s = Step("获取标注项")
    s.method, s.url = "GET", f"/api/v1/annotation-tasks/{annotation_task_id}/annotations"
    t0 = time.perf_counter()
    annotations = []
    try:
        if not annotation_task_id:
            raise RuntimeError("依赖的标注任务ID缺失")
        r = call("GET", BASE + f"/api/v1/annotation-tasks/{annotation_task_id}/annotations", token=token)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        annotations = data if isinstance(data, list) else (data.get("records", []) if isinstance(data, dict) else [])
        s.key_fields = {"annotationCount": len(annotations)}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"获取失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 7. 提交标注
    s = Step("提交标注")
    s.method, s.url = "POST", "/api/v1/annotations/{id}/submit"
    t0 = time.perf_counter()
    try:
        if not annotations:
            raise RuntimeError("无标注项可提交（预标注未生成标注项）")
        ann = annotations[0]
        ann_id = ann.get("id")
        label_code = ann.get("labelCode") or "POS"
        body = {"labelCode": label_code, "labelName": ann.get("labelName") or "正向", "comment": "E2E自动提交"}
        r = call("POST", BASE + f"/api/v1/annotations/{ann_id}/submit", token=token, json=body)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.url = f"/api/v1/annotations/{ann_id}/submit"
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"annotationId": ann_id, "submittedLabel": label_code,
                        "resultStatus": data.get("status") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"提交失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 8. 质量抽检
    s = Step("质量抽检")
    s.method, s.url = "POST", f"/api/v1/annotation-tasks/{annotation_task_id}/quality-sampling"
    t0 = time.perf_counter()
    try:
        if not annotation_task_id:
            raise RuntimeError("依赖的标注任务ID缺失")
        r = call("POST", BASE + f"/api/v1/annotation-tasks/{annotation_task_id}/quality-sampling",
                 token=token, json={"sampleRate": 0.1, "reviewDecisions": {}})
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"hasResult": isinstance(data, dict) and len(data) > 0,
                        "sampledCount": data.get("sampledCount") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"抽检失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 9. 一致性检查
    s = Step("一致性检查")
    s.method, s.url = "POST", f"/api/v1/annotation-tasks/{annotation_task_id}/consistency-check"
    t0 = time.perf_counter()
    try:
        if not annotation_task_id:
            raise RuntimeError("依赖的标注任务ID缺失")
        r = call("POST", BASE + f"/api/v1/annotation-tasks/{annotation_task_id}/consistency-check", token=token)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"hasResult": isinstance(data, dict) and len(data) > 0,
                        "consistencyRate": data.get("consistencyRate") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"一致性检查失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 10. 导出
    s = Step("导出标注结果")
    s.method, s.url = "POST", f"/api/v1/annotation-tasks/{annotation_task_id}/export"
    t0 = time.perf_counter()
    try:
        if not annotation_task_id:
            raise RuntimeError("依赖的标注任务ID缺失")
        r = call("POST", BASE + f"/api/v1/annotation-tasks/{annotation_task_id}/export", token=token)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"hasResult": isinstance(data, dict) and len(data) > 0,
                        "rowCount": data.get("rowCount") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"导出失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    return steps


def chain2_prediction(add):
    add("=" * 70)
    add("链路2：预测评估全流程")
    add("=" * 70)
    steps = []

    s = login(add); steps.append(s)
    if not s.ok:
        s.report(add)
        return steps
    token = s.data.get("token")

    # 2. 列出数据集，挑选含 DATE + NUMERIC 字段的
    s = Step("列出数据集")
    s.method, s.url = "GET", "/api/v1/datasets"
    t0 = time.perf_counter()
    dataset_id = None
    time_field = None
    target_field = None
    try:
        r = call("GET", BASE + "/api/v1/datasets?page=1&size=50", token=token)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        records = data.get("records", []) if isinstance(data, dict) else (data if isinstance(data, list) else [])
        s.key_fields = {"datasetCount": len(records)}
        # 挑选：schema 里有 DATE 字段和 NUMERIC 字段
        for ds in records:
            schema = ds.get("schemaJson")
            if not schema:
                continue
            try:
                sm = json.loads(schema) if isinstance(schema, str) else schema
            except Exception:
                continue
            date_f = next((k for k, v in sm.items() if str(v).upper() == "DATE"), None)
            num_f = next((k for k, v in sm.items() if str(v).upper() == "NUMERIC"), None)
            if date_f and num_f and (ds.get("rowCount") or 0) >= 5:
                dataset_id = ds.get("id")
                time_field = date_f
                target_field = num_f
                break
        s.key_fields["pickedDatasetId"] = dataset_id
        s.key_fields["timeField"] = time_field
        s.key_fields["targetField"] = target_field
        s.done(r.status_code == 200 and dataset_id is not None,
               "ok" if dataset_id else "无可用数据集（无 DATE+NUMERIC 字段组合）")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 3. 创建预测任务
    s = Step("创建预测任务(AUTO)")
    s.method, s.url = "POST", "/api/v1/predictions"
    t0 = time.perf_counter()
    prediction_id = None
    model_version_id = None
    try:
        if not dataset_id:
            raise RuntimeError("无可用数据集")
        body = {"name": "E2E预测任务-%d" % int(time.time()),
                "datasetId": dataset_id, "timeField": time_field,
                "targetField": target_field, "modelType": "AUTO",
                "forecastDays": 7}
        r = call("POST", BASE + "/api/v1/predictions", token=token, json=body, timeout=120)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        prediction_id = data.get("id") if isinstance(data, dict) else None
        model_version_id = data.get("modelVersionId") if isinstance(data, dict) else None
        s.key_fields = {"predictionId": prediction_id,
                        "status": data.get("status") if isinstance(data, dict) else None,
                        "modelVersionId": model_version_id,
                        "modelType": data.get("modelType") if isinstance(data, dict) else None}
        s.done(r.status_code == 200 and prediction_id is not None,
               "ok" if prediction_id else f"创建失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 4. 获取预测结果
    s = Step("获取预测结果")
    s.method, s.url = "GET", f"/api/v1/predictions/{prediction_id}/results"
    t0 = time.perf_counter()
    try:
        if not prediction_id:
            raise RuntimeError("依赖的预测任务ID缺失")
        r = call("GET", BASE + f"/api/v1/predictions/{prediction_id}/results", token=token)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        result_count = len(data) if isinstance(data, list) else 0
        s.key_fields = {"resultCount": result_count}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"获取失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 5. 评估
    s = Step("模型评估")
    s.method, s.url = "POST", f"/api/v1/predictions/{prediction_id}/evaluate"
    t0 = time.perf_counter()
    try:
        if not prediction_id:
            raise RuntimeError("依赖的预测任务ID缺失")
        r = call("POST", BASE + f"/api/v1/predictions/{prediction_id}/evaluate", token=token, timeout=120)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"hasMetrics": isinstance(data, dict) and len(data) > 0,
                        "mae": data.get("mae") if isinstance(data, dict) else None,
                        "rmse": data.get("rmse") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"评估失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 6. 偏差检测
    s = Step("偏差检测")
    s.method, s.url = "POST", f"/api/v1/predictions/{prediction_id}/bias-detection"
    t0 = time.perf_counter()
    try:
        if not prediction_id:
            raise RuntimeError("依赖的预测任务ID缺失")
        r = call("POST", BASE + f"/api/v1/predictions/{prediction_id}/bias-detection", token=token, timeout=120)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"hasResult": isinstance(data, dict) and len(data) > 0}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"偏差检测失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    # 7. 模型比较（用两个已存在的 model version id）
    s = Step("模型比较")
    s.method, s.url = "POST", "/api/v1/predictions/models/compare"
    t0 = time.perf_counter()
    try:
        # 先列出已有模型版本，取前两个
        rl = call("GET", BASE + "/api/v1/predictions/models", token=token)
        ldata, lmsg = extract_data(rl)
        versions = ldata if isinstance(ldata, list) else []
        cand = [v.get("id") for v in versions if v.get("id")][:2]
        # 若新预测产生了 modelVersionId，优先用它 + 一个旧版本
        ids = []
        if model_version_id:
            ids.append(model_version_id)
        for c in cand:
            if c not in ids:
                ids.append(c)
        ids = ids[:2]
        if len(ids) < 2:
            raise RuntimeError(f"可用模型版本不足2个（仅{ids}）")
        r = call("POST", BASE + "/api/v1/predictions/models/compare", token=token,
                 json={"modelId1": ids[0], "modelId2": ids[1]}, timeout=120)
        s.status = r.status_code
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        data, msg = extract_data(r)
        s.data = data
        s.key_fields = {"modelId1": ids[0], "modelId2": ids[1],
                        "hasResult": isinstance(data, dict) and len(data) > 0,
                        "recommended": data.get("recommended") if isinstance(data, dict) else None}
        s.done(r.status_code == 200, "ok" if r.status_code == 200 else f"比较失败({r.status_code}): {msg}")
    except Exception as e:
        s.elapsed_ms = (time.perf_counter() - t0) * 1000
        s.done(False, f"异常 {type(e).__name__}: {e}")
    steps.append(s); s.report(add)

    return steps


def main():
    buf = []
    def add(s=""):
        buf.append(s)
        print(s)

    add("=" * 70)
    add("RIver AGI E2E 全链路业务验收 (e2e_full_chain.py)")
    add(f"运行时间: {ts()}")
    add(f"后端: {BASE}  样本: {os.path.basename(SAMPLE_CSV)}")
    add("=" * 70)

    # admin userId from login
    admin_id = 1000
    ls = login(add)
    if ls.ok and ls.data:
        admin_id = (ls.data.get("user") or {}).get("id") or 1000
    add(f"管理员 userId={admin_id}\n")

    add("\n" + "#" * 70)
    c1 = chain1_annotation(add, admin_id)
    add("\n" + "#" * 70)
    c2 = chain2_prediction(add)

    def summarize(steps):
        passed = sum(1 for s in steps if s.ok)
        failed = [(s.name, s.reason) for s in steps if not s.ok]
        return passed, len(steps), failed

    p1, t1, f1 = summarize(c1)
    p2, t2, f2 = summarize(c2)

    add("=" * 70)
    add("验收汇总")
    add("=" * 70)
    add(f"链路1（采集标注）：通过 {p1}/{t1}  通过率 {p1/t1*100:.0f}%")
    if f1:
        add("  失败步骤：")
        for n, r in f1:
            add(f"    - {n}: {r}")
    add(f"链路2（预测评估）：通过 {p2}/{t2}  通过率 {p2/t2*100:.0f}%")
    if f2:
        add("  失败步骤：")
        for n, r in f2:
            add(f"    - {n}: {r}")
    add(f"\n总体：{p1+p2}/{t1+t2} 步通过")
    add(f"结束时间: {ts()}")

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(buf))
    print(f"\n[完成] 结果已写入: {OUT_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
