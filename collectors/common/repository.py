from __future__ import annotations

import re
from collections.abc import Mapping, Sequence
from typing import Any


IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def ensure_connection(cursor: Any) -> None:
    connection = getattr(cursor, "connection", None)
    ping = getattr(connection, "ping", None)
    if callable(ping):
        ping(reconnect=True)


def quote_identifier(name: str) -> str:
    if not IDENTIFIER_RE.match(name):
        raise ValueError(f"unsafe SQL identifier: {name}")
    return f"`{name}`"


def upsert_dict(
    cursor: Any,
    table: str,
    row: Mapping[str, Any],
    update_columns: Sequence[str] | None = None,
) -> int:
    if not row:
        raise ValueError("upsert row is empty")

    columns = list(row.keys())
    safe_table = quote_identifier(table)
    safe_columns = [quote_identifier(column) for column in columns]
    placeholders = ", ".join(["%s"] * len(columns))

    if update_columns is None:
        update_columns = [
            column
            for column in columns
            if column not in {"id", "created_at", "first_collected_at"}
        ]

    update_expr = ", ".join(
        f"{quote_identifier(column)} = VALUES({quote_identifier(column)})"
        for column in update_columns
    )
    sql = (
        f"INSERT INTO {safe_table} ({', '.join(safe_columns)}) "
        f"VALUES ({placeholders}) "
        f"ON DUPLICATE KEY UPDATE {update_expr}"
    )
    ensure_connection(cursor)
    cursor.execute(sql, [row[column] for column in columns])
    return cursor.rowcount


def select_id(
    cursor: Any,
    table: str,
    where: Mapping[str, Any],
    id_column: str = "id",
) -> int | None:
    if not where:
        raise ValueError("select where is empty")

    conditions = " AND ".join(f"{quote_identifier(column)} = %s" for column in where)
    sql = (
        f"SELECT {quote_identifier(id_column)} "
        f"FROM {quote_identifier(table)} "
        f"WHERE {conditions} "
        "LIMIT 1"
    )
    ensure_connection(cursor)
    cursor.execute(sql, list(where.values()))
    row = cursor.fetchone()
    return int(row[0]) if row else None


def upsert_and_select_id(
    cursor: Any,
    table: str,
    row: Mapping[str, Any],
    where: Mapping[str, Any],
    update_columns: Sequence[str] | None = None,
    id_column: str = "id",
) -> int:
    upsert_dict(cursor, table, row, update_columns=update_columns)
    row_id = select_id(cursor, table, where, id_column=id_column)
    if row_id is None:
        raise RuntimeError(f"upserted row not found: {table}")
    return row_id


def insert_dict(cursor: Any, table: str, row: Mapping[str, Any]) -> int:
    if not row:
        raise ValueError("insert row is empty")
    columns = list(row.keys())
    sql = (
        f"INSERT INTO {quote_identifier(table)} "
        f"({', '.join(quote_identifier(column) for column in columns)}) "
        f"VALUES ({', '.join(['%s'] * len(columns))})"
    )
    ensure_connection(cursor)
    cursor.execute(sql, [row[column] for column in columns])
    return cursor.lastrowid


def insert_many_dicts(cursor: Any, table: str, rows: Sequence[Mapping[str, Any]]) -> int:
    if not rows:
        return 0
    columns = list(rows[0].keys())
    sql = (
        f"INSERT INTO {quote_identifier(table)} "
        f"({', '.join(quote_identifier(column) for column in columns)}) "
        f"VALUES ({', '.join(['%s'] * len(columns))})"
    )
    ensure_connection(cursor)
    cursor.executemany(sql, [[row[column] for column in columns] for row in rows])
    return cursor.rowcount
