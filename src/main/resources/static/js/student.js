/**
 * student.js — Campus Bite student portal JavaScript
 *
 * RESPONSIBILITIES:
 *   1. Authenticate the student (redirect to login if no JWT in localStorage).
 *   2. Load and render the food menu from /api/food/menu.
 *   3. Manage the shopping cart (add, remove, update quantities).
 *   4. Handle the checkout flow (create order → open Razorpay modal → verify payment → show token).
 *   5. Manage the My Orders page separately.
 *
 * JWT STORAGE:
 *   The student JWT is stored in localStorage as 'studentToken'.
 *   It is set by the student login page after extracting it from the OAuth2 redirect URL.
 *   Every API call includes: { headers: { 'Authorization': 'Bearer ' + studentToken } }
 */

'use strict';

// ── GLOBALS ───────────────────────────────────────────────────────────────────

let studentToken = localStorage.getItem('studentToken');
const savedCart = localStorage.getItem('studentCart');
let cart = savedCart ? JSON.parse(savedCart) : []; // Array of { item: FoodItemDTO, quantity: number }
let menuItemsAll = []; // All items loaded from API
let myFavorites = []; // IDs of favorited items
let currentCategory = 'ALL';
let razorpayKeyId = ''; // Loaded from backend settings or hardcoded (will be replaced at demo time)

// ── STARTUP ───────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    // 1. Handle JWT from URL (set by OAuth2SuccessHandler on first login)
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');
    if (tokenFromUrl) {
        localStorage.setItem('studentToken', tokenFromUrl);
        studentToken = tokenFromUrl; // update our script variable
        const name = urlParams.get('name') || '';
        localStorage.setItem('studentName', decodeURIComponent(name));
        // Clean the URL without reloading
        window.history.replaceState({}, document.title, '/student/home');
    }

    // 2. Check if we have a token (either from URL just now, or from previous login)
    if (!studentToken) {
        window.location.href = '/student/login';
        return;
    }

    // Show greeting
    const studentName = localStorage.getItem('studentName') || '';
    const greetingEl = document.getElementById('studentGreeting');
    if (greetingEl) {
        greetingEl.textContent = studentName ? `Hi, ${studentName.split(' ')[0]} 👋` : '';
    }

    // Update cart UI on load
    updateCartUI();

    // Load the menu
    loadMenu();
});

// ── MENU ──────────────────────────────────────────────────────────────────────

/**
 * Loads all food items from the public /api/food/menu endpoint.
 * No JWT needed — students can browse before they log in.
 */
async function loadMenu() {
    const grid = document.getElementById('menuGrid');
    try {
        const res = await fetch('/api/food/menu');
        if (!res.ok) throw new Error('Menu load failed');
        menuItemsAll = await res.json();

        if (studentToken) {
            try {
                const favRes = await fetch('/api/student/favorites', { headers: { 'Authorization': 'Bearer ' + studentToken } });
                if (favRes.ok) {
                    const favs = await favRes.json();
                    myFavorites = favs.map(f => f.id);
                }
            } catch (e) { console.warn('Failed to load favorites', e); }
        }

        renderMenu(menuItemsAll);
    } catch (err) {
        if (grid) {
            grid.innerHTML = '<div class="error-state">Could not load menu. Please refresh.</div>';
        }
        console.error('Menu load error:', err);
    }
}

/**
 * Renders the menu grid filtered by the current category.
 * Input: items — array of FoodItemResponseDTO
 */
function renderMenu(items) {
    const grid = document.getElementById('menuGrid');
    if (!grid) return;

    const filtered = currentCategory === 'ALL'
        ? items
        : items.filter(i => i.category === currentCategory);

    if (!filtered.length) {
        grid.innerHTML = '<div class="empty-category">No items in this category.</div>';
        return;
    }

    grid.innerHTML = filtered.map(item => buildMenuCard(item)).join('');
}

/**
 * Builds a single menu item card HTML.
 */
function buildMenuCard(item) {
    const outOfStock = (item.quantityAvailable !== null && item.quantityAvailable <= 0);
    const vegDot = item.isVeg
        ? '<span class="veg-dot veg-dot-green" title="Vegetarian">🟢</span>'
        : '<span class="veg-dot veg-dot-red" title="Non-Vegetarian">🔴</span>';

    const imgSrc = item.imageUrl || '/images/placeholder-food.jpg';

    const isFav = myFavorites.includes(item.id);
    const favIcon = isFav ? '❤️' : '🤍';

    return `
        <div class="menu-card ${outOfStock ? 'out-of-stock' : ''}" id="menu-item-${item.id}">
            <div class="menu-card-img-wrapper">
                <img src="${escapeHtml(imgSrc)}" alt="${escapeHtml(item.name)}"
                     class="menu-card-img" loading="lazy"
                     onerror="this.src='/images/placeholder-food.jpg'">
                ${outOfStock ? '<div class="sold-out-overlay">SOLD OUT</div>' : ''}
            </div>
            <div class="menu-card-body">
                <div class="menu-card-header-row">
                    ${vegDot}
                    <h3 class="menu-item-name">${escapeHtml(item.name)}</h3>
                    <button class="favorite-toggle-btn" onclick="toggleFavorite(${item.id})" id="fav-btn-${item.id}" style="background:none; border:none; cursor:pointer; font-size:1.2rem; margin-left:auto;">${favIcon}</button>
                </div>
                <p class="menu-item-desc">${escapeHtml(item.description || '')}</p>
                <div class="menu-card-footer">
                    <span class="menu-item-price">₹${item.price?.toFixed ? item.price.toFixed(2) : item.price}</span>
                    <button class="btn-add-cart"
                            onclick="addToCart(${item.id})"
                            ${outOfStock ? 'disabled' : ''}
                            id="add-btn-${item.id}">
                        ${outOfStock ? 'Unavailable' : '+ Add'}
                    </button>
                </div>
            </div>
        </div>
    `;
}

/**
 * Filters the menu by category when the student clicks a category button.
 */
function filterCategory(category, btn) {
    currentCategory = category;
    document.querySelectorAll('.cat-btn').forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');
    renderMenu(menuItemsAll);
}

async function toggleFavorite(itemId) {
    if (!studentToken) {
        showToast('Please login to favorite items', 'error');
        return;
    }
    const btn = document.getElementById(`fav-btn-${itemId}`);
    const isFav = myFavorites.includes(itemId);
    
    try {
        if (isFav) {
            await fetch('/api/student/favorites/' + itemId, { method: 'DELETE', headers: { 'Authorization': 'Bearer ' + studentToken } });
            myFavorites = myFavorites.filter(id => id !== itemId);
            if (btn) btn.textContent = '🤍';
            showToast('Removed from favorites');
        } else {
            await fetch('/api/student/favorites/' + itemId, { method: 'POST', headers: { 'Authorization': 'Bearer ' + studentToken } });
            myFavorites.push(itemId);
            if (btn) btn.textContent = '❤️';
            showToast('Added to favorites');
        }
    } catch (e) {
        showToast('Failed to update favorite', 'error');
    }
}

// ── CART MANAGEMENT ───────────────────────────────────────────────────────────

/**
 * Adds a food item to the cart, or increments quantity if already present.
 */
function addToCart(itemId) {
    const item = menuItemsAll.find(i => i.id === itemId);
    if (!item) return;

    const existing = cart.find(c => c.item.id === itemId);
    if (existing) {
        existing.quantity++;
    } else {
        cart.push({ item, quantity: 1 });
    }

    localStorage.setItem('studentCart', JSON.stringify(cart));
    updateCartUI();
    showToast(`${item.name} added to cart 🛒`);

    // Flash the Add button
    const btn = document.getElementById(`add-btn-${itemId}`);
    if (btn) {
        btn.textContent = 'Added ✓';
        btn.classList.add('btn-added');
        setTimeout(() => {
            btn.textContent = '+ Add';
            btn.classList.remove('btn-added');
        }, 1000);
    }
}

/**
 * Removes an item from the cart by itemId.
 */
function removeFromCart(itemId) {
    cart = cart.filter(c => c.item.id !== itemId);
    localStorage.setItem('studentCart', JSON.stringify(cart));
    updateCartUI();
}

/**
 * Updates the cart sidebar UI to reflect the current cart state.
 */
function updateCartUI() {
    const cartItemsEl = document.getElementById('cartItems');
    const cartFooter = document.getElementById('cartFooter');
    const cartCount = document.getElementById('cartCount');
    const cartTotal = document.getElementById('cartTotal');

    const totalQty = cart.reduce((sum, c) => sum + c.quantity, 0);
    const totalAmount = cart.reduce((sum, c) => sum + (c.item.price * c.quantity), 0);

    if (cartCount) cartCount.textContent = totalQty;
    if (cartTotal) cartTotal.textContent = '₹' + totalAmount.toFixed(2);

    if (!cartItemsEl) return;

    if (!cart.length) {
        cartItemsEl.innerHTML = `
            <div class="empty-cart">
                <span class="empty-cart-icon">🛒</span>
                <p>Your cart is empty</p>
            </div>`;
        if (cartFooter) cartFooter.style.display = 'none';
        return;
    }

    cartItemsEl.innerHTML = cart.map(c => `
        <div class="cart-item-row">
            <div class="cart-item-details">
                <span class="cart-item-name">${escapeHtml(c.item.name)}</span>
                <span class="cart-item-price">₹${(c.item.price * c.quantity).toFixed(2)}</span>
            </div>
            <div class="cart-item-controls">
                <button class="qty-btn" onclick="changeQty(${c.item.id}, -1)">−</button>
                <span class="qty-display">${c.quantity}</span>
                <button class="qty-btn" onclick="changeQty(${c.item.id}, 1)">+</button>
                <button class="remove-btn" onclick="removeFromCart(${c.item.id})">🗑</button>
            </div>
        </div>
    `).join('');

    if (cartFooter) cartFooter.style.display = 'block';
}

/**
 * Changes the quantity of a cart item by delta (+1 or -1).
 */
function changeQty(itemId, delta) {
    const entry = cart.find(c => c.item.id === itemId);
    if (!entry) return;
    entry.quantity = Math.max(1, entry.quantity + delta);
    localStorage.setItem('studentCart', JSON.stringify(cart));
    updateCartUI();
}

/**
 * Toggles the cart sidebar open/closed.
 */
function toggleCart() {
    window.location.href = '/student/checkout';
}

// ── CHECKOUT FLOW ─────────────────────────────────────────────────────────────

/**
 * Opens the checkout confirmation modal.
 */
function proceedToCheckout() {
    if (!cart.length) return;

    const totalAmount = cart.reduce((sum, c) => sum + (c.item.price * c.quantity), 0);
    const modal = document.getElementById('checkoutModal');
    const summary = document.getElementById('orderSummary');
    const modalTotal = document.getElementById('modalTotal');

    if (summary) {
        summary.innerHTML = cart.map(c => `
            <div class="summary-row">
                <span>${escapeHtml(c.item.name)} × ${c.quantity}</span>
                <span>₹${(c.item.price * c.quantity).toFixed(2)}</span>
            </div>
        `).join('');
    }

    if (modalTotal) modalTotal.textContent = '₹' + totalAmount.toFixed(2);
    if (modal) modal.style.display = 'flex';
}

function closeCheckoutModal() {
    const modal = document.getElementById('checkoutModal');
    if (modal) modal.style.display = 'none';
}

/**
 * Initiates the Razorpay payment flow:
 *   1. POST /api/online/orders → get Razorpay orderId
 *   2. Open Razorpay checkout modal
 *   3. On success: POST /api/online/orders/verify-payment → get token
 *   4. Show token to student
 */
async function initiatePayment() {
    const payBtn = document.getElementById('payBtn');
    const payBtnText = document.getElementById('payBtnText');
    const paySpinner = document.getElementById('paySpinner');

    payBtn.disabled = true;
    payBtnText.style.display = 'none';
    paySpinner.style.display = 'inline-block';

    try {
        // Step 1: Create the order and get Razorpay orderId
        const pickupTime = document.getElementById('pickupTime')?.value || null;
        const orderBody = {
            items: cart.map(c => ({ foodItemId: c.item.id, quantity: c.quantity })),
            pickupTime
        };

        const orderRes = await fetch('/api/online/orders', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + studentToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(orderBody)
        });

        const orderData = await orderRes.json();

        if (!orderRes.ok) {
            if (orderRes.status === 400 && orderData.message?.includes('closed')) {
                showErrorModal('⏰ Ordering Closed', orderData.message);
            } else {
                showErrorModal('Order Error', orderData.message || 'Could not create order. Please try again.');
            }
            return;
        }

        const { id: internalOrderId, razorpayOrderId, totalAmount } = orderData;

        // Step 2: Open Razorpay checkout modal
        const totalAmountInPaise = Math.round(totalAmount * 100);
        const studentName = localStorage.getItem('studentName') || 'Student';

        // Razorpay key ID — should be loaded from backend settings
        // For now, read from a meta tag or use a placeholder
        const keyId = document.querySelector('meta[name="rzp-key"]')?.content || 'rzp_test_PLACEHOLDER';

        const rzpOptions = {
            key: keyId,
            amount: totalAmountInPaise,
            currency: 'INR',
            name: 'Campus Bite Canteen',
            description: 'Food Pre-Order',
            order_id: razorpayOrderId,
            prefill: { name: studentName },
            theme: { color: '#6c63ff' },
            handler: async function(rzpResponse) {
                // Step 3: Verify the payment with our backend
                await verifyAndFinalizePayment(
                    rzpResponse.razorpay_order_id,
                    rzpResponse.razorpay_payment_id,
                    rzpResponse.razorpay_signature,
                    internalOrderId
                );
            },
            modal: {
                ondismiss: function() {
                    // Student closed the Razorpay modal without paying
                    payBtn.disabled = false;
                    payBtnText.style.display = 'inline';
                    paySpinner.style.display = 'none';
                }
            }
        };

        const rzp = new Razorpay(rzpOptions);
        rzp.open();

        // Hide the modal while Razorpay overlay is open
        closeCheckoutModal();

    } catch (err) {
        showErrorModal('Error', 'Something went wrong. Please try again.');
        console.error('Payment initiation error:', err);
    } finally {
        payBtn.disabled = false;
        payBtnText.style.display = 'inline';
        paySpinner.style.display = 'none';
    }
}

/**
 * Verifies the Razorpay payment with our backend and shows the student their token.
 */
async function verifyAndFinalizePayment(razorpayOrderId, razorpayPaymentId, razorpaySignature, internalOrderId) {
    try {
        const verifyRes = await fetch('/api/online/orders/verify-payment', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + studentToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ razorpayOrderId, razorpayPaymentId, razorpaySignature })
        });

        const token = await verifyRes.json();

        if (verifyRes.ok && token.tokenNumber) {
            // Payment confirmed — show the token to the student
            cart = [];
            localStorage.removeItem('studentCart');
            updateCartUI();

            showTokenModal(token);
        } else {
            showErrorModal('Payment Error', token.message || 'Payment verification failed. Please contact the canteen.');
        }
    } catch (err) {
        showErrorModal('Error', 'Payment verified but error getting token. Please check My Orders.');
    }
}

/**
 * Shows the student their food pickup token in a prominent modal.
 */
function showTokenModal(token) {
    // Remove any existing token modal
    const existing = document.getElementById('tokenModal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.id = 'tokenModal';
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal-card token-success-modal">
            <div class="token-success-icon">🎉</div>
            <h2>Payment Successful!</h2>
            <p>Show this token at the counter to collect your food</p>
            <div class="token-number-hero">#${token.tokenNumber}</div>
            <div class="token-item-hero">${escapeHtml(token.itemSummary || 'Your order')}</div>
            <div class="token-amount-hero">₹${token.amount}</div>
            <div class="token-modal-actions">
                <button onclick="document.getElementById('tokenModal').remove()"
                        class="btn-primary">Got it!</button>
                <a href="/student/my-orders" class="btn-secondary">View My Orders</a>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function showErrorModal(title, message) {
    alert(`${title}: ${message}`);
}

// ── UTILITIES ─────────────────────────────────────────────────────────────────

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function showToast(message, type = 'success') {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.className = `toast toast-${type}`;
    toast.style.display = 'block';
    setTimeout(() => toast.style.display = 'none', 3000);
}

function logoutStudent() {
    localStorage.removeItem('studentToken');
    localStorage.removeItem('studentName');
    window.location.href = '/student/login';
}
