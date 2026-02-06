package com.example.ecommerce.service;

import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.dto.CartItemResponse;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // Get or create cart for authenticated user
    public CartResponse getOrCreateUserCart(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart(user);
                    return cartRepository.save(newCart);
                });

        return mapToResponse(cart);
    }

    // Get or create cart for guest (session-based)
    public CartResponse getOrCreateGuestCart(String sessionId) {
        final String finalSessionId = (sessionId == null || sessionId.isEmpty())
                ? UUID.randomUUID().toString()
                : sessionId;

        Cart cart = cartRepository.findBySessionId(finalSessionId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(finalSessionId);
                    return cartRepository.save(newCart);
                });

        if (cart.isExpired()) {
            cartRepository.delete(cart);
            Cart newCart = new Cart(finalSessionId);
            cart = cartRepository.save(newCart);
        }

        return mapToResponse(cart);
    }

    // Add item to cart (user)
    public CartResponse addToUserCart(String email, AddToCartRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> cartRepository.save(new Cart(user)));

        return addItemToCart(cart, request);
    }

    // Add item to cart (guest)
    public CartResponse addToGuestCart(String sessionId, AddToCartRequest request) {
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Cart not found for session"));

        return addItemToCart(cart, request);
    }

    // Common method to add item to cart
    private CartResponse addItemToCart(Cart cart, AddToCartRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getActive()) {
            throw new RuntimeException("Product is not available");
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock. Available: " + product.getStockQuantity());
        }

        // Check if product already in cart
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.isSameProduct(product))
                .findFirst();

        if (existingItem.isPresent()) {
            // Update quantity
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();

            if (product.getStockQuantity() < newQuantity) {
                throw new RuntimeException("Cannot add more. Only " + product.getStockQuantity() + " items available");
            }

            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            // Add new item
            CartItem newItem = new CartItem(product, request.getQuantity());
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
        }

        cart = cartRepository.save(cart);
        return mapToResponse(cart);
    }

    // Update cart item quantity
    public CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cartId)) {
            throw new RuntimeException("Item does not belong to this cart");
        }

        Product product = item.getProduct();
        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + product.getStockQuantity());
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return mapToResponse(cart);
    }

    // Remove item from cart
    public CartResponse removeCartItem(Long cartId, Long itemId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cartId)) {
            throw new RuntimeException("Item does not belong to this cart");
        }

        cart.removeItem(item);
        cartItemRepository.delete(item);
        cartRepository.save(cart);

        return mapToResponse(cart);
    }

    // Clear cart
    public void clearCart(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.clearItems();
        cartItemRepository.deleteByCartId(cartId);
        cartRepository.save(cart);
    }

    // Merge guest cart into user cart (when user logs in)
    public CartResponse mergeGuestCartToUser(String sessionId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Cart> guestCartOpt = cartRepository.findBySessionId(sessionId);
        if (guestCartOpt.isEmpty()) {
            return getOrCreateUserCart(email);
        }

        Cart guestCart = guestCartOpt.get();
        Cart userCart = cartRepository.findByUser(user)
                .orElseGet(() -> cartRepository.save(new Cart(user)));

        // Merge items from guest cart to user cart
        for (CartItem guestItem : guestCart.getItems()) {
            Optional<CartItem> existingUserItem = userCart.getItems().stream()
                    .filter(item -> item.isSameProduct(guestItem.getProduct()))
                    .findFirst();

            if (existingUserItem.isPresent()) {
                // Update quantity
                CartItem item = existingUserItem.get();
                item.setQuantity(item.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(item);
            } else {
                // Add new item to user cart
                CartItem newItem = new CartItem(guestItem.getProduct(), guestItem.getQuantity());
                userCart.addItem(newItem);
                cartItemRepository.save(newItem);
            }
        }

        // Delete guest cart
        cartRepository.delete(guestCart);

        userCart = cartRepository.save(userCart);
        return mapToResponse(userCart);
    }

    // Get cart by ID
    @Transactional(readOnly = true)
    public CartResponse getCartById(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));
        return mapToResponse(cart);
    }

    // Helper method to map Cart to CartResponse
    private CartResponse mapToResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());

        if (cart.getUser() != null) {
            response.setUserId(cart.getUser().getId());
            response.setUserEmail(cart.getUser().getEmail());
        }

        response.setSessionId(cart.getSessionId());

        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        response.setItems(itemResponses);
        response.setTotalItems(cart.getTotalItems());
        response.setSubtotal(cart.getSubtotal());
        response.setIsEmpty(cart.isEmpty());

        return response;
    }

    // Helper method to map CartItem to CartItemResponse
    private CartItemResponse mapItemToResponse(CartItem item) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProduct().getId());
        response.setProductName(item.getProduct().getName());
        response.setProductSku(item.getProduct().getSku());
        response.setProductImageUrl(item.getProduct().getImageUrl());
        response.setQuantity(item.getQuantity());
        response.setPriceAtAddition(item.getPriceAtAddition());
        response.setTotalPrice(item.getTotalPrice());
        response.setInStock(item.getProduct().isInStock());
        response.setAvailableStock(item.getProduct().getStockQuantity());

        return response;
    }
}


