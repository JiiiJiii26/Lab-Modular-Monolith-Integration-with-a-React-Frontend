package edu.cit.pena.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing an item in the inventory table.
 */
@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stock", nullable = false)
    private int stock;

    public InventoryItem() {
    }

    public InventoryItem(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "InventoryItem{" +
                "productId='" + productId + '\'' +
                ", name='" + name + '\'' +
                ", stock=" + stock +
                '}';
    }
}
