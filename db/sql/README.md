# SQL Policy

이 폴더는 TiDB/MySQL에 직접 적용할 수 있는 수동 SQL과 관련 메모를 둔다.

초기 SQL 이력은 아직 `scripts/db/`에 있다.

Spring Boot Flyway 전환 시에는 `backend/src/main/resources/db/migration/`에 `V1__...sql` 형식으로 추가한다. 기존 DB에 이미 적용된 수동 SQL과 충돌하지 않도록 baseline 전략을 먼저 정해야 한다.

권장 순서:

1. 현재 TiDB 스키마 상태 확인
2. 기존 `scripts/db/*.sql` 적용 여부 확인
3. Flyway baseline 버전 결정
4. 이후 변경부터 `Vn__description.sql`로 관리
