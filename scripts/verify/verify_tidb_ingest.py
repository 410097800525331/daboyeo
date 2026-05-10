from __future__ import annotations

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.tidb import connect_tidb, load_tidb_config


REQUIRED_TABLES = [
    "schema_migrations",
    "providers",
    "collection_runs",
    "movies",
    "canonical_movies",
    "movie_provider_links",
    "theaters",
    "screens",
    "showtimes",
    "showtime_prices",
    "seat_layouts",
    "seat_layout_items",
    "seat_snapshots",
    "seat_snapshot_items",
    "provider_status_codes",
    "provider_raw_payloads",
    "movie_tags",
    "recommendation_profiles",
    "recommendation_runs",
    "recommendation_feedback",
]

INGEST_COUNT_TABLES = [
    "movies",
    "theaters",
    "screens",
    "showtimes",
    "showtime_prices",
    "seat_snapshots",
    "seat_snapshot_items",
]

REQUIRED_MIGRATIONS = ["001", "002", "003", "004", "005"]


def fetch_existing_tables(cursor) -> set[str]:
    cursor.execute(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
        """
    )
    return {row[0] for row in cursor.fetchall()}


def main() -> int:
    config = load_tidb_config()
    print(f"verifying {config.safe_summary()}")

    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            existing_tables = fetch_existing_tables(cursor)
            missing_tables = [table for table in REQUIRED_TABLES if table not in existing_tables]
            for table in REQUIRED_TABLES:
                state = "present" if table in existing_tables else "missing"
                print(f"schema_table[{table}]={state}")
            if missing_tables:
                print("missing_required_tables=" + ",".join(missing_tables))
                return 1

            cursor.execute("SELECT version FROM schema_migrations ORDER BY version")
            applied_migrations = {row[0] for row in cursor.fetchall()}
            missing_migrations = [
                version for version in REQUIRED_MIGRATIONS if version not in applied_migrations
            ]
            print("schema_migrations=" + ",".join(sorted(applied_migrations)))
            if missing_migrations:
                print("missing_required_migrations=" + ",".join(missing_migrations))
                return 1

            for table in INGEST_COUNT_TABLES:
                cursor.execute(f"SELECT COUNT(*) FROM `{table}`")
                count = cursor.fetchone()[0]
                print(f"{table}={count}")

            cursor.execute(
                """
                SELECT provider_code, COUNT(*), COUNT(DISTINCT external_showtime_key)
                FROM showtimes
                GROUP BY provider_code
                ORDER BY provider_code
                """
            )
            for provider_code, total_count, distinct_count in cursor.fetchall():
                print(
                    f"showtimes_by_provider[{provider_code}]="
                    f"total:{total_count},distinct_keys:{distinct_count}"
                )

            cursor.execute(
                """
                SELECT COUNT(*)
                FROM (
                  SELECT provider_code, external_showtime_key
                  FROM showtimes
                  GROUP BY provider_code, external_showtime_key
                  HAVING COUNT(*) > 1
                ) duplicates
                """
            )
            duplicate_groups = cursor.fetchone()[0]
            print(f"duplicate_showtime_key_groups={duplicate_groups}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
