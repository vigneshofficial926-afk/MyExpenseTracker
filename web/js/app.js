/**
 * Vignesh Expense Tracker - Core Web Application Engine
 * Offline-first persistent architecture using localStorage.
 */

// 1. Storage Keys & Defaults
const STORAGE_KEYS = {
  TRANSACTIONS: 'met_transactions',
  CATEGORIES: 'met_categories',
  BUDGETS: 'met_budgets',
  ACCOUNTS: 'met_accounts',
  GOALS: 'met_savings_goals',
  RECURRING: 'met_recurring',
  SETTINGS: 'met_settings'
};

const DEFAULT_CATEGORIES = [
  // Expense
  { id: 1, name: "Food and Groceries", type: "EXPENSE", icon: "restaurant", color: "#F97316" },
  { id: 2, name: "Hotel and Restaurants", type: "EXPENSE", icon: "local_cafe", color: "#EA580C" },
  { id: 3, name: "Tea, Coffee and Snacks", type: "EXPENSE", icon: "coffee", color: "#B45309" },
  { id: 4, name: "Transportation and Fuel", type: "EXPENSE", icon: "directions_car", color: "#3B82F6" },
  { id: 5, name: "Rent", type: "EXPENSE", icon: "home", color: "#6366F1" },
  { id: 6, name: "Electricity and Utilities", type: "EXPENSE", icon: "bolt", color: "#EAB308" },
  { id: 7, name: "Mobile and Internet", type: "EXPENSE", icon: "phone_android", color: "#06B6D4" },
  { id: 8, name: "Shopping", type: "EXPENSE", icon: "shopping_bag", color: "#EC4899" },
  { id: 9, name: "Health and Medical", type: "EXPENSE", icon: "local_hospital", color: "#EF4444" },
  { id: 10, name: "Entertainment", type: "EXPENSE", icon: "movie", color: "#8B5CF6" },
  { id: 11, name: "Travel", type: "EXPENSE", icon: "flight", color: "#14B8A6" },
  { id: 12, name: "Education", type: "EXPENSE", icon: "school", color: "#10B981" },
  { id: 13, name: "EMI and Loan Payments", type: "EXPENSE", icon: "credit_card", color: "#D97706" },
  { id: 14, name: "Family Expenses", type: "EXPENSE", icon: "groups", color: "#F43F5E" },
  { id: 15, name: "Subscriptions", type: "EXPENSE", icon: "subscriptions", color: "#A855F7" },
  { id: 16, name: "Other Expenses", type: "EXPENSE", icon: "more_horiz", color: "#64748B" },
  // Income
  { id: 17, name: "Salary", type: "INCOME", icon: "account_balance_wallet", color: "#10B981" },
  { id: 18, name: "Freelance Income", type: "INCOME", icon: "laptop_mac", color: "#0EA5E9" },
  { id: 19, name: "Business Income", type: "INCOME", icon: "trending_up", color: "#8B5CF6" },
  { id: 20, name: "Interest", type: "INCOME", icon: "savings", color: "#14B8A6" },
  { id: 21, name: "Gifts", type: "INCOME", icon: "card_giftcard", color: "#EC4899" },
  { id: 22, name: "Other Income", type: "INCOME", icon: "attach_money", color: "#64748B" }
];

const DEFAULT_ACCOUNTS = [
  { id: 1, name: "Primary Bank (HDFC)", type: "BANK", openingBalance: 45000, currentBalance: 45000 },
  { id: 2, name: "Cash Wallet", type: "CASH", openingBalance: 5000, currentBalance: 5000 }
];

// Helper Functions
const formatINR = (amt) => {
  return "₹" + Math.abs(amt).toLocaleString('en-IN', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 0
  });
};

const formatCompactINR = (amt) => {
  const abs = Math.abs(amt);
  if (abs >= 10000000) return "₹" + (abs / 10000000).toFixed(1) + " Cr";
  if (abs >= 100000) return "₹" + (abs / 100000).toFixed(1) + " L";
  if (abs >= 1000) return "₹" + (abs / 1000).toFixed(1) + " k";
  return "₹" + Math.round(abs);
};

const formatDDMMYYYY = (timestamp) => {
  const d = new Date(timestamp);
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${day}-${month}-${year}`;
};

const formatFriendlyDate = (timestamp) => {
  const d = new Date(timestamp);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const txDay = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diffDays = Math.round((today - txDay) / (1000 * 60 * 60 * 24));
  
  if (diffDays === 0) return "Today";
  if (diffDays === 1) return "Yesterday";
  return formatDDMMYYYY(timestamp);
};

// Database Model Helper with in-memory caching for zero-latency reads
const db = {
  _cache: {},
  get(key, defaultValue) {
    if (this._cache[key] !== undefined) {
      return this._cache[key];
    }
    try {
      const data = localStorage.getItem(key);
      const parsed = data ? JSON.parse(data) : defaultValue;
      this._cache[key] = parsed;
      return parsed;
    } catch (e) {
      return defaultValue;
    }
  },
  set(key, value) {
    this._cache[key] = value;
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.error(e);
    }
    markAllTabsDirty();
  },
  invalidate(key) {
    if (key) delete this._cache[key];
    else this._cache = {};
  },
  init() {
    const isInitialized = localStorage.getItem('met_is_initialized');
    if (!isInitialized) {
      // First-ever launch: seed initial defaults
      this.set(STORAGE_KEYS.CATEGORIES, DEFAULT_CATEGORIES);
      this.set(STORAGE_KEYS.ACCOUNTS, DEFAULT_ACCOUNTS);
      const now = new Date();
      const currentMonthKey = `${String(now.getMonth() + 1).padStart(2, '0')}-${now.getFullYear()}`;
      this.set(STORAGE_KEYS.BUDGETS, [
        { id: 1, monthYear: currentMonthKey, categoryId: null, budgetAmount: 35000 }
      ]);
      this.set(STORAGE_KEYS.GOALS, [
        {
          id: 1,
          name: "Emergency Fund",
          targetAmount: 100000,
          savedAmount: 35000,
          targetDate: Date.now() + 180 * 24 * 60 * 60 * 1000,
          notes: "Target 6 months living expenses"
        }
      ]);
      this.set(STORAGE_KEYS.RECURRING, [
        {
          id: 1,
          title: "House Rent",
          amount: 15000,
          type: "EXPENSE",
          categoryName: "Rent",
          paymentMethod: "Bank Transfer",
          frequency: "Monthly",
          nextDueDate: Date.now() + 15 * 24 * 60 * 60 * 1000,
          isActive: true
        }
      ]);
      const sampleTxs = [
        { id: 1, amount: 65000, type: "INCOME", categoryName: "Salary", paymentMethod: "Bank Transfer", description: "Monthly Salary Credit", tags: "job", dateTime: now.getTime() - 5 * 86400000, isCreditCardBillPayment: false },
        { id: 2, amount: 15000, type: "EXPENSE", categoryName: "Rent", paymentMethod: "Bank Transfer", description: "Apartment Rent", tags: "home", dateTime: now.getTime() - 4 * 86400000, isCreditCardBillPayment: false },
        { id: 3, amount: 2450, type: "EXPENSE", categoryName: "Food and Groceries", paymentMethod: "UPI", description: "Supermarket Groceries & Fruits", tags: "groceries", dateTime: now.getTime() - 2 * 86400000, isCreditCardBillPayment: false },
        { id: 4, amount: 850, type: "EXPENSE", categoryName: "Transportation and Fuel", paymentMethod: "UPI", description: "Petrol for Bike", tags: "fuel", dateTime: now.getTime() - 1 * 86400000, isCreditCardBillPayment: false },
        { id: 5, amount: 1200, type: "EXPENSE", categoryName: "Hotel and Restaurants", paymentMethod: "Debit Card", description: "Dinner with Family", tags: "dining", dateTime: now.getTime() - 12 * 3600000, isCreditCardBillPayment: false },
        { id: 6, amount: 120, type: "EXPENSE", categoryName: "Tea, Coffee and Snacks", paymentMethod: "UPI", description: "Chai & Samosa", tags: "snacks", dateTime: now.getTime() - 3 * 3600000, isCreditCardBillPayment: false }
      ];
      this.set(STORAGE_KEYS.TRANSACTIONS, sampleTxs);
      localStorage.setItem('met_is_initialized', 'true');
    } else {
      // Subsequent visits: ensure collections are valid arrays and don't re-seed deleted data
      if (localStorage.getItem(STORAGE_KEYS.CATEGORIES) === null) {
        this.set(STORAGE_KEYS.CATEGORIES, DEFAULT_CATEGORIES);
      }
      if (localStorage.getItem(STORAGE_KEYS.ACCOUNTS) === null) {
        this.set(STORAGE_KEYS.ACCOUNTS, DEFAULT_ACCOUNTS.map(a => ({ ...a, openingBalance: 0, currentBalance: 0 })));
      }
      if (localStorage.getItem(STORAGE_KEYS.TRANSACTIONS) === null) {
        this.set(STORAGE_KEYS.TRANSACTIONS, []);
      }
      if (localStorage.getItem(STORAGE_KEYS.BUDGETS) === null) {
        this.set(STORAGE_KEYS.BUDGETS, []);
      }
      if (localStorage.getItem(STORAGE_KEYS.GOALS) === null) {
        this.set(STORAGE_KEYS.GOALS, []);
      }
      if (localStorage.getItem(STORAGE_KEYS.RECURRING) === null) {
        this.set(STORAGE_KEYS.RECURRING, []);
      }
    }
  }
};

// 2. Financial Calculation Engine (Rule 13 Implementation)
const FinancialCalculator = {
  calculateTotalBalance(accounts, transactions) {
    const openingTotal = accounts.reduce((sum, a) => sum + (a.openingBalance || 0), 0);
    let net = openingTotal;
    transactions.forEach(tx => {
      if (tx.type === 'INCOME' || tx.type === 'REFUND') {
        net += tx.amount;
      } else if (tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment) {
        net -= tx.amount;
      }
      // Internal transfers are excluded from net balance
    });
    return Math.round(net * 100) / 100;
  },

  calculateMonthlyIncome(transactions, startMs, endMs) {
    return transactions
      .filter(tx => tx.dateTime >= startMs && tx.dateTime <= endMs && tx.type === 'INCOME')
      .reduce((sum, tx) => sum + tx.amount, 0);
  },

  calculateMonthlyExpenses(transactions, startMs, endMs) {
    let expense = 0;
    let refund = 0;
    transactions.forEach(tx => {
      if (tx.dateTime >= startMs && tx.dateTime <= endMs) {
        if (tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment) {
          expense += tx.amount;
        } else if (tx.type === 'REFUND') {
          refund += tx.amount;
        }
      }
    });
    return Math.max(0, expense - refund);
  },

  isDuplicate(candidate, transactions) {
    const now = candidate.dateTime || Date.now();
    return transactions.some(tx => 
      tx.id !== candidate.id &&
      tx.amount === candidate.amount &&
      tx.categoryName === candidate.categoryName &&
      tx.type === candidate.type &&
      Math.abs(tx.dateTime - now) <= 60000
    );
  }
};

// 3. UI Controller State
let state = {
  currentTab: 'home',
  theme: 'dark',
  pinLock: false,
  pinCode: '',
  isUnlocked: true,
  txFilterPeriod: 'month',
  txFilterType: 'ALL',
  txSearchQuery: '',
  editingTxId: null,
  selectedCategory: null
};

// 4. Initial Launch & Rendering
document.addEventListener('DOMContentLoaded', () => {
  db.init();
  loadSettings();
  processDueRecurring();
  setupEventListeners();
  switchTab('home');
});

function loadSettings() {
  const settings = db.get(STORAGE_KEYS.SETTINGS, { theme: 'dark', pin: '', isPinEnabled: false });
  state.theme = settings.theme || 'dark';
  state.pinCode = settings.pin || '';
  state.pinLock = !!settings.isPinEnabled;
  state.isUnlocked = !state.pinLock || !state.pinCode;

  document.documentElement.setAttribute('data-theme', state.theme);
  if (!state.isUnlocked) {
    showPinScreen();
  }
}

function processDueRecurring() {
  const recurring = db.get(STORAGE_KEYS.RECURRING, []);
  const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
  const now = Date.now();
  let added = 0;

  recurring.forEach(rule => {
    if (rule.isActive && rule.nextDueDate <= now) {
      transactions.unshift({
        id: Date.now() + Math.random(),
        amount: rule.amount,
        type: rule.type,
        categoryName: rule.categoryName,
        paymentMethod: rule.paymentMethod,
        description: `[Recurring] ${rule.title}`,
        tags: 'recurring',
        dateTime: rule.nextDueDate,
        isCreditCardBillPayment: false
      });
      // advance schedule
      rule.nextDueDate = rule.nextDueDate + (30 * 86400000);
      added++;
    }
  });

  if (added > 0) {
    db.set(STORAGE_KEYS.TRANSACTIONS, transactions);
    db.set(STORAGE_KEYS.RECURRING, recurring);
  }
}

function showToast(msg) {
  const toast = document.getElementById('toastMsg');
  toast.innerHTML = `<span class="material-symbols-rounded">check_circle</span> ${msg}`;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 3000);
}

// 5. Views Rendering & Multi-Section State
const tabDirty = {
  home: true,
  transactions: true,
  reports: true,
  budget: true,
  settings: true
};

function markAllTabsDirty() {
  tabDirty.home = true;
  tabDirty.transactions = true;
  tabDirty.reports = true;
  tabDirty.budget = true;
  tabDirty.settings = true;
}

function renderTab(tab, container) {
  const now = new Date();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59).getTime();

  const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
  const accounts = db.get(STORAGE_KEYS.ACCOUNTS, []);
  const budgets = db.get(STORAGE_KEYS.BUDGETS, []);

  switch (tab) {
    case 'home':
      renderHome(container, transactions, accounts, budgets, startOfMonth, endOfMonth);
      break;
    case 'transactions':
      renderTransactions(container, transactions);
      break;
    case 'reports':
      renderReports(container, transactions, budgets, startOfMonth, endOfMonth);
      break;
    case 'budget':
      renderBudget(container, transactions, accounts, budgets, startOfMonth, endOfMonth);
      break;
    case 'settings':
      renderSettings(container);
      break;
  }
}

function renderApp() {
  markAllTabsDirty();
  const current = state.currentTab || 'home';
  const activePane = document.getElementById(`tab-pane-${current}`);
  if (activePane) {
    renderTab(current, activePane);
    tabDirty[current] = false;
  }
}

// HOME TAB
function renderHome(container, transactions, accounts, budgets, startOfMonth, endOfMonth) {
  const totalBalance = FinancialCalculator.calculateTotalBalance(accounts, transactions);
  const monthIncome = FinancialCalculator.calculateMonthlyIncome(transactions, startOfMonth, endOfMonth);
  const monthExpense = FinancialCalculator.calculateMonthlyExpenses(transactions, startOfMonth, endOfMonth);

  // Today & Week
  const todayStart = new Date();
  todayStart.setHours(0,0,0,0);
  const todayExpense = transactions
    .filter(tx => tx.dateTime >= todayStart.getTime() && tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment)
    .reduce((s, tx) => s + tx.amount, 0);

  const weekStart = new Date();
  weekStart.setDate(weekStart.getDate() - 7);
  const weekExpense = transactions
    .filter(tx => tx.dateTime >= weekStart.getTime() && tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment)
    .reduce((s, tx) => s + tx.amount, 0);

  // Budget alert
  const currentMonthKey = `${String(new Date().getMonth() + 1).padStart(2, '0')}-${new Date().getFullYear()}`;
  const budgetObj = budgets.find(b => b.monthYear === currentMonthKey && !b.categoryId) || { budgetAmount: 0 };
  const overallBudget = budgetObj.budgetAmount || 0;
  const budgetUtilization = overallBudget > 0 ? (monthExpense / overallBudget) * 100 : 0;
  const remainingBudget = overallBudget - monthExpense;

  let alertBannerHtml = '';
  if (overallBudget > 0 && budgetUtilization >= 100) {
    alertBannerHtml = `
      <div class="alert-banner danger">
        <span class="material-symbols-rounded">warning</span>
        <span>Warning: You have reached 100% of your monthly budget!</span>
      </div>`;
  } else if (overallBudget > 0 && budgetUtilization >= 80) {
    alertBannerHtml = `
      <div class="alert-banner warning">
        <span class="material-symbols-rounded">info</span>
        <span>Heads up: You have utilized ${Math.round(budgetUtilization)}% of your monthly budget.</span>
      </div>`;
  }

  // Category Breakdown Data
  const catTotals = {};
  transactions
    .filter(tx => tx.dateTime >= startOfMonth && tx.dateTime <= endOfMonth && tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment)
    .forEach(tx => {
      catTotals[tx.categoryName] = (catTotals[tx.categoryName] || 0) + tx.amount;
    });

  const catSlices = Object.entries(catTotals).map(([cat, amt]) => ({
    label: cat,
    value: amt
  })).sort((a,b) => b.value - a.value);

  // Recent 5 transactions
  const recentTxsHtml = transactions.slice(0, 5).map(tx => renderTxItemHtml(tx)).join('');

  container.innerHTML = `
    ${alertBannerHtml}
    
    <!-- Balance Hero Card -->
    <div class="card balance-hero-card">
      <div class="balance-header">
        <span>Total Net Balance</span>
        <span class="material-symbols-rounded" style="cursor:pointer;" onclick="toggleBalanceEye()">visibility</span>
      </div>
      <div class="balance-amount" id="balanceAmountDisplay">${formatINR(totalBalance)}</div>
      <div class="balance-stats-row">
        <div class="stat-item">
          <div class="stat-icon-wrap"><span class="material-symbols-rounded" style="color:#6EE7B7;font-size:18px;">arrow_downward</span></div>
          <div class="stat-info">
            <div class="stat-label">Month Income</div>
            <div class="stat-val">${formatINR(monthIncome)}</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon-wrap"><span class="material-symbols-rounded" style="color:#FCA5A5;font-size:18px;">arrow_upward</span></div>
          <div class="stat-info">
            <div class="stat-label">Month Expense</div>
            <div class="stat-val">${formatINR(monthExpense)}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Action Add Buttons -->
    <div class="action-buttons-row">
      <button class="btn-action expense" onclick="openAddModal('EXPENSE')">
        <span class="material-symbols-rounded">remove_circle</span>
        Add Expense
      </button>
      <button class="btn-action income" onclick="openAddModal('INCOME')">
        <span class="material-symbols-rounded">add_circle</span>
        Add Income
      </button>
    </div>

    <!-- Mini Metrics Grid -->
    <div class="mini-metrics-grid">
      <div class="mini-card">
        <div class="mini-card-head">
          <span>Today's Expense</span>
          <span class="material-symbols-rounded" style="font-size:16px;">calendar_today</span>
        </div>
        <div class="mini-card-val">${formatINR(todayExpense)}</div>
      </div>
      <div class="mini-card">
        <div class="mini-card-head">
          <span>This Week</span>
          <span class="material-symbols-rounded" style="font-size:16px;">date_range</span>
        </div>
        <div class="mini-card-val">${formatINR(weekExpense)}</div>
      </div>
    </div>

    <!-- Monthly Budget Card -->
    ${overallBudget > 0 ? `
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
        <span style="font-weight:700;font-size:14px;">Monthly Budget</span>
        <span style="font-weight:700;font-size:13px;color:${remainingBudget >= 0 ? 'var(--income)' : 'var(--expense)'}">
          Left: ${formatINR(remainingBudget)}
        </span>
      </div>
      <div class="progress-container">
        <div class="progress-bar-fill ${budgetUtilization >= 100 ? 'danger' : (budgetUtilization >= 80 ? 'warning' : 'safe')}" 
             style="width:${Math.min(100, budgetUtilization)}%"></div>
      </div>
      <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--text-secondary);margin-top:4px;">
        <span>Spent: ${formatINR(monthExpense)}</span>
        <span>Limit: ${formatINR(overallBudget)} (${Math.round(budgetUtilization)}%)</span>
      </div>
    </div>
    ` : ''}

    <!-- Category Breakdown Chart -->
    ${catSlices.length > 0 ? `
    <div class="card">
      <div style="font-weight:700;font-size:15px;margin-bottom:12px;">Expense Breakdown</div>
      <div style="display:flex;justify-content:center;margin-bottom:12px;">
        <canvas id="donutChartCanvas" width="180" height="180"></canvas>
      </div>
      <div id="donutLegend" style="display:flex;flex-direction:column;gap:6px;"></div>
    </div>
    ` : ''}

    <!-- Recent Transactions -->
    <div style="display:flex;justify-content:space-between;align-items:center;margin:16px 0 8px 0;">
      <div class="section-label" style="margin-bottom:0;">Recent Transactions</div>
      <a href="#" style="font-size:12px;font-weight:700;color:var(--primary);text-decoration:none;" onclick="switchTab('transactions');return false;">View All</a>
    </div>
    <div class="tx-list">
      ${recentTxsHtml || '<div style="text-align:center;padding:24px;color:var(--text-secondary);">No transactions yet. Tap "Add Expense" to start!</div>'}
    </div>
  `;

  if (catSlices.length > 0) {
    drawDonutChart('donutChartCanvas', catSlices, monthExpense);
  }
}

// TRANSACTIONS TAB
function renderTransactions(container, transactions) {
  // Apply filtering
  let filtered = [...transactions];
  const now = new Date();
  
  if (state.txFilterPeriod === 'today') {
    const t = new Date(); t.setHours(0,0,0,0);
    filtered = filtered.filter(tx => tx.dateTime >= t.getTime());
  } else if (state.txFilterPeriod === 'week') {
    const w = new Date(); w.setDate(w.getDate() - 7);
    filtered = filtered.filter(tx => tx.dateTime >= w.getTime());
  } else if (state.txFilterPeriod === 'month') {
    const m = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
    filtered = filtered.filter(tx => tx.dateTime >= m);
  }

  if (state.txFilterType !== 'ALL') {
    filtered = filtered.filter(tx => tx.type === state.txFilterType);
  }

  if (state.txSearchQuery.trim()) {
    const q = state.txSearchQuery.toLowerCase();
    filtered = filtered.filter(tx => 
      (tx.description && tx.description.toLowerCase().includes(q)) ||
      (tx.categoryName && tx.categoryName.toLowerCase().includes(q)) ||
      (tx.tags && tx.tags.toLowerCase().includes(q))
    );
  }

  // Group by day
  const groups = {};
  filtered.forEach(tx => {
    const dayKey = formatFriendlyDate(tx.dateTime);
    if (!groups[dayKey]) groups[dayKey] = { items: [], totalExpense: 0 };
    groups[dayKey].items.push(tx);
    if (tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment) {
      groups[dayKey].totalExpense += tx.amount;
    }
  });

  const groupsHtml = Object.entries(groups).map(([dayKey, group]) => {
    return `
      <div style="display:flex;justify-content:space-between;align-items:center;padding:12px 2px 6px 2px;">
        <span style="font-size:12px;font-weight:700;color:var(--text-secondary);">${dayKey}</span>
        ${group.totalExpense > 0 ? `<span style="font-size:11px;font-weight:700;color:var(--expense);">Day: -${formatINR(group.totalExpense)}</span>` : ''}
      </div>
      <div class="tx-list">
        ${group.items.map(tx => renderTxItemHtml(tx)).join('')}
      </div>
    `;
  }).join('');

  container.innerHTML = `
    <!-- Search Bar -->
    <div style="position:relative;margin-bottom:12px;">
      <input type="text" class="input-field" placeholder="Search by note, category, or tag..." 
             value="${state.txSearchQuery}" oninput="searchTransactions(this.value)">
    </div>

    <!-- Time Filter Chips -->
    <div class="filter-chips-row" style="margin-bottom:8px;">
      <button class="filter-chip-btn ${state.txFilterPeriod === 'all' ? 'active' : ''}" onclick="filterPeriod('all')">All Time</button>
      <button class="filter-chip-btn ${state.txFilterPeriod === 'today' ? 'active' : ''}" onclick="filterPeriod('today')">Today</button>
      <button class="filter-chip-btn ${state.txFilterPeriod === 'week' ? 'active' : ''}" onclick="filterPeriod('week')">This Week</button>
      <button class="filter-chip-btn ${state.txFilterPeriod === 'month' ? 'active' : ''}" onclick="filterPeriod('month')">This Month</button>
    </div>

    <!-- Type Filters & CSV Export -->
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <div style="display:flex;gap:6px;">
        <button class="filter-chip-btn ${state.txFilterType === 'ALL' ? 'active' : ''}" onclick="filterType('ALL')">All</button>
        <button class="filter-chip-btn ${state.txFilterType === 'EXPENSE' ? 'active' : ''}" onclick="filterType('EXPENSE')">Expenses</button>
        <button class="filter-chip-btn ${state.txFilterType === 'INCOME' ? 'active' : ''}" onclick="filterType('INCOME')">Income</button>
      </div>
      <button class="filter-chip-btn" onclick="exportCsv()" title="Download CSV">
        <span class="material-symbols-rounded" style="font-size:16px;">upload_file</span>
        CSV
      </button>
    </div>

    ${groupsHtml || '<div style="text-align:center;padding:40px;color:var(--text-secondary);">No transactions match your search.</div>'}
  `;
}

// REPORTS TAB
function renderReports(container, transactions, budgets, startOfMonth, endOfMonth) {
  const monthIncome = FinancialCalculator.calculateMonthlyIncome(transactions, startOfMonth, endOfMonth);
  const monthExpense = FinancialCalculator.calculateMonthlyExpenses(transactions, startOfMonth, endOfMonth);
  const netSavings = monthIncome - monthExpense;

  // Weekly Mon-Sun Data
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const weekVals = [0, 0, 0, 0, 0, 0, 0];
  const now = new Date();
  const currentDayOfWeek = (now.getDay() + 6) % 7; // 0 for Mon
  
  const monday = new Date(now);
  monday.setDate(monday.getDate() - currentDayOfWeek);
  monday.setHours(0,0,0,0);

  transactions.forEach(tx => {
    if (tx.dateTime >= monday.getTime() && tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment) {
      const txDate = new Date(tx.dateTime);
      const dayIdx = (txDate.getDay() + 6) % 7;
      weekVals[dayIdx] += tx.amount;
    }
  });

  // Category breakdown
  const catMap = {};
  transactions
    .filter(tx => tx.dateTime >= startOfMonth && tx.dateTime <= endOfMonth && tx.type === 'EXPENSE' && !tx.isCreditCardBillPayment)
    .forEach(tx => {
      catMap[tx.categoryName] = (catMap[tx.categoryName] || 0) + tx.amount;
    });

  const catList = Object.entries(catMap).sort((a,b) => b[1] - a[1]);

  container.innerHTML = `
    <!-- Net Savings Card -->
    <div class="card">
      <div style="font-weight:700;font-size:15px;margin-bottom:12px;">Monthly Financial Health</div>
      <div style="display:flex;justify-content:space-between;margin-bottom:8px;">
        <span style="font-size:13px;color:var(--text-secondary);">Income: <b style="color:var(--income)">${formatINR(monthIncome)}</b></span>
        <span style="font-size:13px;color:var(--text-secondary);">Expense: <b style="color:var(--expense)">${formatINR(monthExpense)}</b></span>
      </div>
      <div class="progress-container">
        <div class="progress-bar-fill safe" style="width:${monthIncome > 0 ? Math.min(100, (monthExpense / monthIncome) * 100) : 0}%"></div>
      </div>
      <div style="background:var(--surface-variant);padding:10px 14px;border-radius:var(--radius-md);display:flex;justify-content:space-between;margin-top:12px;">
        <span style="font-size:13px;font-weight:600;">Net Savings This Month:</span>
        <span style="font-size:14px;font-weight:800;color:${netSavings >= 0 ? 'var(--income)' : 'var(--expense)'}">
          ${formatINR(netSavings)}
        </span>
      </div>
    </div>

    <!-- Weekly Expenses Bar Chart -->
    <div class="card">
      <div style="font-weight:700;font-size:15px;margin-bottom:12px;">Daily Spending This Week</div>
      <canvas id="weeklyChartCanvas" width="360" height="150" style="width:100%;height:150px;"></canvas>
    </div>

    <!-- Top 5 Categories Leaderboard -->
    <div class="card">
      <div style="font-weight:700;font-size:15px;margin-bottom:12px;">Top Spending Categories</div>
      <div style="display:flex;flex-direction:column;gap:10px;">
        ${catList.slice(0, 5).map(([name, amt], idx) => `
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div style="display:flex;align-items:center;gap:10px;">
              <div style="width:24px;height:24px;border-radius:50%;background:var(--primary-container);color:var(--primary);display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;">
                ${idx + 1}
              </div>
              <span style="font-size:13px;font-weight:600;">${name}</span>
            </div>
            <span style="font-size:13px;font-weight:700;">${formatINR(amt)}</span>
          </div>
        `).join('') || '<div style="color:var(--text-secondary);font-size:12px;">No expenses recorded this month.</div>'}
      </div>
    </div>
  `;

  drawWeeklyBarChart('weeklyChartCanvas', days, weekVals, currentDayOfWeek);
}

// BUDGET TAB
function renderBudget(container, transactions, accounts, budgets, startOfMonth, endOfMonth) {
  const currentMonthKey = `${String(new Date().getMonth() + 1).padStart(2, '0')}-${new Date().getFullYear()}`;
  const overallObj = budgets.find(b => b.monthYear === currentMonthKey && !b.categoryId) || { budgetAmount: 0 };
  const overallLimit = overallObj.budgetAmount || 0;
  
  const monthExpense = FinancialCalculator.calculateMonthlyExpenses(transactions, startOfMonth, endOfMonth);
  const remainingBudget = overallLimit - monthExpense;
  const actualBankBalance = FinancialCalculator.calculateTotalBalance(accounts, transactions);

  const categories = db.get(STORAGE_KEYS.CATEGORIES, []).filter(c => c.type === 'EXPENSE');

  container.innerHTML = `
    <!-- Budget vs Bank Balance Distinction Card (Rule 5 & 13) -->
    <div class="card">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
        <div>
          <div style="font-size:11px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;">Budget Remaining</div>
          <div style="font-size:20px;font-weight:800;color:${remainingBudget >= 0 ? 'var(--income)' : 'var(--expense)'};margin:4px 0;">
            ${formatINR(remainingBudget)}
          </div>
          <div style="font-size:10px;color:var(--text-muted);">Monthly Spending Cap</div>
        </div>
        <div style="border-left:1px solid var(--border-color);padding-left:14px;">
          <div style="font-size:11px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;">Actual Bank Balance</div>
          <div style="font-size:20px;font-weight:800;color:var(--primary);margin:4px 0;">
            ${formatINR(actualBankBalance)}
          </div>
          <div style="font-size:10px;color:var(--text-muted);">Real Liquid Worth</div>
        </div>
      </div>
    </div>

    <!-- Overall Budget Meter -->
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
        <span style="font-weight:700;font-size:15px;">Monthly Budget</span>
        <button class="chip-btn" onclick="openBudgetModal(null, ${overallLimit})">Edit Limit</button>
      </div>
      <div class="progress-container" style="height:12px;">
        <div class="progress-bar-fill ${monthExpense > overallLimit ? 'danger' : 'safe'}" 
             style="width:${overallLimit > 0 ? Math.min(100, (monthExpense / overallLimit) * 100) : 0}%"></div>
      </div>
      <div style="display:flex;justify-content:space-between;font-size:12px;margin-top:6px;font-weight:600;">
        <span>Spent: ${formatINR(monthExpense)}</span>
        <span>Limit: ${formatINR(overallLimit)}</span>
      </div>
    </div>

    <!-- Category Budgets -->
    <div style="font-size:12px;font-weight:700;color:var(--text-secondary);margin:16px 0 8px 0;text-transform:uppercase;">
      Category Budgets
    </div>
    <div style="display:flex;flex-direction:column;gap:8px;">
      ${categories.slice(0, 8).map(cat => {
        const catBudget = (budgets.find(b => b.monthYear === currentMonthKey && b.categoryId === cat.id) || {}).budgetAmount || 0;
        const catSpent = transactions
          .filter(tx => tx.dateTime >= startOfMonth && tx.dateTime <= endOfMonth && tx.categoryName === cat.name && tx.type === 'EXPENSE')
          .reduce((s, tx) => s + tx.amount, 0);

        return `
          <div class="card" style="padding:12px 14px;margin-bottom:0;cursor:pointer;" onclick="openBudgetModal(${cat.id}, ${catBudget}, '${cat.name}')">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-size:13px;font-weight:700;">${cat.name}</span>
              <span style="font-size:13px;font-weight:700;">${catBudget > 0 ? `${formatINR(catSpent)} / ${formatINR(catBudget)}` : '<span style="color:var(--primary);font-size:11px;">+ Set Budget</span>'}</span>
            </div>
            ${catBudget > 0 ? `
              <div class="progress-container" style="margin-top:6px;">
                <div class="progress-bar-fill ${catSpent > catBudget ? 'danger' : 'safe'}" 
                     style="width:${Math.min(100, (catSpent / catBudget) * 100)}%"></div>
              </div>
            ` : ''}
          </div>
        `;
      }).join('')}
    </div>
  `;
}

// SETTINGS TAB
function renderSettings(container) {
  container.innerHTML = `
    <!-- Offline Notice Card -->
    <div class="card" style="background:var(--primary-container);border-color:rgba(13,148,136,0.3);">
      <div style="display:flex;gap:12px;align-items:center;">
        <span class="material-symbols-rounded" style="color:var(--primary);font-size:28px;">security</span>
        <div>
          <div style="font-size:14px;font-weight:800;color:var(--primary);">100% Offline & Private</div>
          <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
            Your personal financial records never leave this device. Zero tracking or cloud transmission.
          </div>
        </div>
      </div>
    </div>

    <!-- Theme & Preferences -->
    <div class="section-label">Preferences</div>
    <div class="card" style="margin-bottom:8px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--primary)">dark_mode</span>
          <span style="font-size:14px;font-weight:600;">Theme Mode</span>
        </div>
        <button class="chip-btn" onclick="toggleTheme()">${state.theme === 'dark' ? '🌙 Dark' : '☀️ Light'}</button>
      </div>
    </div>

    <!-- App Security PIN -->
    <div class="section-label" style="margin-top:16px;">Security Lock</div>
    <div class="card" style="margin-bottom:8px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--primary)">lock</span>
          <div>
            <div style="font-size:14px;font-weight:600;">4-Digit PIN Lock</div>
            <div style="font-size:11px;color:var(--text-secondary);">${state.pinLock ? 'PIN Active' : 'Not Configured'}</div>
          </div>
        </div>
        <button class="chip-btn" onclick="promptSetPin()">${state.pinLock ? 'Change PIN' : 'Set PIN'}</button>
      </div>
    </div>

    <!-- Data Backup & Export -->
    <div class="section-label" style="margin-top:16px;">Backup & Export</div>
    <div class="card" style="margin-bottom:8px;cursor:pointer;" onclick="exportCsv()">
      <div style="display:flex;align-items:center;gap:10px;">
        <span class="material-symbols-rounded" style="color:var(--primary)">upload_file</span>
        <div>
          <div style="font-size:14px;font-weight:600;">Export Transactions to CSV</div>
          <div style="font-size:11px;color:var(--text-secondary);">Spreadsheet compatible file</div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-bottom:8px;cursor:pointer;" onclick="generateMonthlySummaryReport()">
      <div style="display:flex;align-items:center;gap:10px;">
        <span class="material-symbols-rounded" style="color:var(--primary)">description</span>
        <div>
          <div style="font-size:14px;font-weight:600;">Download Monthly Summary Report</div>
          <div style="font-size:11px;color:var(--text-secondary);">Formatted text breakdown</div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-bottom:8px;cursor:pointer;" onclick="downloadJsonBackup()">
      <div style="display:flex;align-items:center;gap:10px;">
        <span class="material-symbols-rounded" style="color:var(--primary)">download</span>
        <div>
          <div style="font-size:14px;font-weight:600;">Database Backup (JSON)</div>
          <div style="font-size:11px;color:var(--text-secondary);">Complete offline database snapshot</div>
        </div>
      </div>
    </div>

    <!-- Danger Zone -->
    <div class="section-label" style="margin-top:16px;color:var(--expense);">Danger Zone</div>
    <div class="card" id="btnClearAllDataCard" style="background:var(--expense-light);border-color:rgba(239,68,68,0.25);cursor:pointer;" onclick="openClearDataModal()">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;">
        <div style="display:flex;align-items:center;gap:12px;">
          <div style="width:36px;height:36px;border-radius:50%;background:rgba(239,68,68,0.15);color:var(--expense);display:flex;align-items:center;justify-content:center;flex-shrink:0;">
            <span class="material-symbols-rounded">delete_forever</span>
          </div>
          <div>
            <div style="font-size:14px;font-weight:700;color:var(--expense);">Clear All App Data</div>
            <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">Permanently erase transactions, budgets, goals & reset accounts</div>
          </div>
        </div>
        <button type="button" class="chip-btn" id="btnClearAllData" style="background:var(--expense);color:#ffffff;border:none;font-weight:700;flex-shrink:0;" onclick="event.stopPropagation();openClearDataModal();">Clear</button>
      </div>
    </div>
  `;
}

// 6. Action Handlers
function toggleBalanceEye() {
  const el = document.getElementById('balanceAmountDisplay');
  if (el.textContent.includes('•')) {
    const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
    const accounts = db.get(STORAGE_KEYS.ACCOUNTS, []);
    el.textContent = formatINR(FinancialCalculator.calculateTotalBalance(accounts, transactions));
  } else {
    el.textContent = '₹ ••••••';
  }
}

function renderTransactionsTab() {
  const pane = document.getElementById('tab-pane-transactions');
  if (pane) {
    const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
    renderTransactions(pane, transactions);
    tabDirty.transactions = false;
  }
}

function filterPeriod(p) {
  state.txFilterPeriod = p;
  renderTransactionsTab();
}

function filterType(t) {
  state.txFilterType = t;
  renderTransactionsTab();
}

function searchTransactions(q) {
  state.txSearchQuery = q;
  renderTransactionsTab();
}

function toggleTheme() {
  state.theme = state.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', state.theme);
  const settings = db.get(STORAGE_KEYS.SETTINGS, {});
  settings.theme = state.theme;
  db.set(STORAGE_KEYS.SETTINGS, settings);
  markAllTabsDirty();
  const current = state.currentTab || 'home';
  const activePane = document.getElementById(`tab-pane-${current}`);
  if (activePane) {
    renderTab(current, activePane);
    tabDirty[current] = false;
  }
}

function switchTab(tab) {
  if (!tab) return;
  state.currentTab = tab;

  // 1. Instant tab button active feedback (0ms)
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.tab === tab);
  });

  // 2. Instant Top Bar Title update
  const titleMap = {
    home: 'Vignesh Expense Tracker',
    transactions: 'Transactions',
    reports: 'Reports & Analytics',
    budget: 'Monthly Budget',
    settings: 'Settings'
  };
  const topTitle = document.getElementById('topBarTitle');
  if (topTitle) topTitle.textContent = titleMap[tab] || 'Vignesh Expense Tracker';

  // 3. Instant FAB visibility
  const fab = document.getElementById('mainFab');
  if (fab) {
    fab.style.display = (tab === 'home' || tab === 'transactions') ? 'flex' : 'none';
  }

  // 4. Instant multi-section visibility toggle (0ms latency, zero layout shifts)
  const tabs = ['home', 'transactions', 'reports', 'budget', 'settings'];
  tabs.forEach(t => {
    const pane = document.getElementById(`tab-pane-${t}`);
    if (pane) {
      pane.style.display = (t === tab) ? 'block' : 'none';
    }
  });

  // 5. Lazy render only if content is dirty or empty
  const activePane = document.getElementById(`tab-pane-${tab}`);
  if (activePane && (tabDirty[tab] || !activePane.innerHTML.trim())) {
    renderTab(tab, activePane);
    tabDirty[tab] = false;
  }
}

// 7. Modals: Add / Edit Transaction
function openAddModal(defaultType = 'EXPENSE', txId = null) {
  state.editingTxId = txId;
  const modal = document.getElementById('addTxModal');
  const categories = db.get(STORAGE_KEYS.CATEGORIES, []);

  let existing = null;
  if (txId) {
    existing = db.get(STORAGE_KEYS.TRANSACTIONS, []).find(t => t.id === txId);
  }

  const type = existing ? existing.type : defaultType;
  const filteredCats = categories.filter(c => c.type === type);
  const selectedCat = existing ? existing.categoryName : (filteredCats[0] ? filteredCats[0].name : '');

  document.getElementById('modalSheetTitle').textContent = txId ? 'Edit Transaction' : 'Quick Add Transaction';
  document.getElementById('txAmountInput').value = existing ? existing.amount : '';
  document.getElementById('txDescInput').value = existing ? existing.description : '';
  document.getElementById('txTagsInput').value = existing ? existing.tags : '';
  document.getElementById('txCcBillCheckbox').checked = existing ? !!existing.isCreditCardBillPayment : false;

  const moreOptionsEl = document.getElementById('txMoreOptions');
  if (moreOptionsEl) {
    moreOptionsEl.open = existing ? (!!existing.tags || !!existing.isCreditCardBillPayment) : false;
  }
  
  // Date time defaults
  const d = existing ? new Date(existing.dateTime) : new Date();
  const dateStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  document.getElementById('txDateInput').value = dateStr;

  // Render type segments (Expense / Income / Transfer)
  document.querySelectorAll('.segment-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.type === type);
    b.onclick = () => {
      document.querySelectorAll('.segment-btn').forEach(btn => btn.classList.remove('active'));
      b.classList.add('active');
      const catsForType = categories.filter(c => c.type === b.dataset.type);
      renderCategoryChipsInModal(catsForType, null);
    };
  });

  renderCategoryChipsInModal(filteredCats, selectedCat);
  modal.classList.add('active');
}

function renderCategoryChipsInModal(categories, selected) {
  const container = document.getElementById('modalCatChips');
  if (!container) return;

  // Deduplicate categories by name to ensure no duplicates
  const seen = new Set();
  const uniqueCats = categories.filter(c => {
    if (!c || !c.name) return false;
    const key = c.name.trim().toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  state.selectedCategory = selected || (uniqueCats[0] ? uniqueCats[0].name : 'Other');
  
  container.innerHTML = uniqueCats.map(cat => {
    const isSel = state.selectedCategory === cat.name;
    const catColor = cat.color || 'var(--primary)';
    return `
      <button type="button" class="category-chip-select ${isSel ? 'selected' : ''}" 
              data-cat-name="${cat.name}"
              onclick="selectModalCategory('${cat.name.replace(/'/g, "\\'")}')">
        <span class="material-symbols-rounded" style="font-size:15px;color:${isSel ? '#fff' : catColor};">${cat.icon || 'label'}</span>
        <span>${cat.name}</span>
      </button>
    `;
  }).join('');
}

function selectModalCategory(name) {
  state.selectedCategory = name;
  document.querySelectorAll('.category-chip-select').forEach(el => {
    const isMatch = el.getAttribute('data-cat-name') === name;
    el.classList.toggle('selected', isMatch);
  });
}

function closeModal(modalId) {
  document.getElementById(modalId).classList.remove('active');
}

function saveTransactionFromModal() {
  const amountStr = document.getElementById('txAmountInput').value;
  const amount = parseFloat(amountStr);
  if (!amount || amount <= 0) {
    alert('Please enter a valid amount in ₹.');
    return;
  }

  const activeSegment = document.querySelector('.segment-btn.active');
  const type = activeSegment ? activeSegment.dataset.type : 'EXPENSE';
  const desc = document.getElementById('txDescInput').value.trim();
  const tags = document.getElementById('txTagsInput').value.trim();
  const isCcBill = document.getElementById('txCcBillCheckbox').checked;
  const dateVal = document.getElementById('txDateInput').value;
  const dateTime = dateVal ? new Date(dateVal).getTime() : Date.now();

  const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
  let existing = null;
  if (state.editingTxId) {
    existing = transactions.find(t => t.id === state.editingTxId);
  }

  // Payment method removed from Quick Add (+) menu; defaults cleanly to UPI or existing value
  const pmEl = document.getElementById('txPaymentMethod');
  const paymentMethod = pmEl ? pmEl.value : (existing ? (existing.paymentMethod || 'UPI') : 'UPI');

  const candidate = {
    id: state.editingTxId || (Date.now() + Math.random()),
    amount: amount,
    type: type,
    categoryName: state.selectedCategory || (type === 'INCOME' ? 'Salary' : 'Other Expenses'),
    paymentMethod: paymentMethod,
    description: desc,
    tags: tags,
    dateTime: dateTime,
    isCreditCardBillPayment: isCcBill
  };

  // Duplicate Check (Rule 13)
  if (!state.editingTxId && FinancialCalculator.isDuplicate(candidate, transactions)) {
    if (!confirm('A transaction with identical details was recorded less than a minute ago. Save anyway?')) {
      return;
    }
  }

  if (state.editingTxId) {
    const idx = transactions.findIndex(t => t.id === state.editingTxId);
    if (idx !== -1) transactions[idx] = candidate;
  } else {
    transactions.unshift(candidate);
  }

  db.set(STORAGE_KEYS.TRANSACTIONS, transactions);
  closeModal('addTxModal');
  showToast(state.editingTxId ? 'Transaction updated' : 'Transaction saved');
  renderApp();
}

function deleteTransaction(txId) {
  if (confirm('Are you sure you want to delete this transaction?')) {
    let transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
    transactions = transactions.filter(t => t.id !== txId);
    db.set(STORAGE_KEYS.TRANSACTIONS, transactions);
    showToast('Transaction deleted');
    renderApp();
  }
}

// 8. Canvas Charts
function drawDonutChart(canvasId, slices, total) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const width = canvas.width;
  const height = canvas.height;
  const centerX = width / 2;
  const centerY = height / 2;
  const radius = Math.min(centerX, centerY) - 15;
  const innerRadius = radius - 28;

  ctx.clearRect(0, 0, width, height);

  const colors = ['#0D9488', '#F97316', '#3B82F6', '#8B5CF6', '#EC4899', '#EAB308', '#EF4444', '#14B8A6'];
  let startAngle = -0.5 * Math.PI;

  slices.slice(0, 6).forEach((slice, idx) => {
    const sweep = (slice.value / (total || 1)) * 2 * Math.PI;
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, startAngle, startAngle + sweep);
    ctx.arc(centerX, centerY, innerRadius, startAngle + sweep, startAngle, true);
    ctx.closePath();
    ctx.fillStyle = colors[idx % colors.length];
    ctx.fill();
    startAngle += sweep;
  });

  // Center text
  ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-primary').trim();
  ctx.font = 'bold 15px Plus Jakarta Sans, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(formatCompactINR(total), centerX, centerY);

  // Legend
  const legendEl = document.getElementById('donutLegend');
  if (legendEl) {
    legendEl.innerHTML = slices.slice(0, 5).map((slice, idx) => {
      const pct = Math.round((slice.value / (total || 1)) * 100);
      return `
        <div style="display:flex;justify-content:space-between;align-items:center;font-size:12px;">
          <div style="display:flex;align-items:center;gap:8px;">
            <div style="width:10px;height:10px;border-radius:50%;background:${colors[idx % colors.length]};"></div>
            <span>${slice.label}</span>
          </div>
          <span><b>${formatINR(slice.value)}</b> (${pct}%)</span>
        </div>
      `;
    }).join('');
  }
}

function drawWeeklyBarChart(canvasId, days, values, highlightIdx) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const width = canvas.width;
  const height = canvas.height;
  ctx.clearRect(0, 0, width, height);

  const maxVal = Math.max(...values, 100);
  const barWidth = 24;
  const spacing = width / 7;

  days.forEach((day, idx) => {
    const val = values[idx];
    const barHeight = Math.max(6, (val / maxVal) * (height - 40));
    const x = idx * spacing + (spacing - barWidth) / 2;
    const y = height - 24 - barHeight;

    // Bar
    ctx.fillStyle = (idx === highlightIdx) ? '#0284C7' : '#0D9488';
    ctx.beginPath();
    ctx.roundRect(x, y, barWidth, barHeight, [4, 4, 0, 0]);
    ctx.fill();

    // Day Label
    ctx.fillStyle = (idx === highlightIdx) ? '#0284C7' : '#94A3B8';
    ctx.font = (idx === highlightIdx ? 'bold ' : '') + '11px Plus Jakarta Sans, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(day, x + barWidth / 2, height - 6);

    // Value on top if > 0
    if (val > 0) {
      ctx.fillStyle = '#94A3B8';
      ctx.font = '10px Plus Jakarta Sans, sans-serif';
      ctx.fillText(formatCompactINR(val), x + barWidth / 2, y - 4);
    }
  });
}

// 9. Export & Backup Handlers
function exportCsv() {
  const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
  let csv = 'ID,Date,Type,Category,Amount,Payment Method,Description,Tags\n';
  transactions.forEach(t => {
    const dateStr = formatDDMMYYYY(t.dateTime);
    csv += `"${t.id}","${dateStr}","${t.type}","${t.categoryName}",${t.amount},"${t.paymentMethod}","${(t.description||'').replace(/"/g, '""')}","${(t.tags||'').replace(/"/g, '""')}"\n`;
  });

  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `transactions_${Date.now()}.csv`;
  a.click();
  showToast('Transactions CSV downloaded!');
}

function generateMonthlySummaryReport() {
  const transactions = db.get(STORAGE_KEYS.TRANSACTIONS, []);
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59).getTime();

  const monthTxs = transactions.filter(t => t.dateTime >= start && t.dateTime <= end);
  const inc = monthTxs.filter(t => t.type === 'INCOME').reduce((s,t) => s + t.amount, 0);
  const exp = monthTxs.filter(t => t.type === 'EXPENSE' && !t.isCreditCardBillPayment).reduce((s,t) => s + t.amount, 0);

  let report = `======================================\n`;
  report += `  VIGNESH EXPENSE TRACKER - MONTHLY REPORT \n`;
  report += `======================================\n`;
  report += `Generated: ${new Date().toLocaleString()}\n\n`;
  report += `Total Income  : ${formatINR(inc)}\n`;
  report += `Total Expense : ${formatINR(exp)}\n`;
  report += `Net Savings   : ${formatINR(inc - exp)}\n\n`;
  report += `--- Top Spending Breakdown ---\n`;

  const catMap = {};
  monthTxs.filter(t => t.type === 'EXPENSE').forEach(t => {
    catMap[t.categoryName] = (catMap[t.categoryName] || 0) + t.amount;
  });

  Object.entries(catMap).sort((a,b) => b[1] - a[1]).forEach(([name, amt]) => {
    report += `• ${name.padEnd(25, ' ')}: ${formatINR(amt)}\n`;
  });

  const blob = new Blob([report], { type: 'text/plain' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `monthly_report_${now.getMonth()+1}_${now.getFullYear()}.txt`;
  a.click();
  showToast('Monthly Summary Report downloaded!');
}

function downloadJsonBackup() {
  const backup = {
    exportDate: new Date().toISOString(),
    transactions: db.get(STORAGE_KEYS.TRANSACTIONS, []),
    categories: db.get(STORAGE_KEYS.CATEGORIES, []),
    budgets: db.get(STORAGE_KEYS.BUDGETS, []),
    accounts: db.get(STORAGE_KEYS.ACCOUNTS, []),
    goals: db.get(STORAGE_KEYS.GOALS, []),
    recurring: db.get(STORAGE_KEYS.RECURRING, [])
  };

  const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `expense_tracker_backup_${Date.now()}.json`;
  a.click();
  showToast('Database backup downloaded!');
}

function confirmResetData() {
  openClearDataModal();
}

function openClearDataModal() {
  const modal = document.getElementById('clearDataConfirmModal');
  const btn = document.getElementById('confirmClearDataBtn');
  const cancelBtn = document.getElementById('cancelClearDataBtn');
  const spinner = document.getElementById('clearDataSpinner');
  const icon = document.getElementById('clearDataBtnIcon');
  const text = document.getElementById('clearDataBtnText');
  const errAlert = document.getElementById('clearDataErrorAlert');

  if (errAlert) errAlert.style.display = 'none';
  if (btn) btn.disabled = false;
  if (cancelBtn) cancelBtn.disabled = false;
  if (spinner) spinner.style.display = 'none';
  if (icon) icon.style.display = 'inline-block';
  if (text) text.textContent = 'Permanently Delete Everything';

  if (modal) modal.classList.add('active');
}

function executeClearAllData() {
  const btn = document.getElementById('confirmClearDataBtn');
  const cancelBtn = document.getElementById('cancelClearDataBtn');
  const spinner = document.getElementById('clearDataSpinner');
  const icon = document.getElementById('clearDataBtnIcon');
  const text = document.getElementById('clearDataBtnText');
  const errAlert = document.getElementById('clearDataErrorAlert');

  // Disable buttons and show loading state
  if (btn) btn.disabled = true;
  if (cancelBtn) cancelBtn.disabled = true;
  if (spinner) spinner.style.display = 'inline-block';
  if (icon) icon.style.display = 'none';
  if (text) text.textContent = 'Deleting all application data...';
  if (errAlert) errAlert.style.display = 'none';

  setTimeout(() => {
    try {
      // 1. Invalidate and clear all in-memory cache
      db.invalidate();

      // 2. Permanently clear all user financial records from localStorage
      localStorage.setItem(STORAGE_KEYS.TRANSACTIONS, JSON.stringify([]));
      localStorage.setItem(STORAGE_KEYS.BUDGETS, JSON.stringify([]));
      localStorage.setItem(STORAGE_KEYS.GOALS, JSON.stringify([]));
      localStorage.setItem(STORAGE_KEYS.RECURRING, JSON.stringify([]));

      // Reset categories to standard defaults (purging any user custom categories)
      localStorage.setItem(STORAGE_KEYS.CATEGORIES, JSON.stringify(DEFAULT_CATEGORIES));

      // Reset all accounts to ₹0 opening & current balances
      const zeroAccounts = DEFAULT_ACCOUNTS.map(a => ({
        ...a,
        openingBalance: 0,
        currentBalance: 0
      }));
      localStorage.setItem(STORAGE_KEYS.ACCOUNTS, JSON.stringify(zeroAccounts));

      // Mark as initialized so db.init will not re-seed mock data on future page reloads
      localStorage.setItem('met_is_initialized', 'true');

      // Invalidate memory cache again to ensure fresh reads
      db.invalidate();

      // 3. Mark all views as dirty and force immediate re-render of ALL tab panes
      markAllTabsDirty();
      const allTabs = ['home', 'transactions', 'reports', 'budget', 'settings'];
      allTabs.forEach(tabName => {
        const pane = document.getElementById(`tab-pane-${tabName}`);
        if (pane) {
          renderTab(tabName, pane);
          tabDirty[tabName] = false;
        }
      });

      // 4. Reset balance display directly in DOM
      const balEl = document.getElementById('balanceAmountDisplay');
      if (balEl) {
        balEl.textContent = formatINR(0);
      }

      // Close modal
      closeModal('clearDataConfirmModal');

      // 5. Display success notification
      showToast('All application data has been permanently cleared.');
    } catch (err) {
      console.error('Failed to clear application data:', err);
      if (errAlert) {
        errAlert.textContent = 'Failed to delete data: ' + (err.message || 'Unknown error') + '. Please try again.';
        errAlert.style.display = 'block';
      }
      if (btn) btn.disabled = false;
      if (cancelBtn) cancelBtn.disabled = false;
      if (spinner) spinner.style.display = 'none';
      if (icon) icon.style.display = 'inline-block';
      if (text) text.textContent = 'Retry Permanent Deletion';
    }
  }, 250);
}

// 10. Security PIN
let enteredPin = '';
function promptSetPin() {
  const pin = prompt('Enter a 4-digit PIN for device security lock (leave empty to disable):');
  if (pin === null) return;
  const settings = db.get(STORAGE_KEYS.SETTINGS, {});
  if (pin.length === 4 && /^\d+$/.test(pin)) {
    settings.pin = pin;
    settings.isPinEnabled = true;
    db.set(STORAGE_KEYS.SETTINGS, settings);
    state.pinLock = true;
    state.pinCode = pin;
    showToast('PIN Lock enabled successfully!');
  } else if (pin.length === 0) {
    settings.pin = '';
    settings.isPinEnabled = false;
    db.set(STORAGE_KEYS.SETTINGS, settings);
    state.pinLock = false;
    state.pinCode = '';
    showToast('PIN Lock disabled');
  } else {
    alert('PIN must be exactly 4 numeric digits.');
  }
  renderApp();
}

function showPinScreen() {
  const screen = document.getElementById('pinLockScreen');
  screen.style.display = 'flex';
  enteredPin = '';
  updatePinDots();
}

function pressPinKey(num) {
  if (enteredPin.length < 4) {
    enteredPin += num;
    updatePinDots();
    if (enteredPin.length === 4) {
      if (enteredPin === state.pinCode) {
        document.getElementById('pinLockScreen').style.display = 'none';
        state.isUnlocked = true;
      } else {
        alert('Incorrect PIN. Try again.');
        enteredPin = '';
        updatePinDots();
      }
    }
  }
}

function deletePinKey() {
  enteredPin = enteredPin.slice(0, -1);
  updatePinDots();
}

function updatePinDots() {
  for (let i = 0; i < 4; i++) {
    const dot = document.getElementById(`pinDot${i}`);
    if (dot) dot.classList.toggle('filled', i < enteredPin.length);
  }
}

// HTML helper for single transaction row
function renderTxItemHtml(tx) {
  const isInc = tx.type === 'INCOME' || tx.type === 'REFUND';
  const icon = isInc ? 'arrow_downward' : 'arrow_upward';
  const prefix = isInc ? '+' : '-';
  const typeClass = tx.type.toLowerCase();

  return `
    <div class="tx-item" onclick="openAddModal('${tx.type}', ${tx.id})">
      <div class="tx-icon-bubble" style="background:${isInc ? 'var(--income-light)' : 'var(--expense-light)'};color:${isInc ? 'var(--income)' : 'var(--expense)'}">
        <span class="material-symbols-rounded">${icon}</span>
      </div>
      <div class="tx-details">
        <div class="tx-title">${tx.description || tx.categoryName}</div>
        <div class="tx-meta">
          <span>${tx.categoryName}</span>
          <span>•</span>
          <span class="badge-tag">${tx.paymentMethod}</span>
          ${tx.isCreditCardBillPayment ? '<span class="badge-tag" style="color:var(--primary)">CC Settle</span>' : ''}
        </div>
      </div>
      <div class="tx-amount ${typeClass}">
        ${prefix}${formatINR(tx.amount)}
        <div style="font-size:10px;font-weight:500;color:var(--text-secondary);">${formatFriendlyDate(tx.dateTime)}</div>
      </div>
    </div>
  `;
}

function setupEventListeners() {
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      switchTab(btn.dataset.tab);
    });
  });
}
