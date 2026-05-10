from __future__ import annotations

import argparse
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.storage import load_r2_config, list_object_keys


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check Cloudflare R2 configuration")
    parser.add_argument("--prefix", default="")
    args = parser.parse_args(argv)

    config = load_r2_config()
    if not config.configured:
        print("R2 설정 부족: R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_BUCKET_NAME 확인 필요")
        return 1

    keys = list_object_keys(prefix=args.prefix, config=config, limit=5)
    print(f"bucket={config.bucket_name}")
    print(f"endpoint={config.endpoint_url}")
    print("sample_keys=" + ",".join(keys))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
