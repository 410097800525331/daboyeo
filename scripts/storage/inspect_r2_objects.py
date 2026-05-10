from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.storage import list_object_keys, load_r2_config


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="List R2 object keys")
    parser.add_argument("--prefix", default="raw/")
    parser.add_argument("--limit", type=int, default=20)
    args = parser.parse_args(argv)

    config = load_r2_config()
    for key in list_object_keys(prefix=args.prefix, config=config, limit=args.limit):
        print(key)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
