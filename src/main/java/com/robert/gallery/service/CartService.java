package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.domain.CartItem;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.dto.CartItemResponse;
import com.robert.gallery.dto.CartResponse;
import com.robert.gallery.dto.PaintingResponse;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.BidRepository;
import com.robert.gallery.repository.CartItemRepository;
import com.robert.gallery.repository.CheckoutReservationRepository;
import com.robert.gallery.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class CartService {

    @Inject
    CartItemRepository items;

    @Inject
    UserRepository users;

    @Inject
    AuctionRepository auctions;

    @Inject
    BidRepository bids;

    @Inject
    CheckoutReservationRepository checkoutReservations;

    @Inject
    PaintingService paintings;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.gallery.currency")
    String currencyCode;

    @Transactional
    public CartResponse get(String userSubject) {
        User user = requireUser(userSubject);
        return buildCart(user.id);
    }

    @Transactional
    public CartResponse add(String userSubject, UUID paintingId) {
        User user = requireUser(userSubject);
        Painting painting = entityManager.find(Painting.class, paintingId, LockModeType.PESSIMISTIC_WRITE);
        if (painting == null) {
            throw new DomainException(Response.Status.NOT_FOUND, "error.painting.not-found");
        }
        if (!isPurchasable(painting)) {
            throw new DomainException(Response.Status.CONFLICT, "error.cart.painting.unavailable");
        }

        if (items.findByUserAndPainting(user.id, paintingId).isEmpty()) {
            CartItem item = new CartItem();
            item.user = user;
            item.painting = painting;
            items.persist(item);
        }
        return buildCart(user.id);
    }

    @Transactional
    public void remove(String userSubject, UUID paintingId) {
        User user = requireUser(userSubject);
        items.deleteByUserAndPainting(user.id, paintingId);
    }

    private CartResponse buildCart(UUID userId) {
        List<CartItemResponse> cartItems = items.listByUser(userId).stream()
                .map(item -> toResponse(item))
                .toList();
        long total = cartItems.stream()
                .filter(CartItemResponse::purchasable)
                .map(CartItemResponse::painting)
                .mapToLong(PaintingResponse::priceMinor)
                .sum();
        return new CartResponse(cartItems, total, currency());
    }

    private CartItemResponse toResponse(CartItem item) {
        Painting painting = item.painting;
        boolean purchasable = isPurchasable(painting);
        PaintingResponse paintingResponse = painting.status == PaintingStatus.DRAFT
                ? null
                : paintings.get(painting.id, false);
        return new CartItemResponse(item.painting.id, item.addedAt, purchasable, paintingResponse);
    }

    private boolean isPurchasable(Painting painting) {
        Instant now = Instant.now();
        return painting.status == PaintingStatus.AVAILABLE
                && auctions.findActiveForPainting(painting.id, now).isEmpty()
                && !checkoutReservations.isReserved(painting.id)
                && !hasWinnerPaymentWindow(painting.id, now);
    }

    private boolean hasWinnerPaymentWindow(UUID paintingId, Instant now) {
        return auctions.findLatestForPainting(paintingId)
                .filter(auction -> auction.endsAt.plus(java.time.Duration.ofHours(24)).isAfter(now))
                .filter(auction -> bidsExist(auction.id))
                .isPresent();
    }

    private boolean bidsExist(UUID auctionId) {
        return bids.countByAuction(auctionId) > 0;
    }

    private User requireUser(String subject) {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid");
        }
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid"));
    }

    private String currency() {
        try {
            Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
            if (currency.getDefaultFractionDigits() != 2) {
                throw new IllegalStateException("Gallery currency must have two minor-unit digits");
            }
            return currency.getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("GALLERY_CURRENCY must be a supported three-letter currency code", exception);
        }
    }
}
