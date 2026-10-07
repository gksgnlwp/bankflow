const state = {
  users: [],
  accounts: [],
  userId: null,
  accountNumber: null,
  tab: 'deposit',
};

const $ = (id) => document.getElementById(id);

const won = (n) => `${Number(n).toLocaleString('ko-KR')}원`;

const formatDate = (iso) => {
  const d = new Date(iso);
  const pad = (v) => String(v).padStart(2, '0');
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const TYPE_LABEL = {
  DEPOSIT: '입금',
  WITHDRAW: '출금',
  TRANSFER_OUT: '이체 보냄',
  TRANSFER_IN: '이체 받음',
};

const TAB_LABEL = { deposit: '입금하기', withdraw: '출금하기', transfer: '이체하기' };

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(data?.message || `요청에 실패했습니다. (${res.status})`);
  }
  return data;
}

let toastTimer;
function toast(message, isError = false) {
  const el = $('toast');
  el.textContent = message;
  el.classList.toggle('error', isError);
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
}

function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  Object.assign(node, props);
  for (const child of [].concat(children)) {
    node.append(child);
  }
  return node;
}

/* ---------- 고객 ---------- */

async function loadUsers() {
  state.users = await api('/api/users');
  renderUsers();
}

function renderUsers() {
  const list = $('user-list');
  list.replaceChildren(
    ...state.users.map((user) => {
      const btn = el('button', { type: 'button', className: user.id === state.userId ? 'active' : '' }, [
        user.name,
        el('span', { className: 'email', textContent: user.email }),
      ]);
      btn.addEventListener('click', () => selectUser(user.id));
      return el('li', {}, btn);
    }),
  );
  $('user-empty').classList.toggle('hidden', state.users.length > 0);
}

async function selectUser(userId) {
  state.userId = userId;
  state.accountNumber = null;
  const user = state.users.find((u) => u.id === userId);

  $('welcome').classList.add('hidden');
  $('user-view').classList.remove('hidden');
  $('user-name').textContent = `${user.name}님`;
  $('user-email').textContent = user.email;

  renderUsers();
  await loadAccounts();
}

$('user-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const submit = form.querySelector('button[type=submit]');
  submit.disabled = true;
  try {
    const user = await api('/api/users', {
      method: 'POST',
      body: { name: form.name.value.trim(), email: form.email.value.trim() },
    });
    form.reset();
    toast(`${user.name}님이 등록되었습니다.`);
    await loadUsers();
    await selectUser(user.id);
  } catch (err) {
    toast(err.message, true);
  } finally {
    submit.disabled = false;
  }
});

/* ---------- 계좌 ---------- */

async function loadAccounts() {
  state.accounts = await api(`/api/users/${state.userId}/accounts`);
  renderAccounts();
  if (state.accountNumber) {
    await loadDetail();
  } else {
    $('account-detail').classList.add('hidden');
  }
}

function renderAccounts() {
  const grid = $('account-list');
  grid.replaceChildren(
    ...state.accounts.map((account, i) => {
      const card = el('button', {
        type: 'button',
        className: `account-card${account.accountNumber === state.accountNumber ? ' active' : ''}`,
      }, [
        el('span', { className: 'muted small', textContent: `입출금 통장 ${i + 1}` }),
        el('span', { className: 'mono small', textContent: account.accountNumber }),
        el('span', { className: 'amount', textContent: won(account.balance) }),
      ]);
      card.addEventListener('click', () => selectAccount(account.accountNumber));
      return card;
    }),
  );
  $('account-empty').classList.toggle('hidden', state.accounts.length > 0);
}

async function selectAccount(accountNumber) {
  state.accountNumber = accountNumber;
  renderAccounts();
  await loadDetail();
}

$('open-account').addEventListener('click', async (e) => {
  const btn = e.currentTarget;
  btn.disabled = true;
  try {
    const account = await api('/api/accounts', { method: 'POST', body: { userId: state.userId } });
    toast(`계좌가 개설되었습니다. (${account.accountNumber})`);
    state.accountNumber = account.accountNumber;
    await loadAccounts();
  } catch (err) {
    toast(err.message, true);
  } finally {
    btn.disabled = false;
  }
});

/* ---------- 계좌 상세 / 거래 ---------- */

async function loadDetail() {
  const number = state.accountNumber;
  const [account, transactions] = await Promise.all([
    api(`/api/accounts/${number}`),
    api(`/api/accounts/${number}/transactions`),
  ]);

  $('account-detail').classList.remove('hidden');
  $('detail-number').textContent = account.accountNumber;
  $('detail-balance').textContent = won(account.balance);

  $('account-options').replaceChildren(
    ...state.accounts
      .filter((a) => a.accountNumber !== number)
      .map((a) => el('option', { value: a.accountNumber, label: `내 계좌 · ${won(a.balance)}` })),
  );

  renderHistory(transactions);
}

function renderHistory(transactions) {
  $('history-body').replaceChildren(
    ...transactions.map((t) => {
      const incoming = t.type === 'DEPOSIT' || t.type === 'TRANSFER_IN';
      return el('tr', {}, [
        el('td', { className: 'muted', textContent: formatDate(t.createdAt) }),
        el('td', {}, el('span', { className: 'badge', textContent: TYPE_LABEL[t.type] })),
        el('td', { className: 'mono small', textContent: t.counterpartyAccountNumber ?? '-' }),
        el('td', { className: `num ${incoming ? 'plus' : 'minus'}`, textContent: `${incoming ? '+' : '-'}${won(t.amount)}` }),
        el('td', { className: 'num', textContent: won(t.balanceAfter) }),
      ]);
    }),
  );
  $('history-empty').classList.toggle('hidden', transactions.length > 0);
  document.querySelector('.history').classList.toggle('hidden', transactions.length === 0);
}

document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    state.tab = tab.dataset.tab;
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t === tab));
    const isTransfer = state.tab === 'transfer';
    $('to-field').classList.toggle('hidden', !isTransfer);
    $('action-form').to.required = isTransfer;
    $('action-submit').textContent = TAB_LABEL[state.tab];
  });
});

document.querySelectorAll('.quick button').forEach((btn) => {
  btn.addEventListener('click', () => {
    const input = $('action-form').amount;
    input.value = (Number(input.value) || 0) + Number(btn.dataset.add);
  });
});

$('action-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const amount = Number(form.amount.value);
  const number = state.accountNumber;
  const submit = $('action-submit');
  submit.disabled = true;

  try {
    if (state.tab === 'deposit') {
      await api(`/api/accounts/${number}/deposit`, { method: 'POST', body: { amount } });
      toast(`${won(amount)}을 입금했습니다.`);
    } else if (state.tab === 'withdraw') {
      await api(`/api/accounts/${number}/withdraw`, { method: 'POST', body: { amount } });
      toast(`${won(amount)}을 출금했습니다.`);
    } else {
      const to = form.to.value.trim();
      if (!confirm(`${to} 계좌로 ${won(amount)}을 이체할까요?`)) return;
      await api('/api/accounts/transfer', {
        method: 'POST',
        body: { fromAccountNumber: number, toAccountNumber: to, amount },
      });
      toast(`${to} 계좌로 ${won(amount)}을 이체했습니다.`);
    }
    form.reset();
    await loadAccounts();
  } catch (err) {
    toast(err.message, true);
  } finally {
    submit.disabled = false;
  }
});

loadUsers().catch((err) => toast(err.message, true));
