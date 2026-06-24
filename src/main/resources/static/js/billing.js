/**
 * billing.js — QuickBill main billing screen JavaScript
 *
 * RESPONSIBILITIES:
 *   1. Authenticate the biller (redirect to login if no JWT).
 *   2. Load the food item dropdown and build Quick Items buttons.
 *   3. Handle the Create Bill form submission.
 *   4. Poll GET /api/biller/live-status every 2500ms to refresh all zones.
 *   5. Render: pending bills, ambiguous matches with candidate buttons, recent tokens,
 *              recent payments, status bar counts, printer indicator, live clock.
 *   6. Handle the manual resolution of AMBIGUOUS bills.
 *
 * TOKEN FROM APPLICATION.PROPERTIES:
 *   The billerToken is stored in localStorage after login (set by biller/login.html).
 *   Every API call includes: { headers: { 'Authorization': 'Bearer ' + billerToken } }
 */

'use strict';

// ── GLOBALS ───────────────────────────────────────────────────────────────────

const billerToken = localStorage.getItem('billerToken');
let pollIntervalId = null;
let foodItemsCache = []; // all food items from the dropdown API

// ── STARTUP ───────────────────────────────────────────────────────────────────

/**
 * Called when the DOM is fully loaded.
 * Redirects to login if no token, then initialises everything.
 */
document.addEventListener('DOMContentLoaded', () => {
    if (!billerToken) {
        window.location.href = '/biller/login';
        return;
    }

    // Show biller username in the top nav
    document.getElementById('navUsername').textContent =
        localStorage.getItem('billerUsername') || 'Biller';

    // Load food items for the dropdown and quick items
    loadFoodItemDropdown();

    // Start the live clock in the status bar
    startLiveClock();

    // Begin polling the live-status endpoint every 2500ms
    pollLiveStatus();
    pollIntervalId = setInterval(pollLiveStatus, 2500);
});

// ── FOOD DROPDOWN ─────────────────────────────────────────────────────────────

/**
 * Loads food items from /api/food/dropdown and populates:
 *   - The <select> dropdown on the Create Bill form
 *   - The Quick Items grid (top 8 items as one-tap buttons)
 */
async function loadFoodItemDropdown() {
    try {
        const res = await fetch('/api/food/dropdown', {
            headers: { 'Authorization': 'Bearer ' + billerToken }
        });
        if (!res.ok) return;
        foodItemsCache = await res.json();

        // Populate the <select>
        const select = document.getElementById('foodItemSelect');
        foodItemsCache.forEach(item => {
            const opt = document.createElement('option');
            opt.value = item.id;
            opt.dataset.price = item.price;
            opt.dataset.name = item.name;
            opt.textContent = `${item.name} — ₹${item.price}`;
            select.appendChild(opt);
        });

        // Build Quick Items grid (first 8 items)
        buildQuickItems(foodItemsCache.slice(0, 8));

    } catch (err) {
        console.error('Failed to load food items:', err);
    }
}

/**
 * Builds Quick Item buttons — tapping one fills the form with that item's details.
 * Input: items — array of FoodItemResponseDTO.
 */
function buildQuickItems(items) {
    const grid = document.getElementById('quickItemsGrid');
    grid.innerHTML = items.map(item => `
        <button type="button"
                class="quick-item-btn"
                onclick="applyQuickItem(${item.id}, '${escapeHtml(item.name)}', ${item.price})"
                title="${escapeHtml(item.name)} — ₹${item.price}">
            <span class="qi-name">${escapeHtml(item.name)}</span>
            <span class="qi-price">₹${item.price}</span>
        </button>
    `).join('');
}

/**
 * Fills the bill form with the selected quick item's data.
 */
function applyQuickItem(id, name, price) {
    document.getElementById('foodItemSelect').value = id;
    document.getElementById('itemName').value = name;
    document.getElementById('unitRate').value = price;
    recalcAmount();
}

/**
 * Called when the dropdown changes — auto-fills itemName and unitRate.
 */
function handleItemSelect(select) {
    const selectedOpt = select.options[select.selectedIndex];
    if (selectedOpt && selectedOpt.value) {
        document.getElementById('itemName').value = selectedOpt.dataset.name || '';
        document.getElementById('unitRate').value = selectedOpt.dataset.price || '';
        recalcAmount();
    }
}

/**
 * Recalculates and displays the total amount (unit rate × quantity).
 */
function recalcAmount() {
    const qty = parseFloat(document.getElementById('quantity').value) || 0;
    const rate = parseFloat(document.getElementById('unitRate').value) || 0;
    const total = qty * rate;
    document.getElementById('totalAmount').textContent = '₹ ' + total.toFixed(2);
}

/**
 * Sets the active payment mode button (CASH or UPI).
 */
function setPaymentMode(mode) {
    document.getElementById('paymentMode').value = mode;
    document.getElementById('btnCash').classList.toggle('active', mode === 'CASH');
    document.getElementById('btnUpi').classList.toggle('active', mode === 'UPI');
}

// ── CREATE BILL ───────────────────────────────────────────────────────────────

/**
 * Handles the Create Bill form submission.
 * Sends POST /api/biller/bills with the form data.
 * If CASH: shows a success message immediately (token generated).
 * If UPI:  shows "Waiting for payment..." and relies on polling.
 */
async function handleCreateBill(event) {
    event.preventDefault();

    const btn = document.getElementById('createBillBtn');
    const btnText = document.getElementById('createBillBtnText');
    const spinner = document.getElementById('createBillSpinner');
    const successMsg = document.getElementById('billSuccessMsg');
    const errorMsg = document.getElementById('billErrorMsg');

    // Hide previous messages
    successMsg.style.display = 'none';
    errorMsg.style.display = 'none';

    // Show loading state
    btn.disabled = true;
    btnText.style.display = 'none';
    spinner.style.display = 'inline-block';

    try {
        const foodItemId = document.getElementById('foodItemSelect').value || null;
        const body = {
            foodItemId: foodItemId ? parseInt(foodItemId) : null,
            itemName: document.getElementById('itemName').value.trim(),
            quantity: parseInt(document.getElementById('quantity').value),
            unitRate: parseFloat(document.getElementById('unitRate').value),
            paymentMode: document.getElementById('paymentMode').value
        };

        const res = await fetch('/api/biller/bills', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + billerToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        });

        const bill = await res.json();

        if (res.ok) {
            // Reset form
            document.getElementById('createBillForm').reset();
            document.getElementById('foodItemSelect').value = '';
            document.getElementById('totalAmount').textContent = '₹ 0.00';
            setPaymentMode('CASH');

            // Show success message
            if (bill.status === 'CASH_CONFIRMED' && bill.token) {
                successMsg.innerHTML = `✅ Token <strong>#${bill.token.tokenNumber}</strong> generated for ${bill.itemName}!`;
            } else if (bill.status === 'MATCHED' && bill.token) {
                successMsg.innerHTML = `✅ Payment matched! Token <strong>#${bill.token.tokenNumber}</strong> generated.`;
            } else if (bill.status === 'AMBIGUOUS') {
                successMsg.innerHTML = `⚠️ Multiple payments found. Resolve in the "Multiple Match Found" panel →`;
            } else {
                successMsg.innerHTML = `⏳ Bill created. Waiting for UPI payment (₹${bill.amount})...`;
            }
            successMsg.style.display = 'flex';
            setTimeout(() => successMsg.style.display = 'none', 5000);

        } else {
            errorMsg.textContent = bill.message || 'Error creating bill. Please try again.';
            errorMsg.style.display = 'flex';
        }

    } catch (err) {
        errorMsg.textContent = 'Network error. Please check connection.';
        errorMsg.style.display = 'flex';
    } finally {
        btn.disabled = false;
        btnText.style.display = 'inline';
        spinner.style.display = 'none';
    }
}

// ── LIVE STATUS POLLING ───────────────────────────────────────────────────────

/**
 * Polls GET /api/biller/live-status and re-renders all zones.
 * Called every 2500ms by setInterval.
 */
async function pollLiveStatus() {
    try {
        const res = await fetch('/api/biller/live-status', {
            headers: { 'Authorization': 'Bearer ' + billerToken }
        });

        if (res.status === 401 || res.status === 403) {
            // Token expired or invalid — clear token and redirect to login
            clearInterval(pollIntervalId);
            localStorage.removeItem('billerToken');
            localStorage.removeItem('billerUsername');
            window.location.href = '/biller/login';
            return;
        }

        if (!res.ok) return; // transient error — retry next interval

        const data = await res.json();

        // Re-render each zone
        renderPendingBills(data.pendingBills || []);
        renderAmbiguousBills(data.ambiguousBills || []);
        renderRecentTokens(data.recentTokens || []);
        renderRecentPayments(data.recentPayments || []);
        updateStatusBar(data);
        updatePrinterIndicator(data.printerStatus);

    } catch (err) {
        // Network error during poll — fail silently, retry next interval
        console.warn('Live status poll error:', err.message);
    }
}

// ── ZONE RENDERERS ────────────────────────────────────────────────────────────

/**
 * Renders bills in WAITING_PAYMENT status (Column 2, top zone).
 */
function renderPendingBills(bills) {
    const zone = document.getElementById('pendingBillsZone');
    const badge = document.getElementById('pendingCount');
    badge.textContent = bills.length;

    if (!bills.length) {
        zone.innerHTML = `<div class="empty-state"><span class="empty-icon">✅</span><p>All bills matched</p></div>`;
        return;
    }

    zone.innerHTML = bills.map(bill => `
        <div class="bill-card bill-waiting" id="bill-${bill.id}">
            <div class="bill-card-header">
                <span class="bill-id">#${bill.id}</span>
                <span class="bill-amount">₹${bill.amount}</span>
            </div>
            <div class="bill-item">${escapeHtml(bill.itemName)} × ${bill.quantity}</div>
            <div class="bill-meta">
                <span class="bill-time">${formatTime(bill.createdAt)}</span>
                <span class="bill-mode mode-upi">UPI</span>
            </div>
            <div class="bill-status-indicator">
                <span class="spinner-small"></span> Waiting for payment...
            </div>
        </div>
    `).join('');
}

/**
 * Renders bills in AMBIGUOUS status (Column 2, bottom zone).
 * Each bill shows candidate payment buttons for the biller to click.
 */
function renderAmbiguousBills(bills) {
    const section = document.getElementById('ambiguousSection');
    const zone = document.getElementById('ambiguousBillsZone');
    const badge = document.getElementById('ambiguousCount');
    badge.textContent = bills.length;

    if (!bills.length) {
        section.style.display = 'none';
        return;
    }

    section.style.display = 'block';
    zone.innerHTML = bills.map(bill => `
        <div class="bill-card bill-ambiguous" id="ambiguous-bill-${bill.id}">
            <div class="bill-card-header">
                <span class="bill-id">#${bill.id}</span>
                <span class="bill-amount">₹${bill.amount}</span>
            </div>
            <div class="bill-item">${escapeHtml(bill.itemName)} × ${bill.quantity}</div>
            <div class="bill-meta">
                <span class="bill-time">${formatTime(bill.createdAt)}</span>
            </div>
            <p class="candidate-label">Click the correct payment:</p>
            <div class="candidates-grid">
                ${(bill.candidatePayments || []).map(p => `
                    <button class="candidate-btn"
                            onclick="resolveMatch(${bill.id}, ${p.paymentId})"
                            title="Received at ${formatTime(p.receivedAt)}">
                        <span class="candidate-ref">…${p.last4Digits || '????'}</span>
                        <span class="candidate-time">${formatTime(p.receivedAt)}</span>
                    </button>
                `).join('')}
            </div>
        </div>
    `).join('');
}

/**
 * Renders the last 3 generated tokens (Column 3, bottom zone).
 */
function renderRecentTokens(tokens) {
    const zone = document.getElementById('recentTokensZone');

    if (!tokens.length) {
        zone.innerHTML = `<div class="empty-state"><span class="empty-icon">🎫</span><p>No tokens yet</p></div>`;
        return;
    }

    zone.innerHTML = tokens.map(token => {
        // If the printer failed, render the virtualPrintHtml if available
        if (token.status === 'PRINT_FAILED' && token.virtualPrintHtml) {
            return `<div class="token-card token-virtual">${token.virtualPrintHtml}</div>`;
        }
        return `
            <div class="token-card ${token.status === 'PRINTED' ? 'token-printed' : 'token-active'}">
                <div class="token-number-large">#${token.tokenNumber}</div>
                <div class="token-item-text">${escapeHtml(token.itemSummary || '')}</div>
                <div class="token-amount-text">₹${token.amount}</div>
                <div class="token-footer">
                    <span class="token-time">${formatTime(token.generatedAt)}</span>
                    <button class="btn-reprint" onclick="reprintToken(${token.id})">🖨 Reprint</button>
                </div>
            </div>`;
    }).join('');
}

/**
 * Renders recent incoming UPI payments (Column 3, top zone).
 */
function renderRecentPayments(payments) {
    const zone = document.getElementById('paymentsZone');

    if (!payments.length) {
        zone.innerHTML = `<div class="empty-state"><span class="empty-icon">💳</span><p>No payments yet...</p></div>`;
        return;
    }

    zone.innerHTML = payments.map(p => `
        <div class="payment-row ${p.status === 'MATCHED' ? 'payment-matched' : 'payment-unmatched'}">
            <div class="payment-row-left">
                <span class="payment-amount">₹${p.amount}</span>
                <span class="payment-time">${formatTime(p.receivedAt)}</span>
            </div>
            <div class="payment-row-right">
                <span class="payment-ref">…${p.last4Digits || '????'}</span>
                <span class="payment-status-dot ${p.status === 'MATCHED' ? 'dot-green' : 'dot-orange'}"></span>
            </div>
        </div>
    `).join('');
}

/**
 * Updates the bottom status bar counts.
 */
function updateStatusBar(data) {
    document.getElementById('totalBillsToday').textContent = data.totalBillsToday || 0;
    document.getElementById('totalPaymentsToday').textContent = data.totalPaymentsToday || 0;
    document.getElementById('matchedCount').textContent = data.matchedCount || 0;
    document.getElementById('unmatchedPaymentsCount').textContent = data.unmatchedPaymentCount || 0;
    document.getElementById('unmatchedCount').textContent = data.unmatchedPaymentCount || 0;
}

/**
 * Updates the printer status indicator in the bottom bar.
 */
function updatePrinterIndicator(status) {
    const dot = document.getElementById('printerStatusDot');
    const text = document.getElementById('printerStatusText');
    if (status === 'ONLINE') {
        dot.className = 'printer-dot dot-online';
        text.textContent = 'ONLINE';
    } else {
        dot.className = 'printer-dot dot-offline';
        text.textContent = 'OFFLINE';
    }
}

// ── MANUAL MATCH RESOLUTION ───────────────────────────────────────────────────

/**
 * Called when the biller clicks a candidate payment in the AMBIGUOUS panel.
 * Sends POST /api/biller/resolve-match with billId and chosenPaymentId.
 */
async function resolveMatch(billId, chosenPaymentId) {
    try {
        const res = await fetch('/api/biller/resolve-match', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + billerToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ billId, chosenPaymentId })
        });

        const token = await res.json();

        if (res.ok) {
            showToast(`✅ Match resolved! Token #${token.tokenNumber} generated.`);
        } else {
            showToast(`❌ Error: ${token.message || 'Could not resolve match.'}`, 'error');
        }

    } catch (err) {
        showToast('❌ Network error resolving match.', 'error');
    }
}

// ── REPRINT TOKEN ─────────────────────────────────────────────────────────────

async function reprintToken(tokenId) {
    try {
        const res = await fetch(`/api/token/${tokenId}/reprint`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + billerToken }
        });
        if (res.ok) {
            showToast('🖨 Reprint sent to printer.');
        } else {
            showToast('⚠ Printer offline. Check virtual print.', 'warning');
        }
    } catch (err) {
        showToast('❌ Error sending reprint.', 'error');
    }
}

function printVirtualToken(btn) {
    const slip = btn.closest('.virtual-token-slip');
    if (!slip) return;
    
    // Add print-target class to isolate it in CSS
    slip.classList.add('print-target');
    
    // Trigger browser print dialog
    window.print();
    
    // Remove class after printing
    slip.classList.remove('print-target');
}

// ── UTILITIES ─────────────────────────────────────────────────────────────────

function startLiveClock() {
    const el = document.getElementById('liveClockDisplay');
    function tick() {
        const now = new Date();
        el.textContent = now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }
    tick();
    setInterval(tick, 1000);
}

function formatTime(isoStr) {
    if (!isoStr) return '—';
    try {
        return new Date(isoStr).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
        return isoStr;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function showToast(message, type = 'success') {
    const toast = document.getElementById('toast') || createToastElement();
    toast.textContent = message;
    toast.className = `toast toast-${type}`;
    toast.style.display = 'block';
    setTimeout(() => toast.style.display = 'none', 4000);
}

function createToastElement() {
    const el = document.createElement('div');
    el.id = 'toast';
    el.className = 'toast';
    document.body.appendChild(el);
    return el;
}

function logoutBiller() {
    clearInterval(pollIntervalId);
    localStorage.removeItem('billerToken');
    localStorage.removeItem('billerUsername');
    window.location.href = '/biller/login';
}
