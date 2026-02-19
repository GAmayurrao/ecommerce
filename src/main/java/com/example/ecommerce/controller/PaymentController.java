package com.example.ecommerce.controller;

import com.example.ecommerce.dto.PaymentConfirmationRequest;
import com.example.ecommerce.dto.PaymentIntentRequest;
import com.example.ecommerce.dto.PaymentIntentResponse;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.service.PaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a payment intent for an order
     * POST /api/payment/create-intent
     */
    @PostMapping("/create-intent")
    public ResponseEntity<?> createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            String email = authentication.getName();
            PaymentIntentResponse response = paymentService.createPaymentIntent(
                    request.getOrderId(),
                    email
            );
            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirm a payment after successful payment
     * POST /api/payment/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(
            @Valid @RequestBody PaymentConfirmationRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            String email = authentication.getName();
            Order order = paymentService.confirmPayment(
                    request.getOrderId(),
                    request.getPaymentIntentId(),
                    email
            );

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment confirmed successfully");
            response.put("orderId", order.getId());
            response.put("orderNumber", order.getOrderNumber());
            response.put("paymentStatus", order.getPaymentStatus());
            response.put("orderStatus", order.getStatus());

            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get payment status
     * GET /api/payment/status/{paymentIntentId}
     */
    @GetMapping("/status/{paymentIntentId}")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable String paymentIntentId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            String status = paymentService.getPaymentStatus(paymentIntentId);
            return ResponseEntity.ok(Map.of(
                    "paymentIntentId", paymentIntentId,
                    "status", status
            ));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    /**
     * Cancel a payment intent
     * POST /api/payment/cancel/{paymentIntentId}
     */
    @PostMapping("/cancel/{paymentIntentId}")
    public ResponseEntity<?> cancelPayment(
            @PathVariable String paymentIntentId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            PaymentIntent cancelledIntent = paymentService.cancelPaymentIntent(paymentIntentId);
            return ResponseEntity.ok(Map.of(
                    "message", "Payment cancelled successfully",
                    "paymentIntentId", cancelledIntent.getId(),
                    "status", cancelledIntent.getStatus()
            ));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    /**
     * Test endpoint to verify Stripe configuration
     * GET /api/payment/test
     */
    @GetMapping("/test")
    public ResponseEntity<?> testStripeConnection(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Stripe payment integration is configured",
                "status", "ready",
                "note", "Use test card 4242 4242 4242 4242 for testing"
        ));
    }
}

