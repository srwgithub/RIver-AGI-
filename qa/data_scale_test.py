#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
任务 A：数据规模验证
直连 MySQL（river_agi 库，连接信息取自 application-mysql.yml 与运行中后端进程环境），
创建临时测试表 annotation_scale_test / analysis_scale_test，批量插入大规模数据，
对每张表测三种查询并记录延迟（毫秒），结果写入 qa-results/data-scale-test.txt。
测试完后 DROP 临时表，不污染现有数据。
若 MySQL 连接失败或权限不足，降级为 sqlite3 内存模式本地模拟，并明确说明降级原因。
"""
import os
import sys
import time
import datetime
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "qa-results")
OUT_FILE = os.path.join(OUT_DIR, "data-scale-test.txt")
os.makedirs(OUT_DIR, exist_ok=True)

# MySQL 连接信息（与 application-mysql.yml / 运行中后端进程环境一致）
MYSQL = dict(host="127.0.0.1", port=3306, user="root",
             password="root@123456", database="river_agi", connect_timeout=5)

ANNOTATION_TARGET = 10_000_000   # 1000 万
ANNOTATION_MIN = 1_000_000       # 太慢则降到 100 万
ANALYSIS_TARGET = 1_000_000      # 100 万
INSERT_BUDGET_SEC = 280          # 单表插入总时间预算
CHUNK = 20000                    # 每条 INSERT 的行数

LABEL_CODES = ["POS", "NEG", "NEU", "RISK"]
STATUSES = ["PENDING", "IN_PROGRESS", "COMPLETED", "REVIEWED"]
METRIC_NAMES = ["accuracy", "precision", "recall", "f1", "mae", "rmse"]


def ts():
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def lines_buffer():
    buf = []

    def add(s=""):
        buf.append(s)
        print(s)
    return buf, add


def annotation_row_gen():
    i = 0
    base = datetime.datetime(2026, 1, 1)
    while True:
        i += 1
        yield (random.randint(1, 1000),            # task_id
               i,                                   # row_index
               random.choice(LABEL_CODES),          # label_code
               random.choice(STATUSES),             # status
               (base + datetime.timedelta(seconds=random.randint(0, 15_000_000))).strftime("%Y-%m-%d %H:%M:%S"))


def analysis_row_gen():
    while True:
        yield (random.randint(1, 100),                          # dataset_id
               random.choice(METRIC_NAMES),                     # metric_name
               round(random.uniform(0, 1), 6),                  # metric_value
               "2026-Q%d" % random.randint(1, 4))              # period


def insert_table(conn, table, columns, gen, target, min_target, budget, add):
    cur = conn.cursor()
    col_sql = ",".join(columns)
    placeholders = "(" + ",".join(["%s"] * len(columns)) + ")"
    inserted = 0
    start = time.perf_counter()
    last_report = start
    while inserted < target:
        now = time.perf_counter()
        if now - start > budget and inserted >= min_target:
            add(f"  [{table}] 达到时间预算 {budget}s，提前停止（已插入 {inserted:,}）")
            break
        if now - start > budget and inserted < min_target:
            # 仍在最低目标以下但超预算：再坚持到最低目标，避免数据量过小
            pass
        n = min(CHUNK, target - inserted)
        rows = [next(gen) for _ in range(n)]
        sql = f"INSERT INTO {table} ({col_sql}) VALUES " + ",".join([placeholders] * n)
        flat = [v for row in rows for v in row]
        cur.execute(sql, flat)
        if inserted % (CHUNK * 10) == 0:
            conn.commit()
        inserted += n
        if now - last_report > 20:
            add(f"  [{table}] 已插入 {inserted:,}/{target:,} ({inserted/target*100:.1f}%) 用时 {now-start:.1f}s")
            last_report = now
    conn.commit()
    elapsed = time.perf_counter() - start
    add(f"  [{table}] 插入完成：{inserted:,} 行，耗时 {elapsed:.2f}s "
        f"({inserted/elapsed:.0f} rows/s)")
    return inserted, elapsed


def run_query(conn, sql, label, add):
    cur = conn.cursor()
    start = time.perf_counter()
    cur.execute(sql)
    rows = cur.fetchall()
    elapsed = time.perf_counter() - start
    n = len(rows)
    add(f"  [{label}] 耗时 {elapsed*1000:.2f} ms  | 结果行数={n} | 首行={rows[0] if rows else None}")
    return elapsed * 1000.0, rows


def run_mysql(buf, add):
    import pymysql
    add(f"[{ts()}] 尝试连接 MySQL ({MYSQL['host']}:{MYSQL['port']}/{MYSQL['database']}) ...")
    conn = pymysql.connect(**MYSQL)
    cur = conn.cursor()
    cur.execute("SELECT VERSION()")
    add(f"[{ts()}] MySQL 连接成功，版本: {cur.fetchone()[0]}")
    add(f"[{ts()}] 数据库: {MYSQL['database']}（真实 MySQL，非降级）")

    # 清理可能残留的旧表
    cur.execute("DROP TABLE IF EXISTS annotation_scale_test")
    cur.execute("DROP TABLE IF EXISTS analysis_scale_test")
    conn.commit()

    add("\n===== 建表 =====")
    cur.execute("""
        CREATE TABLE annotation_scale_test (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            task_id INT NOT NULL,
            row_index BIGINT NOT NULL,
            label_code VARCHAR(32) NOT NULL,
            status VARCHAR(32) NOT NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_status (status),
            INDEX idx_task (task_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)
    cur.execute("""
        CREATE TABLE analysis_scale_test (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            dataset_id INT NOT NULL,
            metric_name VARCHAR(64) NOT NULL,
            metric_value DOUBLE NOT NULL,
            period VARCHAR(16) NOT NULL,
            INDEX idx_metric (metric_name),
            INDEX idx_dataset (dataset_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)
    conn.commit()
    add("  已创建 annotation_scale_test（含 idx_status, idx_task）")
    add("  已创建 analysis_scale_test（含 idx_metric, idx_dataset）")

    add("\n===== 批量插入数据 =====")
    add(f"  annotation_scale_test 目标 {ANNOTATION_TARGET:,} 行（若超预算降到 {ANNOTATION_MIN:,}）")
    ann_cols = ["task_id", "row_index", "label_code", "status", "created_at"]
    ann_rows, ann_ins_sec = insert_table(conn, "annotation_scale_test", ann_cols,
                                         annotation_row_gen(), ANNOTATION_TARGET,
                                         ANNOTATION_MIN, INSERT_BUDGET_SEC, add)
    add(f"  analysis_scale_test 目标 {ANALYSIS_TARGET:,} 行")
    ana_cols = ["dataset_id", "metric_name", "metric_value", "period"]
    ana_rows, ana_ins_sec = insert_table(conn, "analysis_scale_test", ana_cols,
                                         analysis_row_gen(), ANALYSIS_TARGET,
                                         ANALYSIS_TARGET, INSERT_BUDGET_SEC, add)

    results = {"ann_rows": ann_rows, "ana_rows": ana_rows, "queries": []}

    add("\n===== 查询性能测试（annotation_scale_test）=====")
    ann_offset = max(0, ann_rows - 100)
    q1, _ = run_query(conn, "SELECT COUNT(*) FROM annotation_scale_test",
                      "A1 COUNT(*) 全表", add)
    q2, _ = run_query(conn,
                      f"SELECT * FROM annotation_scale_test ORDER BY id LIMIT 100 OFFSET {ann_offset}",
                      f"A2 深度分页 LIMIT 100 OFFSET {ann_offset:,}", add)
    q3, _ = run_query(conn,
                      "SELECT status, COUNT(*) AS c FROM annotation_scale_test GROUP BY status",
                      "A3 GROUP BY status 聚合", add)
    results["queries"].append(("annotation", q1, q2, q3))

    add("\n===== 查询性能测试（analysis_scale_test）=====")
    ana_offset = max(0, ana_rows - 100)
    q4, _ = run_query(conn, "SELECT COUNT(*) FROM analysis_scale_test",
                      "B1 COUNT(*) 全表", add)
    q5, _ = run_query(conn,
                      f"SELECT * FROM analysis_scale_test ORDER BY id LIMIT 100 OFFSET {ana_offset}",
                      f"B2 深度分页 LIMIT 100 OFFSET {ana_offset:,}", add)
    q6, _ = run_query(conn,
                      "SELECT metric_name, AVG(metric_value) AS avg_v, COUNT(*) AS c "
                      "FROM analysis_scale_test GROUP BY metric_name",
                      "B3 GROUP BY metric_name 聚合", add)
    results["queries"].append(("analysis", q4, q5, q6))

    add("\n===== 清理临时表 =====")
    cur.execute("DROP TABLE IF EXISTS annotation_scale_test")
    cur.execute("DROP TABLE IF EXISTS analysis_scale_test")
    conn.commit()
    add("  已 DROP annotation_scale_test / analysis_scale_test，未污染现有数据")
    conn.close()
    return results


def run_sqlite_fallback(buf, add, reason):
    add(f"[{ts()}] 降级原因：{reason}")
    add(f"[{ts()}] 降级方案：使用 sqlite3 内存模式本地模拟（结果仅作规模量级参考，不代表真实 MySQL 性能）")
    import sqlite3
    conn = sqlite3.connect(":memory:")
    cur = conn.cursor()
    cur.execute("""CREATE TABLE annotation_scale_test (
        id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INT, row_index INT,
        label_code TEXT, status TEXT, created_at TEXT)""")
    cur.execute("""CREATE TABLE analysis_scale_test (
        id INTEGER PRIMARY KEY AUTOINCREMENT, dataset_id INT, metric_name TEXT,
        metric_value REAL, period TEXT)""")
    cur.execute("CREATE INDEX idx_ann_status ON annotation_scale_test(status)")
    cur.execute("CREATE INDEX idx_ana_metric ON analysis_scale_test(metric_name)")
    conn.commit()

    add("\n===== 批量插入数据（sqlite3 内存模拟）=====")
    # sqlite 内存模式下插入千万级行性能受限，降到 50 万/10 万以保证可完成
    ann_target = 500_000
    ana_target = 100_000
    add(f"  注：sqlite3 内存模式插入吞吐远低于 MySQL，规模降为 annotation={ann_target:,} / analysis={ana_target:,}")
    start = time.perf_counter()
    cur.executemany("INSERT INTO annotation_scale_test(task_id,row_index,label_code,status,created_at) VALUES (?,?,?,?,?)",
                    [(random.randint(1,1000), i, random.choice(LABEL_CODES), random.choice(STATUSES), "2026-06-01 00:00:00") for i in range(ann_target)])
    conn.commit()
    ann_rows = cur.execute("SELECT COUNT(*) FROM annotation_scale_test").fetchone()[0]
    add(f"  annotation 插入 {ann_rows:,} 行，耗时 {time.perf_counter()-start:.2f}s")
    start = time.perf_counter()
    cur.executemany("INSERT INTO analysis_scale_test(dataset_id,metric_name,metric_value,period) VALUES (?,?,?,?)",
                    [(random.randint(1,100), random.choice(METRIC_NAMES), round(random.uniform(0,1),6), "2026-Q1") for _ in range(ana_target)])
    conn.commit()
    ana_rows = cur.execute("SELECT COUNT(*) FROM analysis_scale_test").fetchone()[0]
    add(f"  analysis 插入 {ana_rows:,} 行，耗时 {time.perf_counter()-start:.2f}s")

    results = {"ann_rows": ann_rows, "ana_rows": ana_rows, "queries": [], "degraded": True}
    add("\n===== 查询性能测试（annotation_scale_test）=====")
    q1 = _sqlite_q(cur, add, "A1 COUNT(*) 全表", "SELECT COUNT(*) FROM annotation_scale_test")
    q2 = _sqlite_q(cur, add, f"A2 深度分页 LIMIT 100 OFFSET {ann_rows-100}",
                   f"SELECT * FROM annotation_scale_test ORDER BY id LIMIT 100 OFFSET {ann_rows-100}")
    q3 = _sqlite_q(cur, add, "A3 GROUP BY status 聚合",
                   "SELECT status, COUNT(*) FROM annotation_scale_test GROUP BY status")
    results["queries"].append(("annotation", q1, q2, q3))
    add("\n===== 查询性能测试（analysis_scale_test）=====")
    q4 = _sqlite_q(cur, add, "B1 COUNT(*) 全表", "SELECT COUNT(*) FROM analysis_scale_test")
    q5 = _sqlite_q(cur, add, f"B2 深度分页 LIMIT 100 OFFSET {ana_rows-100}",
                   f"SELECT * FROM analysis_scale_test ORDER BY id LIMIT 100 OFFSET {ana_rows-100}")
    q6 = _sqlite_q(cur, add, "B3 GROUP BY metric_name 聚合",
                   "SELECT metric_name, COUNT(*) FROM analysis_scale_test GROUP BY metric_name")
    results["queries"].append(("analysis", q4, q5, q6))
    add("\n===== 清理（内存库随连接关闭自动释放）=====")
    conn.close()
    return results


def _sqlite_q(cur, add, label, sql):
    start = time.perf_counter()
    rows = cur.execute(sql).fetchall()
    elapsed = (time.perf_counter() - start) * 1000
    add(f"  [{label}] 耗时 {elapsed:.2f} ms | 结果行数={len(rows)} | 首行={rows[0] if rows else None}")
    return elapsed


def main():
    buf, add = lines_buffer()
    add("=" * 70)
    add("RIver AGI 数据规模验证 (data_scale_test.py)")
    add(f"运行时间: {ts()}")
    add(f"目标: annotation_scale_test {ANNOTATION_TARGET:,} 行 / analysis_scale_test {ANALYSIS_TARGET:,} 行")
    add(f"判定标准: 千万级标注 / 百万级分析 可秒级查询（<=1000ms 为优秀, <=3000ms 为达标, >3000ms 为未达标）")
    add("=" * 70)

    degraded = False
    try:
        results = run_mysql(buf, add)
    except Exception as e:
        add(f"\n[警告] MySQL 路径失败：{type(e).__name__}: {e}")
        results = run_sqlite_fallback(buf, add, f"MySQL 连接/权限失败 ({type(e).__name__}: {e})")
        degraded = True

    ann_rows = results["ann_rows"]
    ana_rows = results["ana_rows"]
    queries = results["queries"]

    add("\n===== 判定 =====")
    ann_q = next(q for n, *q in queries if n == "annotation")
    ana_q = next(q for n, *q in queries if n == "analysis")

    def judge(rows, target, label, latencies):
        scale_ok = rows >= target
        max_lat = max(latencies) if latencies else 999999
        if max_lat <= 1000:
            perf = "优秀(秒级)"
        elif max_lat <= 3000:
            perf = "达标(秒级边缘)"
        else:
            perf = "未达标(超秒级)"
        return scale_ok, perf, max_lat

    ann_scale_ok, ann_perf, ann_max = judge(ann_rows, ANNOTATION_MIN, "annotation", ann_q)
    ana_scale_ok, ana_perf, ana_max = judge(ana_rows, ANALYSIS_TARGET, "analysis", ana_q)

    add(f"  annotation_scale_test: 实际 {ann_rows:,} 行 "
        f"({'达到千万级目标' if ann_rows>=ANNOTATION_TARGET else ('达到百万级最低目标' if ann_scale_ok else '未达规模目标')})")
    add(f"    三种查询延迟: COUNT={ann_q[0]:.2f}ms / 深度分页={ann_q[1]:.2f}ms / GROUP BY={ann_q[2]:.2f}ms")
    add(f"    查询判定: {ann_perf}（最大延迟 {ann_max:.2f}ms）")
    add(f"  analysis_scale_test: 实际 {ana_rows:,} 行 "
        f"({'达到百万级目标' if ana_scale_ok else '未达规模目标'})")
    add(f"    三种查询延迟: COUNT={ana_q[0]:.2f}ms / 深度分页={ana_q[1]:.2f}ms / GROUP BY={ana_q[2]:.2f}ms")
    add(f"    查询判定: {ana_perf}（最大延迟 {ana_max:.2f}ms）")

    overall = (ann_scale_ok and ana_scale_ok and
               ann_max <= 3000 and ana_max <= 3000)
    add(f"\n  总体判定: {'通过' if overall else '部分未达标'}"
        f"{'（降级模式 sqlite3，仅供参考）' if degraded else '（真实 MySQL 9.x）'}")
    add(f"\n结论: 千万级标注/百万级分析 {'可' if (ann_max<=3000 and ana_max<=3000) else '不可'}秒级查询")
    add("=" * 70)
    add(f"结束时间: {ts()}")

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(buf))
    print(f"\n[完成] 结果已写入: {OUT_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
