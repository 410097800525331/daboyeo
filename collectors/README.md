# Collectors

Lotte Cinema and Megabox raw-data collectors live here.

Current scope is intentionally limited to the providers still used by the app.
Spring Boot owns persistence and API serving; these Python modules only collect
provider payloads while preserving source-specific fields as much as possible.

## Responsibilities

- Call provider APIs.
- Preserve raw response details.
- Extract the smallest common comparison fields.
- Read secrets only from environment variables.

## Providers

- `lotte/`
- `megabox/`
- `common/`
