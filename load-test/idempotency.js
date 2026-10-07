// 중복 요청 차단(Idempotency-Key) 검증
// 실행: k6 run load-test/idempotency.js
//
// 거래 500건을 각각 같은 키로 3번씩 동시에 보낸다(네트워크 재시도·중복 클릭 상황).
// 중복 차단이 켜져 있으면 잔액은 500 × 100원 = 50,000원이어야 한다. 꺼져 있으면 3배가 된다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const DEALS = 500;
const DUPLICATES = 3;
const AMOUNT = 100;

const applied = new Counter('deposit_applied');
const rejected = new Counter('duplicate_rejected');

export const options = {
  scenarios: {
    retries: { executor: 'shared-iterations', vus: 20, iterations: DEALS },
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function setup() {
  const json = { headers: { 'Content-Type': 'application/json' } };
  const email = `idem-${Date.now()}@load.test`;
  const user = http.post(`${BASE}/api/users`, JSON.stringify({ email, name: 'idem' }), json).json();
  const account = http.post(`${BASE}/api/accounts`, JSON.stringify({ userId: user.id }), json).json();
  return { number: account.accountNumber };
}

export default function (data) {
  const params = {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuid() },
  };
  const request = ['POST', `${BASE}/api/accounts/${data.number}/deposit`, JSON.stringify({ amount: AMOUNT }), params];

  const responses = http.batch(Array.from({ length: DUPLICATES }, () => request));

  for (const res of responses) {
    if (res.status === 200) applied.add(1);
    if (res.status === 409) rejected.add(1);
  }
  check(responses, { '최소 한 번은 성공': (rs) => rs.some((r) => r.status === 200) });
}

export function teardown(data) {
  const balance = http.get(`${BASE}/api/accounts/${data.number}`).json('balance');
  const transactions = http.get(`${BASE}/api/accounts/${data.number}/transactions`).json().length;
  const expected = DEALS * AMOUNT;
  console.log(`[중복 차단] 잔액 ${balance}원 / 기대값 ${expected}원, 거래 기록 ${transactions}건 → ${balance === expected ? 'OK' : 'MISMATCH'}`);
}
