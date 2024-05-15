package org.TRestaurant;

import java.io.Serializable;

public class Order implements Serializable {
    private int id;
    private String details;
    private String status;

    public Order(int id, String details, String status) {
        this.id = id;
        this.details = details;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getDetails() {
        return details;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "Order ID: " + id + ", Details: " + details + ", Status: " + status;
    }
}