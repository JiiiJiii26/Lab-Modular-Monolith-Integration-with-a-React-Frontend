import { useState, useEffect } from 'react'
import './App.css'

export default function App() {
  const [inventory, setInventory] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [loadingInventory, setLoadingInventory] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [orderResult, setOrderResult] = useState(null)

  // Fetch inventory list from backend
  const fetchInventory = async () => {
    setLoadingInventory(true)
    setErrorMsg('')
    try {
      const res = await fetch('/api/inventory')
      if (!res.ok) {
        throw new Error(`Failed to load inventory (HTTP ${res.status})`)
      }
      const data = await res.json()
      setInventory(data)
      // Auto-select first product if none currently selected
      if (data && data.length > 0 && !selectedProductId) {
        setSelectedProductId(data[0].productId)
      }
    } catch (err) {
      console.error('Error fetching inventory:', err)
      setErrorMsg(`Unable to load inventory: ${err.message}. Ensure backend is running.`)
    } finally {
      setLoadingInventory(false)
    }
  }

  useEffect(() => {
    fetchInventory()
  }, [])

  // Handle order submission
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!selectedProductId) {
      setErrorMsg('Please select a product.')
      return
    }

    setSubmitting(true)
    setErrorMsg('')

    try {
      const payload = {
        productId: selectedProductId,
        quantity: parseInt(quantity, 10)
      }

      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      })

      if (!res.ok) {
        throw new Error(`Order request failed (HTTP ${res.status})`)
      }

      const data = await res.json()
      setOrderResult(data)

      // Refresh inventory so dropdown immediately reflects updated stock levels
      await fetchInventory()
    } catch (err) {
      console.error('Error placing order:', err)
      setErrorMsg(`Failed to submit order: ${err.message}`)
    } finally {
      setSubmitting(false)
    }
  }

  const currentSelectedProduct = inventory.find(i => i.productId === selectedProductId)

  return (
    <div className="container">
      <header className="header">
        <h1>Store ni Sir Fredrick</h1>
      </header>

      <main className="grid">
        {/* Order Form Card */}
        <section className="card">
          <h2>Place New Order</h2>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="productSelect">Select Product:</label>
              <select
                id="productSelect"
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
                disabled={loadingInventory || submitting}
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

            <div className="form-group">
              <label htmlFor="quantityInput">Order Quantity:</label>
              <input
                id="quantityInput"
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                disabled={submitting}
                required
              />
            </div>

            <div className="actions">
              <button
                type="submit"
                className="btn btn-primary"
                disabled={submitting || loadingInventory || !selectedProductId}
              >
                {submitting ? 'Submitting Order...' : 'Submit Order'}
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={fetchInventory}
                disabled={loadingInventory || submitting}
              >
                {loadingInventory ? 'Refreshing...' : 'Refresh Inventory'}
              </button>
            </div>
          </form>

          {errorMsg && <div className="alert alert-danger">{errorMsg}</div>}
        </section>

        {/* Order Result Card */}
        <section className="card">
          <h2>Order Result Area</h2>
          {!orderResult ? (
            <div className="empty-state">
              <p>No orders submitted yet in this session.</p>
              <p className="hint">Select a product, enter a quantity, and submit to see real-time CONFIRMED or REJECTED results.</p>
            </div>
          ) : (
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

              {orderResult.inventory && (
                <div className="snapshot-card">
                  <h3>Returned Inventory Snapshot</h3>
                  <table className="snapshot-table">
                    <tbody>
                      <tr>
                        <th>Product ID:</th>
                        <td><code>{orderResult.inventory.productId}</code></td>
                      </tr>
                      <tr>
                        <th>Product Name:</th>
                        <td>{orderResult.inventory.name}</td>
                      </tr>
                      <tr>
                        <th>Remaining Stock:</th>
                        <td>
                          <span className={orderResult.inventory.stock > 0 ? 'stock-ok' : 'stock-zero'}>
                            {orderResult.inventory.stock}
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </section>
      </main>

      {/* Live Inventory Overview Table */}
      <section className="card inventory-summary">
        <h2>Live Inventory Table</h2>
        {inventory.length === 0 ? (
          <p className="empty-text">No inventory loaded.</p>
        ) : (
          <table className="inventory-table">
            <thead>
              <tr>
                <th>Product ID</th>
                <th>Product Name</th>
                <th>Current Stock</th>
                <th>Availability</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map((item) => (
                <tr key={item.productId}>
                  <td><code>{item.productId}</code></td>
                  <td>{item.name}</td>
                  <td><strong>{item.stock}</strong></td>
                  <td>
                    {item.stock > 0 ? (
                      <span className="badge in-stock">In Stock</span>
                    ) : (
                      <span className="badge out-of-stock">Out of Stock</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
