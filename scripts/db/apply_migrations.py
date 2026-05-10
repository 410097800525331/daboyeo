from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.tidb import connect_tidb, load_tidb_config


DEFAULT_MIGRATION_DIR = PROJECT_ROOT / "db" / "migrations"


def strip_line_comments(sql_text: str) -> str:
    lines: list[str] = []
    for line in sql_text.splitlines():
        stripped = line.lstrip()
        if stripped.startswith("--"):
            continue
        lines.append(line)
    return "\n".join(lines)


def split_sql(sql_text: str) -> list[str]:
    cleaned = strip_line_comments(sql_text)
    return [statement.strip() for statement in cleaned.split(";") if statement.strip()]


def migration_files(migration_dir: Path) -> list[Path]:
    return sorted(path for path in migration_dir.glob("*.sql") if path.is_file())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Apply daboyeo TiDB SQL migrations")
    parser.add_argument("--migration-dir", type=Path, default=DEFAULT_MIGRATION_DIR)
    parser.add_argument("--dry-run", action="store_true", help="Print migration files without executing SQL")
    args = parser.parse_args(argv)

    files = migration_files(args.migration_dir)
    if not files:
        print(f"migration files not found: {args.migration_dir}")
        return 1

    if args.dry_run:
        for path in files:
            print(path.relative_to(PROJECT_ROOT))
        return 0

    config = load_tidb_config()
    print(f"applying migrations to {config.safe_summary()}")

    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            for path in files:
                statements = split_sql(path.read_text(encoding="utf-8"))
                for statement in statements:
                    cursor.execute(statement)
                print(f"applied {path.name} ({len(statements)} statements)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
