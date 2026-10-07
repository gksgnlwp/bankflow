# BankFlow

Spring Boot 기반의 간단한 은행 계좌 서비스입니다. 고객 등록, 계좌 개설, 입금·출금·이체, 거래 내역 조회를 웹 화면과 REST API로 제공합니다.

## 실행

### MySQL 없이 바로 실행 (H2 인메모리)

```bash
./gradlew bootRun --args='--spring.profiles.active=h2'
```

http://localhost:8080 에 접속하면 화면을 볼 수 있습니다. 서버를 끄면 데이터는 사라집니다.

### MySQL로 실행

`bankflow` 데이터베이스를 만든 뒤 계정 정보를 환경변수로 넘깁니다. 테이블은 `ddl-auto: update`로 자동 생성됩니다.

```bash
DB_USERNAME=root DB_PASSWORD=비밀번호 ./gradlew bootRun
```

### 테스트

```bash
./gradlew test
```

테스트는 H2 인메모리 DB(`src/test/resources/application.yml`)로 실행됩니다.

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/users` | 고객 등록 `{ "email", "name" }` |
| GET | `/api/users` | 고객 목록 |
| GET | `/api/users/{userId}` | 고객 조회 |
| GET | `/api/users/{userId}/accounts` | 고객의 계좌 목록 |
| POST | `/api/accounts` | 계좌 개설 `{ "userId" }` |
| GET | `/api/accounts/{accountNumber}` | 계좌 조회 |
| POST | `/api/accounts/{accountNumber}/deposit` | 입금 `{ "amount" }` |
| POST | `/api/accounts/{accountNumber}/withdraw` | 출금 `{ "amount" }` |
| POST | `/api/accounts/transfer` | 이체 `{ "fromAccountNumber", "toAccountNumber", "amount" }` |
| GET | `/api/accounts/{accountNumber}/transactions` | 거래 내역 (최신순) |

오류는 `{ "message": "..." }` 형식으로 응답합니다. (400 잘못된 요청·잔액 부족, 404 없는 고객·계좌, 409 이메일 중복)

## 구조

```
user/          고객 (User)
account/       계좌 (Account) 및 입출금·이체 로직
transaction/   거래 내역 (Transaction)
global/        공통 예외 처리
resources/static/  웹 화면 (index.html, app.css, app.js)
```

- 잔액 변경은 비관적 락(`SELECT ... FOR UPDATE`)으로 동시 요청 시 갱신 손실을 막습니다.
- 이체는 두 계좌를 계좌번호 순서대로 잠가 교착 상태를 피합니다.
