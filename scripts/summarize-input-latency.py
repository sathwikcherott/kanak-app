#!/usr/bin/env python3
"""Summarize dedicated timing logs without confusing draw and input latency."""
import json
import math
import statistics
import sys
from pathlib import Path

def load_report(text, run_id=None):
    reports = []
    for line in text.splitlines():
        if "KANAK_INPUT_TIMING" not in line or "{" not in line:
            continue
        try:
            report = json.loads(line[line.index("{"):])
        except json.JSONDecodeError:
            continue
        if "samples" in report and (run_id is None or report.get("run_id") == run_id):
            reports.append(report)
    if not reports:
        raise ValueError("No complete timing report found; no latency estimate can be made.")
    report = reports[-1]
    rows = report["samples"]
    if len(rows) != 24:
        raise ValueError("Expected 24 timing samples.")
    for row in rows:
        for key in ("delivery_ms", "dispatch_ms", "next_draw_ms"):
            if not isinstance(row.get(key), (int, float)) or row[key] < 0:
                raise ValueError("Invalid timing sample.")
    return report

def summarize(report):
    lines = [f"Device: {report['device']}; Android API {report['sdk']}; {report['build_type']} build",
             "Injected input, not physical touchscreen latency. Next draw is NOT confirmed visible response."]
    for label, rows in (("All taps", report["samples"]), ("First cycle", report["samples"][:4]), ("Warm cycles", report["samples"][4:])):
        lines.append(f"{label} ({len(rows)} samples):")
        for field in ("delivery_ms", "dispatch_ms", "next_draw_ms"):
            values = sorted(row[field] for row in rows)
            p95 = values[math.ceil(len(values) * .95) - 1]
            lines.append(f"  {field}: median={statistics.median(values):.1f} ms; p95={p95:.1f} ms; max={max(values):.1f} ms")
    return "\n".join(lines)

if __name__ == "__main__":
    try:
        print(summarize(load_report(Path(sys.argv[1]).read_text(), sys.argv[2] if len(sys.argv) > 2 else None)))
    except (IndexError, OSError, ValueError, KeyError) as error:
        sys.exit(str(error))
