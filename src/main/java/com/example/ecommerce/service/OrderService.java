package com.example.ecommerce.service;

import com.example.ecommerce.dto.CheckoutRequest;
import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       CartRepository cartRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // Create order from cart (checkout)
    public OrderResponse createOrderFromCart(String email, CheckoutRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        if (cart.isEmpty()) {
            throw new RuntimeException("Cannot create order from empty cart");
        }

        // Generate unique order number
        String orderNumber = generateOrderNumber();

        // Create order
        Order order = new Order(user, orderNumber);

        // Set shipping information
        order.setShippingName(request.getShippingName());
        order.setShippingAddress(request.getShippingAddress());
        order.setShippingCity(request.getShippingCity());
        order.setShippingState(request.getShippingState());
        order.setShippingPostalCode(request.getShippingPostalCode());
        order.setShippingCountry(request.getShippingCountry());
        order.setShippingPhone(request.getShippingPhone());

        // Set payment information
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus("PENDING");

        // Set costs
        order.setShippingCost(request.getShippingCost() != null ? request.getShippingCost() : BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTax(BigDecimal.ZERO); // Tax calculation can be added later

        // Set notes
        order.setNotes(request.getNotes());

        // Transfer cart items to order items
        for (var cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            // Check stock availability
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName());
            }

            // Create order item
            OrderItem orderItem = new OrderItem(
                    product,
                    cartItem.getQuantity(),
                    cartItem.getPriceAtAddition()
            );
            order.addItem(orderItem);

            // Reduce stock
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        // Calculate totals
        order.calculateTotals();

        // Save order
        order = orderRepository.save(order);

        // Clear cart
        cart.clearItems();
        cartRepository.save(cart);

        return mapToResponse(order);
    }

    // Get order by ID
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return mapToResponse(order);
    }

    // Get order by order number
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return mapToResponse(order);
    }

    // Get user's orders
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return orderRepository.findByUserId(user.getId(), pageable)
                .map(this::mapToResponse);
    }

    // Get all orders (admin)
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    // Get orders by status (admin)
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    // Update order status (admin)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(newStatus);

        // Update timestamps based on status change
        switch (newStatus) {
            case CONFIRMED:
                order.confirmOrder();
                break;
            case PROCESSING:
                order.markAsProcessing();
                break;
            case SHIPPED:
                order.markAsShipped();
                break;
            case DELIVERED:
                order.markAsDelivered();
                break;
            case REFUNDED:
                order.markAsRefunded();
                // Restore stock for refunded orders
                restoreStock(order);
                break;
        }

        order = orderRepository.save(order);
        return mapToResponse(order);
    }

    // Cancel order
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.canBeCancelled()) {
            throw new RuntimeException("Order cannot be cancelled in current status: " + order.getStatus());
        }

        order.cancel(reason);

        // Restore stock
        restoreStock(order);

        order = orderRepository.save(order);
        return mapToResponse(order);
    }

    // Cancel order by user
    public OrderResponse cancelOrderByUser(String email, Long orderId, String reason) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Order does not belong to user");
        }

        if (!order.canBeCancelled()) {
            throw new RuntimeException("Order cannot be cancelled in current status: " + order.getStatus());
        }

        order.cancel(reason);
        restoreStock(order);

        order = orderRepository.save(order);
        return mapToResponse(order);
    }

    // Helper method to restore stock
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    // Generate unique order number
    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String orderNumber = "ORD-" + timestamp + "-" + random;

        // Ensure uniqueness
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            orderNumber = "ORD-" + timestamp + "-" + random;
        }

        return orderNumber;
    }

    // Helper method to map Order to OrderResponse
    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();

        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setUserId(order.getUser().getId());
        response.setUserEmail(order.getUser().getEmail());
        response.setStatus(order.getStatus().name());

        // Amounts
        response.setSubtotal(order.getSubtotal());
        response.setTax(order.getTax());
        response.setShippingCost(order.getShippingCost());
        response.setDiscount(order.getDiscount());
        response.setTotalAmount(order.getTotalAmount());

        // Shipping info
        response.setShippingName(order.getShippingName());
        response.setShippingAddress(order.getShippingAddress());
        response.setShippingCity(order.getShippingCity());
        response.setShippingState(order.getShippingState());
        response.setShippingPostalCode(order.getShippingPostalCode());
        response.setShippingCountry(order.getShippingCountry());
        response.setShippingPhone(order.getShippingPhone());

        // Payment info
        response.setPaymentMethod(order.getPaymentMethod());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setTransactionId(order.getTransactionId());

        // Timestamps
        response.setCreatedAt(order.getCreatedAt());
        response.setConfirmedAt(order.getConfirmedAt());
        response.setShippedAt(order.getShippedAt());
        response.setDeliveredAt(order.getDeliveredAt());
        response.setCancelledAt(order.getCancelledAt());
        response.setCancellationReason(order.getCancellationReason());

        response.setNotes(order.getNotes());

        // Items
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());
        response.setItems(itemResponses);

        return response;
    }

    // Helper method to map OrderItem to OrderItemResponse
    private OrderItemResponse mapItemToResponse(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();

        response.setId(item.getId());
        response.setProductId(item.getProduct().getId());
        response.setProductName(item.getProduct().getName());
        response.setProductSku(item.getProduct().getSku());
        response.setProductImageUrl(item.getProduct().getImageUrl());
        response.setQuantity(item.getQuantity());
        response.setPriceAtPurchase(item.getPriceAtPurchase());
        response.setTotalPrice(item.getTotalPrice());

        return response;
    }
}

