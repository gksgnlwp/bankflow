// BankFlow 부하 테스트
// 실행: k6 run load-test/bank-load.js  (서버: docker 프로필로 localhost:8080)
//
// 두 종류의 트래픽을 동시에 보낸다.
//  - hot   : 이벤트 계좌 하나에 입금/출금이 몰리는 상황 (같은 행 잠금 경합)
//  - normal: 일반 고객들끼리 무작위 이체 (경합이 거의 없음)
// hot 트래픽이 DB 커넥션을 붙잡고 있으면 normal 트래픽까지 느려지는지 보는 것이 핵심이다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const HOT_VUS = Number(__ENV.HOT_VUS || 60);
const NORMAL_RATE = Number(__ENV.NORMAL_RATE || 150);
const DURATION = __ENV.DURATION || '30s';
const NORMAL_ACCOUNTS = 50;
const INITIAL_BALANCE = 1_000_000;

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const scenarios = {
    hot: {
      executor: 'constant-vus',
      vus: HOT_VUS,
      duration: DURATION,
      exec: 'hotAccount',
    },
    normal: {
      executor: 'constant-arrival-rate',
      rate: NORMAL_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 300,
      exec: 'normalTransfer',
    },
};

// HOT_VUS=0이면 일반 트래픽만 보내 기준값을 잰다
if (HOT_VUS === 0) {
  delete scenarios.hot;
}

export const options = {
  scenarios,
  thresholds: {
    // 시나리오별 지표가 요약에 따로 나오도록 기준을 건다
    ...(HOT_VUS > 0 && {
      'http_req_duration{scenario:hot}': ['p(95)<5000'],
      'http_req_failed{scenario:hot}': ['rate<0.5'],
    }),
    'http_req_duration{scenario:normal}': ['p(95)<5000'],
    'http_req_failed{scenario:normal}': ['rate<0.05'],
  },
};

function post(path, body, params = JSON_HEADERS) {
  return http.post(`${BASE}${path}`, JSON.stringify(body), params);
}

function openFundedAccount(name) {
  const email = `${name}-${Date.now()}-${Math.random().toString(36).slice(2)}@load.test`;
  const user = post('/api/users', { email, name }).json();
  const account = post('/api/accounts', { userId: user.id }).json();
  post(`/api/accounts/${account.accountNumber}/deposit`, { amount: INITIAL_BALANCE });
  return account.accountNumber;
}

export function setup() {
  const hot = openFundedAccount('hot');
  const normals = [];
  for (let i = 0; i < NORMAL_ACCOUNTS; i++) {
    normals.push(openFundedAccount(`user${i}`));
  }
  return { hot, normals };
}

export function hotAccount(data) {
  // 입금과 출금을 번갈아 보내 잔액은 크게 변하지 않게 한다
  const action = __ITER % 2 === 0 ? 'deposit' : 'withdraw';
  const res = post(`/api/accounts/${data.hot}/${action}`, { amount: 100 });
  check(res, { 'hot 200': (r) => r.status === 200 });
}

export function normalTransfer(data) {
  const n = data.normals.length;
  const from = Math.floor(Math.random() * n);
  const to = (from + 1 + Math.floor(Math.random() * (n - 1))) % n;
  const res = post('/api/accounts/transfer', {
    fromAccountNumber: data.normals[from],
    toAccountNumber: data.normals[to],
    amount: 10,
  });
  check(res, { 'normal 200': (r) => r.status === 200 });
}

export function teardown(data) {
  // 일반 계좌끼리는 이체만 했으므로 총액은 그대로여야 한다
  const total = data.normals
    .map((number) => http.get(`${BASE}/api/accounts/${number}`).json('balance'))
    .reduce((a, b) => a + b, 0);
  const expected = INITIAL_BALANCE * data.normals.length;
  console.log(`[정합성] 일반 계좌 총액 ${total} / 기대값 ${expected} → ${total === expected ? 'OK' : 'MISMATCH'}`);
}
