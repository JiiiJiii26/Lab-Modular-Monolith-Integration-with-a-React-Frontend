import { useState, useEffect, useCallback, useRef } from 'react'
import './App.css'

export default function App() {
  const [inventory, setInventory] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [cart, setCart] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const [loading, setLoading] = useState(false)
  const [submittingOrder, setSubmittingOrder] = useState(false)
  const [cancellingOrderId, setCancellingOrderId] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [orderResult, setOrderResult] = useState(null)

  // Ref to the current AbortController so we can cancel stale in-flight requests
  // when loadAll is called again before the previous batch finishes.
  const abortRef = useRef(null)

  /**
   * loadAll — single consolidated data loader.
   * - Deps array is [] so this function's identity is stable for the lifetime
   *   of the component; it will never cause a useEffect to re-fire on its own.
   * - AbortController cancels any in-flight requests from a previous call before
   *   starting a new one, preventing stale-response races.
   * - setSelectedProductId is initialised here only when it is still empty, so
   *   it does NOT need to be a dep of this callback.
   */
  const loadAll = useCallback(async () => {
    // Cancel any previous in-flight fetch batch
    if (abortRef.current) {
      abortRef.current.abort()
    }
    const controller = new AbortController()
    abortRef.current = controller
    const { signal } = controller

    setLoading(true)
    setErrorMsg('')
    try {
      const [inv, ord, notif] = await Promise.all([
        fetch('/api/inventory',    { signal }).then(r => { if (!r.ok) throw new Error(`Inventory HTTP ${r.status}`); return r.json() }),
        fetch('/api/orders',       { signal }).then(r => { if (!r.ok) throw new Error(`Orders HTTP ${r.status}`);    return r.json() }),
        fetch('/api/notifications',{ signal }).then(r => { if (!r.ok) throw new Error(`Notifications HTTP ${r.status}`); return r.json() }),
      ])

      if (signal.aborted) return   // discard if superseded

      setInventory(inv)
      setOrders(ord)
      setNotifications(notif)

      // Initialise the dropdown to the first product on first load only
      setSelectedProductId(prev => (prev === '' && inv.length > 0) ? inv[0].productId : prev)
    } catch (err) {
      if (err.name === 'AbortError') return  // cancelled — not an error
      console.error('Failed to load data:', err)
      setErrorMsg(`Failed to load data: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])  // ← stable: no data state in deps; AbortController lives in a ref

  // Mount-only effect: fire loadAll exactly once when the component mounts.
  // [loadAll] is safe here because loadAll's identity is stable ([] deps above).
  useEffect(() => {
    loadAll()
    // Cleanup: abort any in-flight requests if the component unmounts
    return () => { if (abortRef.current) abortRef.current.abort() }
  }, [loadAll])

  // Cart operations
  const handleAddToCart = (e) => {
    e.preventDefault()
    if (!selectedProductId) return
    const qty = parseInt(quantity, 10)
    if (isNaN(qty) || qty <= 0) {
      setErrorMsg('Please enter a valid positive quantity.')
      return
    }

    const item = inventory.find((i) => i.productId === selectedProductId)
    const existingIndex = cart.findIndex((c) => c.productId === selectedProductId)

    if (existingIndex > -1) {
      const updatedCart = [...cart]
      updatedCart[existingIndex].quantity += qty
      setCart(updatedCart)
    } else {
      setCart([
        ...cart,
        {
          productId: selectedProductId,
          name: item ? item.name : selectedProductId,
          quantity: qty
        }
      ])
    }

    setQuantity(1)
    setErrorMsg('')
  }

  const handleRemoveFromCart = (index) => {
    setCart(cart.filter((_, i) => i !== index))
  }

  const handleClearCart = () => {
    setCart([])
  }

  // Place Order (POST /api/orders with multi-item payload)
  const handlePlaceOrder = async () => {
    if (cart.length === 0) {
      setErrorMsg('Your cart is empty. Add at least one item before placing an order.')
      return
    }

    setSubmittingOrder(true)
    setErrorMsg('')

    try {
      const payload = {
        items: cart.map((c) => ({ productId: c.productId, quantity: c.quantity }))
      }

      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })

      if (!res.ok) throw new Error(`Order placement failed (HTTP ${res.status})`)

      const data = await res.json()
      setOrderResult(data)

      if (data.status === 'CONFIRMED') {
        setCart([])
      }

      await loadAll()
    } catch (err) {
      console.error('Error placing order:', err)
      setErrorMsg(`Failed to submit order: ${err.message}`)
    } finally {
      setSubmittingOrder(false)
    }
  }

  // Cancel Order (POST /api/orders/{id}/cancel)
  const handleCancelOrder = async (orderId) => {
    setCancellingOrderId(orderId)
    setErrorMsg('')

    try {
      const res = await fetch(`/api/orders/${orderId}/cancel`, {
        method: 'POST'
      })

      if (res.status === 409) {
        setErrorMsg(`Order ${orderId} is already CANCELLED.`)
      } else if (res.status === 404) {
        setErrorMsg(`Order ${orderId} not found.`)
      } else if (!res.ok) {
        throw new Error(`Cancellation failed (HTTP ${res.status})`)
      }

      await loadAll()
    } catch (err) {
      console.error('Error cancelling order:', err)
      setErrorMsg(`Failed to cancel order: ${err.message}`)
    } finally {
      setCancellingOrderId(null)
    }
  }

  const currentSelectedProduct = inventory.find((i) => i.productId === selectedProductId)

  return (
    <div className="container">
      <header className="header">
        <h1>Store ni Sir Frederick</h1>
        <button
          type="button"
          className="btn btn-secondary refresh-btn"
          onClick={loadAll}
          disabled={loading}
        >
          {loading ? 'Refreshing...' : 'Refresh All'}
        </button>
      </header>

      {errorMsg && <div className="alert alert-danger">{errorMsg}</div>}

      <main className="dashboard-grid">
        {/* SECTION A: Cart Builder */}
        <section className="card cart-builder-card">
          <h2>A. Cart Builder</h2>

          <form onSubmit={handleAddToCart} className="cart-add-form">
            <div className="form-group">
              <label htmlFor="productSelect">Select Product:</label>
              <select
                id="productSelect"
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
                disabled={submittingOrder}
              >
                {inventory.length === 0 ? (
                  <option value="">No products available</option>
                ) : (
                  inventory.map((item) => (
                    <option key={item.productId} value={item.productId}>
                      {item.name} ({item.productId}) &mdash; Stock: {item.stock}
                    </option>
                  ))
                )}
              </select>
              {currentSelectedProduct && (
                <div className="stock-hint">
                  Available in stock: <strong>{currentSelectedProduct.stock}</strong> units
                </div>
              )}
            </div>

            <div className="form-row">
              <div className="form-group qty-group">
                <label htmlFor="quantityInput">Quantity:</label>
                <input
                  id="quantityInput"
                  type="number"
                  min="1"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  disabled={submittingOrder}
                  required
                />
              </div>
              <button
                type="submit"
                className="btn btn-secondary add-to-cart-btn"
                disabled={submittingOrder || !selectedProductId}
              >
                + Add to Cart
              </button>
            </div>
          </form>

          {/* Cart Contents */}
          <div className="cart-contents">
            <div className="cart-header">
              <h3>Cart Items ({cart.length})</h3>
              {cart.length > 0 && (
                <button
                  type="button"
                  className="btn-link"
                  onClick={handleClearCart}
                  disabled={submittingOrder}
                >
                  Clear Cart
                </button>
              )}
            </div>

            {cart.length === 0 ? (
              <p className="empty-cart-hint">Cart is empty. Add products above to start an order.</p>
            ) : (
              <ul className="cart-list">
                {cart.map((cartItem, idx) => (
                  <li key={idx} className="cart-item">
                    <div className="cart-item-info">
                      <strong>{cartItem.name}</strong>
                      <span className="cart-item-code">({cartItem.productId})</span>
                      <span className="cart-item-qty">&times; {cartItem.quantity}</span>
                    </div>
                    <button
                      type="button"
                      className="btn-remove"
                      onClick={() => handleRemoveFromCart(idx)}
                      disabled={submittingOrder}
                      title="Remove item"
                    >
                      &times;
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <button
              type="button"
              className="btn btn-primary place-order-btn"
              onClick={handlePlaceOrder}
              disabled={submittingOrder || cart.length === 0}
            >
              {submittingOrder ? 'Processing Order...' : `Place Order (${cart.reduce((s, i) => s + i.quantity, 0)} items)`}
            </button>
          </div>

          {/* Order Result Banner */}
          {orderResult && (
            <div className={`result ${orderResult.status.toLowerCase()}`}>
              <div className="result-header">
                <span className="status-label">Status:</span>
                <span className={`status-badge badge-${orderResult.status.toLowerCase()}`}>
                  {orderResult.status}
                </span>
              </div>
              <div className="result-field">
                <strong>Reason:</strong>
                <p className="reason-text">{orderResult.reason}</p>
              </div>

              {orderResult.items && orderResult.items.length > 0 && (
                <div className="item-outcomes">
                  <strong>Item Outcomes:</strong>
                  <ul className="outcome-list">
                    {orderResult.items.map((it, i) => (
                      <li key={i}>
                        <code>{it.productId}</code>: <span className={`outcome-${it.outcome.toLowerCase()}`}>{it.outcome}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </section>

        {/* SECTION B: Live Inventory Table */}
        <section className="card inventory-card">
          <div className="card-header-flex">
            <h2>B. Live Inventory Table</h2>
            <span className="hint-pill">Low Stock &lt; 5 highlighted</span>
          </div>

          {inventory.length === 0 ? (
            <p className="empty-text">No inventory loaded.</p>
          ) : (
            <table className="inventory-table">
              <thead>
                <tr>
                  <th>Product ID</th>
                  <th>Name</th>
                  <th>Stock</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map((item) => {
                  const isLow = item.stock < 5
                  return (
                    <tr key={item.productId} className={isLow ? 'low-stock-row' : ''}>
                      <td><code>{item.productId}</code></td>
                      <td>{item.name}</td>
                      <td>
                        <strong className={isLow ? 'stock-warning' : 'stock-ok'}>
                          {item.stock}
                        </strong>
                      </td>
                      <td>
                        {item.stock === 0 ? (
                          <span className="badge out-of-stock">Out of Stock</span>
                        ) : isLow ? (
                          <span className="badge low-stock-badge">Low Stock</span>
                        ) : (
                          <span className="badge in-stock">In Stock</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </section>

        {/* SECTION C: Order History */}
        <section className="card order-history-card">
          <div className="card-header-flex">
            <h2>C. Order History</h2>
            <span className="hint-pill">{orders.length} order(s)</span>
          </div>

          {orders.length === 0 ? (
            <div className="empty-state">
              <p>No orders recorded yet.</p>
            </div>
          ) : (
            <div className="orders-list">
              {orders.map((ord) => {
                const isCancelled = ord.status === 'CANCELLED'
                const isRejected = ord.status === 'REJECTED'
                const isCancelling = cancellingOrderId === ord.orderId

                return (
                  <div key={ord.orderId} className={`order-card order-status-${ord.status.toLowerCase()}`}>
                    <div className="order-card-header">
                      <div>
                        <strong>Order #{ord.orderId}</strong>
                        <span className={`status-badge badge-${ord.status.toLowerCase()}`}>
                          {ord.status}
                        </span>
                      </div>
                      <span className="order-date">
                        {ord.createdAt ? new Date(ord.createdAt).toLocaleTimeString() : ''}
                      </span>
                    </div>

                    <p className="order-reason">{ord.reason}</p>

                    {ord.items && ord.items.length > 0 && (
                      <div className="order-items-snippet">
                        <strong>Line Items (Total Qty: {ord.totalQuantity}):</strong>
                        <ul>
                          {ord.items.map((it) => (
                            <li key={it.orderItemId}>
                              <code>{it.productId}</code> &times; {it.quantity}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    <div className="order-actions">
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleCancelOrder(ord.orderId)}
                        disabled={isCancelled || isRejected || isCancelling}
                      >
                        {isCancelling ? 'Cancelling...' : isCancelled ? 'Cancelled' : 'Cancel Order'}
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </section>

        {/* SECTION D: Notification Feed */}
        <section className="card notification-feed-card">
          <div className="card-header-flex">
            <h2>D. Notification Feed</h2>
            <span className="hint-pill">{notifications.length} event(s)</span>
          </div>

          {notifications.length === 0 ? (
            <div className="empty-state">
              <p>No notifications generated yet.</p>
            </div>
          ) : (
            <ul className="notification-list">
              {notifications.map((notif) => {
                const kind = notif.kind || 'INFO'
                return (
                  <li key={notif.notificationId} className="notification-item">
                    <span className={`notif-dot dot-${kind.toLowerCase()}`} title={kind} />
                    <div className="notif-content">
                      <p className="notif-msg">{notif.message}</p>
                      <span className="notif-time">
                        {notif.createdAt ? new Date(notif.createdAt).toLocaleTimeString() : ''} &bull; {kind}
                      </span>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </main>
    </div>
  )
}
