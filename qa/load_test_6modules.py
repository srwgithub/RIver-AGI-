#!/usr/bin/env python3
"""RIver AGI 6 模块核心业务接口 200 并发压测脚本。

仅依赖 Python 标准库 (urllib + concurrent.futures)。
用法:
    python3 qa/load_test_6modules.py
输出会打印到 stdout，可重定向到 qa-results/load-test-6modules.txt。
"""
import concurrent.futures
import json
import math
import statistics
import time
import urllib.error
import urllib.request

BASE_URL = "http://127.0.0.1:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
CONCURRENCY = 200
REQUESTS_PER_API = 200
REQUEST_TIMEOUT = 30  # 单请求超时秒数
P95_THRESHOLD_MS = 1500  # 普通查询接口达标阈值
COOLDOWN_SECONDS = 1.0  # 模块间冷却

# 6 模块核心只读 GET 接口
MODULES = [
    ("数据采集与标注", "/api/v1/collection-tasks?page=1&size=10"),
    ("标注质量管理", "/api/v1/annotation-tasks?page=1&size=10"),
    ("市场需求预测", "/api/v1/predictions?page=1&size=10"),
    ("趋势分析与可视化", "/api/v1/dashboards"),
    ("预测结果评估与优化", "/api/v1/predictions/evaluations?limit=20"),
    ("数据管理与安全审计", "/api/v1/audit/logs?page=1&size=10"),
]


def login():
    """登录并返回可直接用作 Authorization header 的 token。"""
    body = json.dumps({"username": "admin", "password": "admin123"}).encode("utf-8")
    req = urllib.request.Request(
        LOGIN_URL, data=body, method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    data = payload.get("data", {}) or {}
    token = data.get("token") or data.get("accessToken")
    if not token:
        raise RuntimeError(f"登录响应缺少 token: {payload}")
    # 任务说明 token 已含 "Bearer " 前缀；实际返回为裸 JWT，此处兼容两种情况
    if not token.startswith("Bearer "):
        token = "Bearer " + token
    return token


def do_request(url, token):
    """发一次 GET 请求，返回 (status_code, latency_ms)。status=0 表示异常。"""
    started = time.perf_counter()
    req = urllib.request.Request(url, method="GET", headers={"Authorization": token})
    try:
        with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        status = e.code
        try:
            e.read()
        except Exception:
            pass
    except Exception:
        status = 0
    latency_ms = (time.perf_counter() - started) * 1000
    return status, latency_ms


def percentile(sorted_latencies, p):
    """nearest-rank 百分位: rank=ceil(p/100*n), index=rank-1。"""
    if not sorted_latencies:
        return 0.0
    n = len(sorted_latencies)
    rank = max(1, math.ceil(p / 100 * n))
    idx = min(rank - 1, n - 1)
    return sorted_latencies[idx]


def display_width(s):
    """近似显示宽度: ASCII=1, 非 ASCII(中文等)=2。"""
    width = 0
    for ch in s:
        width += 2 if ord(ch) > 127 else 1
    return width


def pad(s, width):
    """按显示宽度右补空格。"""
    return s + " " * max(0, width - display_width(s))


def test_module(name, path, token):
    """对单个接口发 200 并发 200 请求，统计指标。"""
    url = BASE_URL + path
    results = []
    started_wall = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        futures = [pool.submit(do_request, url, token) for _ in range(REQUESTS_PER_API)]
        for f in concurrent.futures.as_completed(futures):
            results.append(f.result())
    elapsed_wall = time.perf_counter() - started_wall

    latencies = sorted(r[1] for r in results)
    successes = sum(1 for r in results if 200 <= r[0] < 300)
    status_counts = {}
    for r in results:
        status_counts[r[0]] = status_counts.get(r[0], 0) + 1

    return {
        "name": name,
        "path": path,
        "total": len(results),
        "success": successes,
        "failed": len(results) - successes,
        "success_rate": successes / len(results) * 100,
        "avg_ms": statistics.mean(latencies),
        "p50_ms": percentile(latencies, 50),
        "p95_ms": percentile(latencies, 95),
        "p99_ms": percentile(latencies, 99),
        "max_ms": max(latencies),
        "rps": len(results) / elapsed_wall if elapsed_wall > 0 else 0.0,
        "elapsed_wall_s": elapsed_wall,
        "status_counts": status_counts,
        # Latency alone is not a successful business response. A 500 response
        # must fail the contractual smoke gate even when it is fast.
        "passed": successes == len(results) and percentile(latencies, 95) <= P95_THRESHOLD_MS,
    }


def main():
    print("=" * 90)
    print("RIver AGI 6 模块核心业务接口 200 并发压测")
    print("=" * 90)
    print(f"目标地址: {BASE_URL}")
    print(f"并发数: {CONCURRENCY}    每接口请求数: {REQUESTS_PER_API}")
    print(f"判定阈值: 普通查询接口 P95 <= {P95_THRESHOLD_MS}ms 达标")
    print()

    print("[1/3] 登录获取 token ...")
    token = login()
    masked = token[:24] + "..." if len(token) > 24 else token
    print(f"  登录成功, token: {masked} (长度 {len(token)})")
    print()

    print("[2/3] 逐模块执行 200 并发压测")
    print()
    results = []
    for idx, (name, path) in enumerate(MODULES, 1):
        print(f"--- [{idx}/{len(MODULES)}] {name} ---")
        print(f"  GET {path}")
        r = test_module(name, path, token)
        results.append(r)
        print(f"  总请求: {r['total']}  成功: {r['success']}  失败: {r['failed']}  成功率: {r['success_rate']:.2f}%")
        print(f"  状态码分布: {r['status_counts']}")
        print(f"  平均延迟: {r['avg_ms']:.2f}ms  P50: {r['p50_ms']:.2f}ms  P95: {r['p95_ms']:.2f}ms  "
              f"P99: {r['p99_ms']:.2f}ms  最大: {r['max_ms']:.2f}ms")
        print(f"  吞吐 RPS: {r['rps']:.2f}  墙钟耗时: {r['elapsed_wall_s']:.3f}s")
        verdict = "达标" if r["passed"] else "不达标"
        op = "<=" if r["passed"] else ">"
        print(f"  P95 判定: {r['p95_ms']:.2f}ms {op} {P95_THRESHOLD_MS}ms -> {verdict}")
        print()
        time.sleep(COOLDOWN_SECONDS)

    print("[3/3] 达标判定总表")
    print()
    print("=" * 110)
    cols = [
        ("序号", 6),
        ("模块", 22),
        ("接口", 46),
        ("成功率", 9),
        ("P50(ms)", 10),
        ("P95(ms)", 10),
        ("P99(ms)", 10),
        ("RPS", 9),
        ("最大(ms)", 10),
        ("判定", 8),
    ]
    header = "".join(pad(c[0], c[1]) for c in cols)
    print(header)
    print("-" * 110)
    all_passed = True
    for idx, r in enumerate(results, 1):
        if not r["passed"]:
            all_passed = False
        path_short = r["path"] if len(r["path"]) <= 44 else r["path"][:41] + "..."
        verdict = "达标" if r["passed"] else "不达标"
        row = (
            pad(str(idx), 6)
            + pad(r["name"], 22)
            + pad(path_short, 46)
            + pad(f"{r['success_rate']:.2f}%", 9)
            + pad(f"{r['p50_ms']:.2f}", 10)
            + pad(f"{r['p95_ms']:.2f}", 10)
            + pad(f"{r['p99_ms']:.2f}", 10)
            + pad(f"{r['rps']:.2f}", 9)
            + pad(f"{r['max_ms']:.2f}", 10)
            + pad(verdict, 8)
        )
        print(row)
    print("-" * 110)
    overall = ("整体达标 (6/6 接口成功率 100% 且 P95 <= 1500ms)"
               if all_passed else
               "整体不达标 (存在失败响应或接口 P95 > 1500ms)")
    print(f"整体结论: {overall}")
    print("=" * 110)

    return 0 if all_passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
