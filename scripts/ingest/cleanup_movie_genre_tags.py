from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.tidb import connect_tidb, load_tidb_config
from scripts.ingest.genre_tagging import CANONICAL_GENRES


def canonical_values() -> tuple[str, ...]:
    return tuple(sorted(CANONICAL_GENRES))


def placeholders(values: tuple[str, ...]) -> str:
    return ", ".join(["%s"] * len(values))


def audit_noncanonical_genres(cursor: Any) -> list[dict[str, Any]]:
    values = canonical_values()
    cursor.execute(
        f"""
        SELECT
          tag_value,
          COALESCE(source, '') AS source,
          COUNT(*) AS row_count,
          COUNT(DISTINCT CONCAT(provider_code, ':', external_movie_id)) AS movie_count
        FROM movie_tags
        WHERE tag_type = 'genre'
          AND tag_value NOT IN ({placeholders(values)})
        GROUP BY tag_value, source
        ORDER BY row_count DESC, tag_value ASC, source ASC
        """,
        values,
    )
    rows: list[dict[str, Any]] = []
    for tag_value, source, row_count, movie_count in cursor.fetchall():
        rows.append(
            {
                "tagValue": tag_value,
                "source": source,
                "rowCount": int(row_count or 0),
                "movieCount": int(movie_count or 0),
            }
        )
    return rows


def cleanup_noncanonical_genres(cursor: Any, dry_run: bool) -> dict[str, Any]:
    values = canonical_values()
    before = audit_noncanonical_genres(cursor)
    rows_before = sum(row["rowCount"] for row in before)
    movies_before = sum(row["movieCount"] for row in before)
    deleted = 0
    if not dry_run and rows_before > 0:
        cursor.execute(
            f"""
            DELETE FROM movie_tags
            WHERE tag_type = 'genre'
              AND tag_value NOT IN ({placeholders(values)})
            """,
            values,
        )
        deleted = int(cursor.rowcount or 0)
    after = audit_noncanonical_genres(cursor)
    return {
        "mode": "dry-run" if dry_run else "write",
        "canonicalGenres": list(values),
        "noncanonicalRowsBefore": rows_before,
        "noncanonicalMoviesBeforeApprox": movies_before,
        "deletedRows": deleted,
        "noncanonicalRowsAfter": sum(row["rowCount"] for row in after),
        "before": before,
        "after": after,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Delete movie_tags genre rows that are not in the canonical genre set."
    )
    parser.add_argument("--dry-run", action="store_true", help="Report rows that would be deleted without mutating TiDB.")
    return parser.parse_args(argv)


def run(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    config = load_tidb_config()
    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            result = cleanup_noncanonical_genres(cursor, dry_run=args.dry_run)
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
