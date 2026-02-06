package com.example.ecommerce.model;

public enum OrderStatus {
    PENDING,        // Order created, awaiting payment confirmation
    CONFIRMED,      // Payment confirmed, processing order
    PROCESSING,     // Order being prepared
    SHIPPED,        // Order shipped to customer
    DELIVERED,      // Order delivered successfully
    CANCELLED,      // Order cancelled by user or admin
    REFUNDED        // Order refunded
}

