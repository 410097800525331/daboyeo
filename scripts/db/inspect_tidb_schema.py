from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.tidb import connect_tidb, load_tidb_config


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Inspect current TiDB schema")
    parser.add_argument("--table", help="Optional table name to print columns for")
    args = parser.parse_args(argv)

    config = load_tidb_config()
    print(f"schema: {config.safe_summary()}")

    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            cursor.execute("SHOW TABLES")
            tables = [row[0] for row in cursor.fetchall()]
            print("tables=" + ",".join(tables))

            cursor.execute("SELECT version FROM schema_migrations ORDER BY version")
            migrations = [row[0] for row in cursor.fetchall()]
            print("migrations=" + ",".join(migrations))

            if args.table:
                cursor.execute(
                    """
                    SELECT column_name, column_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = %s
                    ORDER BY ordinal_position
                    """,
                    (args.table,),
                )
                for name, column_type, nullable in cursor.fetchall():
                    print(f"{name}\t{column_type}\tnullable={nullable}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
