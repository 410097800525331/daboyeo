# Migration Policy

이 폴더가 현재 수동 TiDB migration의 기준 위치다.

## 실행

```powershell
python scripts\db\apply_migrations.py
python scripts\db\inspect_tidb_schema.py
```

## 규칙

- 파일명은 `001_description.sql`, `002_description.sql`처럼 정렬 가능한 번호로 시작한다.
- migration은 가능하면 재실행 가능하게 작성한다.
- 이미 운영 DB에 적용한 migration은 수정하지 말고 새 번호로 추가한다.
- Spring Boot Flyway 전환 시에는 이 폴더를 기준으로 baseline 버전을 먼저 정한다.
