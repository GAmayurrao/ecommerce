package com.example.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentConfirmationRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Payment Intent ID is required")
    private String paymentIntentId;

    // Constructors
    public PaymentConfirmationRequest() {
    }

    public PaymentConfirmationRequest(Long orderId, String paymentIntentId) {
        this.orderId = orderId;
        this.paymentIntentId = paymentIntentId;
    }

    // Getters and Setters
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }
}

