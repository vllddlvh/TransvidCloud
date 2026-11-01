# Project Harness Configuration

## Repository

- Project name: `TransVidCloud Backend`
- Repository type: `Monorepo (Backend Service Module)`
- Primary branch: `main`
- Language(s): `Java 21`
- Framework(s): `Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2, MapStruct, Lombok`
- Runtime versions: `JDK 21 LTS, Maven 3.9+`

## Modules

| Module | Path | Responsibility | Local setup / test guide |
|---|---|---|---|
| `backend` | `/backend` | REST API cho SaaS Web & Developer Platform, xác thực JWT/API Key, cấp S3 Presigned URL, xác nhận upload và điều phối SQS job | `docs/development-setup.md` |

## Development commands

- Install: `./mvnw dependency:resolve`
- Run: `./mvnw spring-boot:run`
- Build: `./mvnw -B clean package -DskipTests`
- Test: `./mvnw -B test`
- Verify (Unit + Integration): `./mvnw -B verify`

## External services

- **LocalStack** (`S3`, `SQS`, `DynamoDB`): Khởi chạy từ thư mục gốc bằng `make infra` và kiểm tra bằng `make smoke` (endpoint mặc định: `http://localhost:4566`).

## Agent constraints

- Allowed paths / operations: Sửa đổi trong `/backend`, cập nhật tài liệu liên quan trong `/docs/docs-backend`.
- Restricted paths / operations: Không tự ý sửa đổi `/contracts`, `/infra`, `/worker`, `/frontend` khi chưa được phê duyệt.
- Required approvals: Thay đổi trong `/contracts` (OpenAPI, DynamoDB schema, SQS message, S3 key, state machine) cần con người review và xác nhận.
