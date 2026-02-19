package com.example.ecommerce.service;

import com.example.ecommerce.dto.PaymentIntentResponse;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.repository.OrderRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;

    @Value("${stripe.currency:usd}")
    private String currency;

    public PaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Create a Stripe Payment Intent for an order
     * @param orderId The order ID
     * @param userEmail The user's email
     * @return PaymentIntentResponse with client secret
     * @throws StripeException if Stripe API call fails
     */
    @Transactional
    public PaymentIntentResponse createPaymentIntent(Long orderId, String userEmail) throws StripeException {
        // Fetch the order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        // Verify the order belongs to the user
        if (!order.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Order does not belong to the user");
        }

        // Check if order is already paid
        if ("PAID".equals(order.getPaymentStatus())) {
            throw new RuntimeException("Order is already paid");
        }

        // Convert amount to cents (Stripe works with smallest currency unit)
        long amountInCents = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // Create metadata for tracking
        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_id", orderId.toString());
        metadata.put("order_number", order.getOrderNumber());
        metadata.put("user_email", userEmail);

        // Create Payment Intent parameters
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency)
                .putMetadata("order_id", orderId.toString())
                .putMetadata("order_number", order.getOrderNumber())
                .putMetadata("user_email", userEmail)
                .setDescription("Payment for order " + order.getOrderNumber())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        // Create the Payment Intent
        PaymentIntent paymentIntent = PaymentIntent.create(params);

        // Update order with payment intent ID
        order.setTransactionId(paymentIntent.getId());
        order.setPaymentMethod("stripe");
        order.setPaymentStatus("PENDING");
        orderRepository.save(order);

        // Return response
        return new PaymentIntentResponse(
                paymentIntent.getClientSecret(),
                paymentIntent.getId(),
                paymentIntent.getStatus(),
                paymentIntent.getAmount(),
                paymentIntent.getCurrency()
        );
    }

    /**
     * Confirm a payment and update order status
     * @param orderId The order ID
     * @param paymentIntentId The Stripe Payment Intent ID
     * @param userEmail The user's email
     * @return Updated order
     */
    @Transactional
    public Order confirmPayment(Long orderId, String paymentIntentId, String userEmail) throws StripeException {
        // Fetch the order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        // Verify the order belongs to the user
        if (!order.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Order does not belong to the user");
        }

        // Retrieve Payment Intent from Stripe
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        // Check if payment is successful
        if ("succeeded".equals(paymentIntent.getStatus())) {
            order.setPaymentStatus("PAID");
            order.confirmOrder();
            orderRepository.save(order);
            return order;
        } else {
            throw new RuntimeException("Payment not successful. Status: " + paymentIntent.getStatus());
        }
    }

    /**
     * Get payment status from Stripe
     * @param paymentIntentId The Stripe Payment Intent ID
     * @return Payment Intent status
     */
    public String getPaymentStatus(String paymentIntentId) throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
        return paymentIntent.getStatus();
    }

    /**
     * Cancel a payment intent
     * @param paymentIntentId The Stripe Payment Intent ID
     * @return Cancelled Payment Intent
     */
    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
        return paymentIntent.cancel();
    }
}

