# Portfolio-Free R2 Poster And AI Provider Plan

## Runtime Contract

- Portfolio deploy defaults to `DABOYEO_RECOMMEND_PROVIDER=fallback`.
- `fallback` uses local scoring only. It does not call Codex, OpenAI API, or any paid AI endpoint.
- `codex` is a demo-only provider. It works only when `DABOYEO_AI_BRIDGE_TOKEN` is set on the server and the bridge worker is running with the same token.
- Removing or blanking `DABOYEO_AI_BRIDGE_TOKEN` disables Codex routing without code changes, even if `DABOYEO_RECOMMEND_PROVIDER=codex`.
- `openai-api` is reserved as a future provider boundary and stays disabled in the free portfolio runtime.
- Public collection triggers are disabled by default in the portfolio runtime. Keep `DABOYEO_PUBLIC_COLLECTION_ENABLED=false`, `DABOYEO_PUBLIC_NEARBY_REFRESH_ENABLED=false`, and `DABOYEO_PUBLIC_SEAT_LAYOUT_ENABLED=false` unless an operator intentionally enables a demo path.
- Manual refresh/crawl calls require `DABOYEO_ADMIN_TOKEN` and the `X-DABOYEO-ADMIN-TOKEN` request header when public collection is disabled.
- Production CORS should list exact deployed origins only. Do not use wildcard dev-port origins such as `http://*:5173` in the Oracle env.

## Poster Storage Contract

- Cloudflare R2 stores poster image bytes under `posters/...`.
- TiDB stores the final display URL and R2 metadata:
  - `poster_url`
  - `poster_source_url`
  - `poster_r2_key`
  - `poster_etag`
  - `poster_storage_status`
  - `poster_stored_at`
- Ingest keeps the provider source URL if R2 is unconfigured or upload fails.
- R2 failures are non-fatal and are reported in ingest result counters.

## Commands

Install Python collector dependencies:

```powershell
python -m pip install -r requirements.txt
```

Dry-run fixed seed poster upload:

```powershell
python scripts/storage/upload_seed_posters_to_r2.py
```

Upload fixed seed posters to R2:

```powershell
python scripts/storage/upload_seed_posters_to_r2.py --write
```

Run normal ingest with automatic poster mirroring when R2 config exists:

```powershell
python scripts/ingest/collect_all_to_tidb.py --provider all --all-provider-dates
```

Run ingest without R2 poster mirroring:

```powershell
python scripts/ingest/collect_all_to_tidb.py --provider all --skip-r2-posters
```

Dry-run Oracle redeploy without printing secrets:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1 -DryRun
```

Redeploy the current jar, sanitized env, and bridge worker to Oracle:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1
```

Localhost demo-only Codex provider:

```powershell
$env:DABOYEO_RECOMMEND_PROVIDER = "codex"
python scripts/ai_bridge_agent.py --server http://127.0.0.1:5500
```

Oracle demo-only Codex provider:

```powershell
# Server env: DABOYEO_RECOMMEND_PROVIDER=codex and DABOYEO_AI_BRIDGE_TOKEN=<same-token>
# Demo machine env: DABOYEO_BRIDGE_SERVER=http://<oracle-public-ip> and DABOYEO_AI_BRIDGE_TOKEN=<same-token>
python scripts/ai_bridge_agent.py
```

After the demo, stop the bridge process. To fully disable the path, remove or blank `DABOYEO_AI_BRIDGE_TOKEN` from the server env and restart the Spring service.

Electron bridge GUI:

```powershell
cd tools/bridge-gui
npm install
npm start
```

The GUI reads `.env`, shows whether the bridge token exists without printing it, starts the worker only when the operator clicks Start, and kills the worker process tree when the operator clicks Kill.
