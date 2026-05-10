from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.cgv import CgvCollector


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="CGV API collector demo")
    parser.add_argument("--site-no", default="0056", help="CGV site number")
    parser.add_argument("--movie-no", default="30000927", help="CGV movie number")
    parser.add_argument("--screening-date", default="20260408", help="CGV screening date in YYYYMMDD")
    parser.add_argument("--screen-no", default="002", help="CGV screen number")
    parser.add_argument("--screen-sequence", default="3", help="CGV screen sequence")
    parser.add_argument("--seat-area-no", default="", help="Optional CGV seat area number")
    parser.add_argument(
        "--mode",
        choices=[
            "bundle",
            "movies",
            "attributes",
            "regions",
            "sites",
            "dates",
            "schedules",
            "seats",
            "seat-summary",
            "seat-layout",
        ],
        default="bundle",
    )
    parser.add_argument("--output", help="Optional path to save JSON output")
    args = parser.parse_args()

    collector = CgvCollector()
    if args.mode == "bundle":
        output = collector.collect_bundle(
            site_no=args.site_no,
            mov_no=args.movie_no,
            scn_ymd=args.screening_date,
            scns_no=args.screen_no,
            scn_sseq=args.screen_sequence,
        )
    elif args.mode == "movies":
        output = collector.build_movie_records()
    elif args.mode == "attributes":
        output = collector.build_attribute_records()
    elif args.mode == "regions":
        output = collector.build_region_records()
    elif args.mode == "sites":
        output = collector.build_site_records()
    elif args.mode == "dates":
        output = collector.build_date_records(site_no=args.site_no, mov_no=args.movie_no)
    elif args.mode == "schedules":
        output = collector.build_schedule_records(
            site_no=args.site_no,
            scn_ymd=args.screening_date,
            mov_no=args.movie_no,
        )
    elif args.mode == "seats":
        output = collector.build_seat_records(
            site_no=args.site_no,
            scn_ymd=args.screening_date,
            scns_no=args.screen_no,
            scn_sseq=args.screen_sequence,
            seat_area_no=args.seat_area_no,
        )
    elif args.mode == "seat-summary":
        output = collector.summarize_seat_map(
            site_no=args.site_no,
            scn_ymd=args.screening_date,
            scns_no=args.screen_no,
            scn_sseq=args.screen_sequence,
            seat_area_no=args.seat_area_no,
        )
    else:
        output = collector.build_seat_layout(
            site_no=args.site_no,
            scn_ymd=args.screening_date,
            scns_no=args.screen_no,
            scn_sseq=args.screen_sequence,
            seat_area_no=args.seat_area_no,
        )

    text = json.dumps(output, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
