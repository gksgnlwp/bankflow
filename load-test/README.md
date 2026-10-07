# 부하 테스트 결과

## 환경

- MacBook Air (Apple Silicon, 10코어), 서버·DB·Redis·k6 모두 같은 장비
- MySQL 8.4, Redis 7.4 (`docker compose up -d`), HikariCP 커넥션 풀 기본값 10개
- k6로 30초 동안 측정. 각 측정마다 서버를 새로 띄움

## 1. 동시성 테스트 (`AccountConcurrencyTest`)

DB 비관적 락(`SELECT ... FOR UPDATE`)이 정말 필요한지 확인하기 위해, 락을 잠시 빼고 같은 테스트를 돌렸다.

| 테스트 | 락 있음 | 락 없음 |
| --- | --- | --- |
| 잔액 10만 원 계좌에서 1천 원씩 100번 동시 출금 | 0원 남음 | **91,000원 남음** (갱신 손실) |
| 잔액 1만 원 계좌에 1천 원 출금 50번 동시 요청 | 10번 성공 | **50번 모두 성공** |
| 두 계좌가 서로 100번 동시 이체 | 총액 200,000원 유지 | **총액 198,000원** (2,000원 증발) |

## 2. 부하 테스트 (`bank-load.js`)

두 종류의 트래픽을 동시에 보낸다.

- **hot**: 사용자 60명이 이벤트 계좌 하나에 입금/출금을 계속 보냄 (같은 계좌에 요청이 몰리는 상황)
- **normal**: 일반 고객 50명끼리 초당 150건 무작위 이체

### 문제: 한 계좌에 몰린 요청 때문에 다른 고객까지 느려진다

| 상황 | normal 이체 p95 |
| --- | --- |
| normal 트래픽만 | 3.95ms |
| hot 트래픽과 함께 | **72 ~ 99ms (약 18~25배)** |

부하 중 MySQL을 조회해 보니 커넥션 10개 중 **9개가 `LOCK WAIT`** 상태였다.
hot 계좌의 잠금을 기다리는 요청이 DB 커넥션을 쥔 채 기다리기 때문에,
경합이 없는 normal 이체도 남은 커넥션 1개를 두고 줄을 서야 했다.

### 개선: 잠금 대기를 DB 밖(Redis)으로 옮긴다

`bankflow.lock.strategy=redis`이면 트랜잭션 시작 전에 Redis에서 계좌 잠금을 먼저 잡는다.
잠금을 기다리는 동안에는 DB 커넥션을 쓰지 않는다. DB 락은 그대로 두어 정합성은 DB가 최종 보장한다.

| | DB 락 | Redis 락 1차 (스핀) | **Redis 락 개선 (로컬 공정 락 + Redis)** |
| --- | --- | --- | --- |
| normal 이체 p95 | 72 ~ 99ms | 7.5 ~ 8.9ms | **4.4 ~ 5.3ms** |
| normal 이체 중앙값 | 51 ~ 57ms | 3.3ms | **2.5ms** |
| hot 계좌 처리량 | 약 930건/s | 약 460건/s | 약 620건/s |
| hot 계좌 p95 | 78 ~ 89ms | 약 405ms | 126 ~ 136ms |
| 실패 / 정합성 | 0건 / OK | 0건 / OK | 0건 / OK |

(각 2회 측정 범위. 원본 수치는 `results/*.json`)

- **1차 시도**: 모든 요청이 1~5ms마다 Redis를 다시 확인(스핀)했다. normal 이체는 빨라졌지만,
  잠금이 풀린 뒤 다음 요청이 잡기까지 빈틈이 생기고 순서도 뒤섞여 hot 계좌 처리량이 절반으로 떨어졌다.
- **개선**: 같은 서버 안의 요청은 공정한 로컬 락(`ReentrantLock(true)`)에 먼저 줄을 세우고,
  맨 앞 요청만 Redis 잠금을 시도하게 했다. (Spring Integration `RedisLockRegistry`와 같은 방식)
- **트레이드오프**: 요청마다 Redis 왕복이 추가되어, hot 계좌 하나의 처리량은 DB 락보다 약 1/3 낮다.
  대신 한 계좌에 요청이 몰려도 나머지 고객의 응답 시간은 거의 영향을 받지 않는다.

## 3. 중복 요청 차단 (`idempotency.js`)

거래 500건을 각각 같은 `Idempotency-Key`로 3번씩 동시에 보냈다. (네트워크 재시도·중복 클릭 상황)

| | 중복 차단 끔 | 중복 차단 켬 |
| --- | --- | --- |
| 최종 잔액 (기대값 50,000원) | **150,000원** (3배 입금) | 50,000원 |
| 거래 기록 | 1,500건 | 500건 |
| 차단된 중복 요청 | 0건 | 1,000건 (409) |

## 다시 측정하기

```bash
docker compose up -d
./gradlew bootJar
java -jar build/libs/bankflow-0.0.1-SNAPSHOT.jar --spring.profiles.active=docker --bankflow.lock.strategy=db
k6 run load-test/bank-load.js
```

`--bankflow.lock.strategy=redis`로 바꿔 다시 실행하면 비교할 수 있다.
일반 트래픽만 재려면 `HOT_VUS=0 k6 run load-test/bank-load.js`.
중복 차단은 `--bankflow.idempotency.enabled=false/true`로 서버를 띄운 뒤 `k6 run load-test/idempotency.js`.
