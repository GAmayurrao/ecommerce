package com.example.ecommerce.controller;

import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.dto.UpdateCartItemRequest;
import com.example.ecommerce.service.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // Get current user's cart or create if doesn't exist
    @GetMapping
    public ResponseEntity<CartResponse> getCart(HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            // Authenticated user
            String email = auth.getName();
            CartResponse cart = cartService.getOrCreateUserCart(email);
            return ResponseEntity.ok(cart);
        } else {
            // Guest user
            String sessionId = session.getId();
            CartResponse cart = cartService.getOrCreateGuestCart(sessionId);
            return ResponseEntity.ok(cart);
        }
    }

    // Add item to cart
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            HttpSession session) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        try {
            CartResponse cart;
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                // Authenticated user
                String email = auth.getName();
                cart = cartService.addToUserCart(email, request);
            } else {
                // Guest user
                String sessionId = session.getId();
                cart = cartService.addToGuestCart(sessionId, request);
            }
            return ResponseEntity.ok(cart);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update cart item quantity
    @PutMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<CartResponse> updateCartItem(
            @PathVariable Long cartId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        try {
            CartResponse cart = cartService.updateCartItem(cartId, itemId, request.getQuantity());
            return ResponseEntity.ok(cart);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Remove item from cart
    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<CartResponse> removeCartItem(
            @PathVariable Long cartId,
            @PathVariable Long itemId) {

        try {
            CartResponse cart = cartService.removeCartItem(cartId, itemId);
            return ResponseEntity.ok(cart);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Clear cart
    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> clearCart(@PathVariable Long cartId) {
        try {
            cartService.clearCart(cartId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Merge guest cart to user cart (called after login)
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> mergeCart(
            @RequestParam String sessionId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String email = authentication.getName();
            CartResponse cart = cartService.mergeGuestCartToUser(sessionId, email);
            return ResponseEntity.ok(cart);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

