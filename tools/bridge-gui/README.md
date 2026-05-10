# DABOYEO Bridge GUI

시연 중에만 Codex bridge worker를 켜고 끄는 로컬 Electron GUI다.

## 실행

```powershell
cd tools/bridge-gui
npm install
npm start
```

## 동작 원칙

- 자동 시작하지 않는다.
- repo root의 `.env`를 읽지만 secret 값은 화면에 출력하지 않는다.
- `DABOYEO_AI_BRIDGE_TOKEN`은 존재 여부만 보여준다.
- `Start bridge`는 `scripts/ai_bridge_agent.py`를 child process로 실행한다.
- `Kill bridge`는 worker process tree를 종료한다.
- 창을 닫을 때도 실행 중인 worker를 종료해 orphan process를 남기지 않는다.

## 필요한 env

- `DABOYEO_AI_BRIDGE_TOKEN`
- `DABOYEO_BRIDGE_SERVER`, 기본값 `http://127.0.0.1:5500`
- `DABOYEO_BRIDGE_PROVIDERS`, 기본값 `codex`
- `DABOYEO_BRIDGE_PYTHON` 또는 `PYTHON`, 없으면 `python`, `py`, `python3` 순서로 탐색
