#!/usr/bin/env python
"""Poll DABOYEO AI bridge jobs and run the Codex worker."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any
from urllib import error, request


TOKEN_HEADER = "X-DABOYEO-BRIDGE-TOKEN"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the DABOYEO AI bridge worker.")
    parser.add_argument("--server", default=os.getenv("DABOYEO_BRIDGE_SERVER", "http://127.0.0.1:5500"))
    parser.add_argument("--token", default=os.getenv("DABOYEO_AI_BRIDGE_TOKEN", ""))
    parser.add_argument("--providers", default=os.getenv("DABOYEO_BRIDGE_PROVIDERS", "codex"))
    parser.add_argument("--codex-command", default=os.getenv("DABOYEO_CODEX_COMMAND", "codex"))
    parser.add_argument("--codex-cwd", default=os.getenv("DABOYEO_CODEX_CWD", str(Path.cwd())))
    parser.add_argument("--codex-model", default=os.getenv("DABOYEO_CODEX_MODEL", ""))
    parser.add_argument("--codex-reasoning-effort", default=os.getenv("DABOYEO_CODEX_REASONING_EFFORT", ""))
    parser.add_argument("--poll-interval", type=float, default=float(os.getenv("DABOYEO_BRIDGE_POLL_INTERVAL", "2")))
    parser.add_argument("--timeout", type=int, default=int(os.getenv("DABOYEO_BRIDGE_WORKER_TIMEOUT", "70")))
    parser.add_argument("--once", action="store_true")
    return parser.parse_args()


def api_json(server: str, token: str, method: str, path: str, body: Any | None = None) -> tuple[int, Any | None]:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {TOKEN_HEADER: token, "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = request.Request(server.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=15) as response:
            payload = response.read()
            if not payload:
                return response.status, None
            return response.status, json.loads(payload.decode("utf-8"))
    except error.HTTPError as exc:
        if exc.code == 204:
            return 204, None
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from {path}: {detail}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"request failed for {path}: {exc.reason}") from exc


def heartbeat(args: argparse.Namespace, bridge_id: str, providers: list[str]) -> None:
    api_json(args.server, args.token, "POST", "/api/internal/ai-bridge/heartbeat", {
        "bridgeId": bridge_id,
        "providers": providers,
    })


def active_providers(args: argparse.Namespace, providers: list[str]) -> list[str]:
    active: list[str] = []
    for provider in providers:
        if provider == "codex" and resolve_codex_command(args.codex_command):
            active.append(provider)
    return active


def resolve_codex_command(command: str) -> str:
    if not command:
        return ""
    configured = Path(command)
    if configured.exists():
        if configured.is_file():
            return str(configured)
        exe_sibling = Path(str(configured) + ".exe")
        return str(exe_sibling) if exe_sibling.is_file() else ""

    candidates = [command]
    if os.name == "nt" and not command.lower().endswith(".exe"):
        candidates.insert(0, command + ".exe")
    for candidate in candidates:
        resolved = shutil.which(candidate)
        if resolved and Path(resolved).is_file():
            return resolved
    return ""


def claim_job(args: argparse.Namespace, provider: str, bridge_id: str) -> dict[str, Any] | None:
    path = f"/api/internal/ai-bridge/jobs?provider={provider}&bridgeId={bridge_id}"
    status, payload = api_json(args.server, args.token, "GET", path)
    if status == 204:
        return None
    return payload


def complete_job(args: argparse.Namespace, job_id: str, raw_json: str = "", error_message: str = "") -> None:
    api_json(args.server, args.token, "POST", f"/api/internal/ai-bridge/jobs/{job_id}/result", {
        "rawJson": raw_json,
        "error": error_message,
    })


def run_codex(args: argparse.Namespace, job: dict[str, Any]) -> str:
    request_body = job.get("request") or {}
    schema = extract_output_schema(request_body)
    prompt = codex_prompt(request_body)
    codex_command = resolve_codex_command(args.codex_command)
    if not codex_command:
        raise RuntimeError("codex command is not available")
    tmp_parent = Path(args.codex_cwd) / "backend" / "build" / "tmp" / "ai-bridge"
    tmp_parent.mkdir(parents=True, exist_ok=True)
    tmp = tmp_parent / ("daboyeo-codex-bridge-" + uuid.uuid4().hex)
    tmp.mkdir(parents=False, exist_ok=False)
    try:
        schema_path = tmp / "schema.json"
        output_path = tmp / "result.json"
        schema_path.write_text(json.dumps(schema, ensure_ascii=False), encoding="utf-8")
        command = [
            codex_command,
            "--ask-for-approval",
            "never",
            "exec",
            "--ignore-user-config",
            "--disable",
            "plugins",
            "--ephemeral",
            "--sandbox",
            "read-only",
            "-C",
            args.codex_cwd,
            "--output-schema",
            str(schema_path),
            "-o",
            str(output_path),
            "-",
        ]
        codex_global_options = codex_exec_global_options(args, job)
        if codex_global_options:
            command[3:3] = codex_global_options
        completed = subprocess.run(
            command,
            input=prompt,
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
            timeout=args.timeout,
            check=False,
        )
        if completed.returncode != 0:
            stderr = completed.stderr.strip() or completed.stdout.strip()
            raise RuntimeError(f"codex exec failed: {stderr[:800]}")
        if output_path.exists():
            output = output_path.read_text(encoding="utf-8").strip()
        else:
            output = completed.stdout.strip()
        if not output:
            raise RuntimeError("codex exec returned an empty result")
        return output
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def codex_exec_global_options(args: argparse.Namespace, job: dict[str, Any]) -> list[str]:
    request_body = job.get("request") or {}
    codex_model = str(request_body.get("model") or job.get("model") or args.codex_model or "").strip()
    if str(request_body.get("model") or "").strip():
        reasoning_source = request_body.get("reasoning_effort") or ""
    else:
        reasoning_source = request_body.get("reasoning_effort") or args.codex_reasoning_effort or ""
    reasoning_effort = str(reasoning_source).strip().lower()
    options: list[str] = []
    if codex_model:
        options.extend(["--model", codex_model])
    if reasoning_effort:
        options.extend(["-c", f'model_reasoning_effort="{reasoning_effort}"'])
    return options


def extract_output_schema(request_body: dict[str, Any]) -> dict[str, Any]:
    response_format = request_body.get("response_format") or {}
    json_schema = response_format.get("json_schema") or {}
    schema = json_schema.get("schema") or response_format.get("schema")
    if isinstance(schema, dict):
        return schema
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["r"],
        "properties": {
            "r": {"type": "array", "maxItems": 3, "items": {"type": "object"}}
        },
    }


def codex_prompt(request_body: dict[str, Any]) -> str:
    messages = request_body.get("messages") or []
    lines = [
        "You are the DABOYEO recommendation bridge worker.",
        "Use only the supplied movie/showtime candidates.",
        "Return only JSON that matches the provided output schema.",
        "Do not run commands, edit files, browse, or invent unavailable facts.",
        "",
    ]
    for message in messages:
        role = message.get("role", "user")
        content = message.get("content", "")
        lines.append(f"[{role}]")
        lines.append(str(content))
        lines.append("")
    return "\n".join(lines)


def process_job(args: argparse.Namespace, job: dict[str, Any]) -> None:
    job_id = job["jobId"]
    provider = job.get("provider", "")
    try:
        if provider == "codex":
            raw_json = run_codex(args, job)
        else:
            raise RuntimeError(f"unsupported provider: {provider}")
        complete_job(args, job_id, raw_json=raw_json)
        print(f"completed {provider} job {job_id}")
    except Exception as exc:  # noqa: BLE001
        try:
            complete_job(args, job_id, error_message=str(exc))
        except Exception as report_exc:  # noqa: BLE001
            print(f"failed to report {provider} job {job_id}: {report_exc}", file=sys.stderr)
        print(f"failed {provider} job {job_id}: {exc}", file=sys.stderr)


def main() -> int:
    args = parse_args()
    providers = [value.strip().lower() for value in args.providers.split(",") if value.strip()]
    if not args.token:
        print("DABOYEO_AI_BRIDGE_TOKEN is required.", file=sys.stderr)
        return 2
    if not providers:
        print("At least one provider is required.", file=sys.stderr)
        return 2
    bridge_id = "bridge_" + uuid.uuid4().hex[:12]
    print(f"AI bridge started: server={args.server} providers={','.join(providers)} bridgeId={bridge_id}")
    while True:
        try:
            live_providers = active_providers(args, providers)
            heartbeat(args, bridge_id, live_providers)
            did_work = False
            for provider in live_providers:
                job = claim_job(args, provider, bridge_id)
                if job:
                    did_work = True
                    process_job(args, job)
        except Exception as exc:  # noqa: BLE001
            print(f"bridge poll failed: {exc}", file=sys.stderr)
            if args.once:
                return 1
            time.sleep(max(args.poll_interval, 5))
            continue
        if args.once:
            return 0
        if not did_work:
            time.sleep(args.poll_interval)


if __name__ == "__main__":
    raise SystemExit(main())
