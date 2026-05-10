from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ENV_PATH = PROJECT_ROOT / ".env"


@dataclass(frozen=True)
class TidbConfig:
    host: str
    port: int
    user: str
    password: str
    database: str
    use_ssl: bool = True

    @property
    def configured(self) -> bool:
        return bool(self.host and self.port and self.user and self.password and self.database)

    def safe_summary(self) -> str:
        return f"{self.user}@{self.host}:{self.port}/{self.database}"


def parse_env_file(path: Path = DEFAULT_ENV_PATH) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def _env_value(name: str, file_values: dict[str, str], default: str = "") -> str:
    return os.environ.get(name, file_values.get(name, default))


def load_tidb_config(env_path: Path = DEFAULT_ENV_PATH) -> TidbConfig:
    file_values = parse_env_file(env_path)
    database_url = _env_value("DATABASE_URL", file_values)

    if database_url and not _env_value("TIDB_HOST", file_values):
        parsed = urlparse(database_url)
        return TidbConfig(
            host=parsed.hostname or "",
            port=parsed.port or 4000,
            user=parsed.username or "",
            password=parsed.password or "",
            database=(parsed.path or "/").lstrip("/"),
            use_ssl=True,
        )

    ssl_value = _env_value("TIDB_SSL", file_values, "true").lower()
    return TidbConfig(
        host=_env_value("TIDB_HOST", file_values),
        port=int(_env_value("TIDB_PORT", file_values, "4000")),
        user=_env_value("TIDB_USER", file_values),
        password=_env_value("TIDB_PASSWORD", file_values),
        database=_env_value("TIDB_DATABASE", file_values),
        use_ssl=ssl_value not in {"0", "false", "no"},
    )


def connect_tidb(config: TidbConfig | None = None):
    try:
        import pymysql
    except ImportError as exc:
        raise RuntimeError("PyMySQL이 필요함. python -m pip install --user PyMySQL") from exc

    effective = config or load_tidb_config()
    if not effective.configured:
        raise RuntimeError("TiDB 설정이 부족함. TIDB_HOST/TIDB_USER/TIDB_PASSWORD/TIDB_DATABASE 확인 필요")

    ssl_arg = {"ssl": {}} if effective.use_ssl else None
    return pymysql.connect(
        host=effective.host,
        port=effective.port,
        user=effective.user,
        password=effective.password,
        database=effective.database,
        ssl=ssl_arg,
        charset="utf8mb4",
        autocommit=True,
    )
