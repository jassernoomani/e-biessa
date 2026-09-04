"""Evaluate the live Spring -> FastAPI -> Ollama retrieval path."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import httpx


ROOT = Path(__file__).resolve().parent


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8080/api/ai/search")
    parser.add_argument("--benchmark", type=Path, default=ROOT / "data" / "retrieval_benchmark.json")
    parser.add_argument("--output", type=Path, default=ROOT / "retrieval-results.json")
    parser.add_argument("--minimum-score", type=float, default=0.5)
    parser.add_argument("--timeout", type=float, default=120)
    return parser.parse_args()


def main() -> int:
    args = arguments()
    cases = json.loads(args.benchmark.read_text(encoding="utf-8"))
    details = []
    reciprocal_rank_total = 0.0
    constraint_correct = 0
    constraint_total = 0

    with httpx.Client(timeout=args.timeout) as client:
        for index, case in enumerate(cases, start=1):
            response = client.get(args.url, params={
                "query": case["query"], "limit": 12, "minimumScore": args.minimum_score,
            })
            response.raise_for_status()
            payload = response.json()
            titles = [result["part"]["title"] for result in payload.get("results", [])]
            expected_title = case["expectedTitle"]
            rank = next((position for position, title in enumerate(titles, 1) if title == expected_title), None)
            if rank:
                reciprocal_rank_total += 1 / rank

            actual_constraints = payload.get("extractedConstraints", {})
            expected_constraints = case.get("expectedConstraints", {})
            matches = {
                key: actual_constraints.get(key) == expected
                for key, expected in expected_constraints.items()
            }
            constraint_correct += sum(matches.values())
            constraint_total += len(matches)
            detail = {
                "language": case["language"], "query": case["query"],
                "expectedTitle": expected_title, "rank": rank,
                "constraintMatches": matches, "tookMs": payload.get("tookMs"),
                "answerGeneratedBy": (payload.get("answer") or {}).get("generatedBy"),
            }
            details.append(detail)
            print(f"[{index:>2}/{len(cases)}] {case['language']}: rank={rank or '-'} constraints={sum(matches.values())}/{len(matches)}")

    count = len(cases)
    report = {
        "cases": count,
        "hitRateAt12": sum(item["rank"] is not None for item in details) / count if count else 0,
        "meanReciprocalRank": reciprocal_rank_total / count if count else 0,
        "constraintAccuracy": constraint_correct / constraint_total if constraint_total else 0,
        "details": details,
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "details"}, indent=2))
    print(f"Saved detailed results to {args.output.resolve()}")
    return 0 if report["hitRateAt12"] == 1 and report["constraintAccuracy"] >= 0.95 else 1


if __name__ == "__main__":
    raise SystemExit(main())
