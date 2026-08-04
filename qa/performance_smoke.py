#!/usr/bin/env python3
"""Repeatable concurrent smoke test; use a real load tool for contractual capacity proof."""
import argparse
import concurrent.futures
import statistics
import time
import urllib.request


def request(url):
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            response.read()
            return response.status, (time.perf_counter() - started) * 1000
    except Exception:
        return 0, (time.perf_counter() - started) * 1000


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', default='http://127.0.0.1:8080/actuator/health')
    parser.add_argument('--users', type=int, default=200)
    parser.add_argument('--requests', type=int, default=200)
    args = parser.parse_args()
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.users) as pool:
        results = list(pool.map(request, [args.url] * args.requests))
    elapsed = time.perf_counter() - started
    latencies = [x[1] for x in results]
    successes = sum(200 <= x[0] < 300 for x in results)
    print(f'url={args.url}')
    print(f'requests={len(results)} success={successes} failed={len(results)-successes}')
    print(f'elapsed_seconds={elapsed:.3f} throughput_rps={len(results)/elapsed:.2f}')
    print(f'latency_ms_avg={statistics.mean(latencies):.2f} p95={sorted(latencies)[int(len(latencies)*.95)-1]:.2f} max={max(latencies):.2f}')
    return 0 if successes == len(results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
