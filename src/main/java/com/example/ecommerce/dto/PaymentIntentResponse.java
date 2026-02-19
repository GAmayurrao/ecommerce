package com.example.ecommerce.dto;

public class PaymentIntentResponse {

    private String clientSecret;
    private String paymentIntentId;
    private String status;
    private Long amount;
    private String currency;

    // Constructors
    public PaymentIntentResponse() {
    }

    public PaymentIntentResponse(String clientSecret, String paymentIntentId, String status, Long amount, String currency) {
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
    }

    // Getters and Setters
    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

