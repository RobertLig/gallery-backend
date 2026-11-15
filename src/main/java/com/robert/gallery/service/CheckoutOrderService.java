package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.domain.Auction;
import com.robert.gallery.domain.Bid;
import com.robert.gallery.domain.CartItem;
import com.robert.gallery.domain.CheckoutOrder;
import com.robert.gallery.domain.CheckoutOrderItem;
import com.robert.gallery.domain.CheckoutOrderStatus;
import com.robert.gallery.domain.CheckoutOrderType;
import com.robert.gallery.domain.CheckoutReservation;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.dto.CheckoutOrderResponse;
import com.robert.gallery.dto.AdminOrderResponse;
import com.robert.gallery.dto.CollectionDetailsResponse;
import com.robert.gallery.dto.OrderHistoryResponse;
import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.BidRepository;
import com.robert.gallery.repository.CartItemRepository;
import com.robert.gallery.repository.CheckoutOrderRepository;
import com.robert.gallery.repository.CheckoutReservationRepository;
import com.robert.gallery.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class CheckoutOrderService {

    private static final Duration CART_PAYMENT_WINDOW = Duration.ofMinutes(31);
    private static final Duration AUCTION_PAYMENT_WINDOW = Duration.ofHours(24);
    private static final Duration ASYNC_PAYMENT_WINDOW = Duration.ofHours(48);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_INDEX = 10_000;

    @Inject
    CheckoutOrderRepository orders;

    @Inject
    CheckoutReservationRepository reservations;

    @Inject
    CartItemRepository cartItems;

    @Inject
    AuctionRepository auctions;

    @Inject
    BidRepository bids;

    @Inject
    UserRepository users;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.gallery.currency")
    String currencyCode;

    @ConfigProperty(name = "app.collection.address")
    String collectionAddress;

    @Transactional
    public CheckoutDraft createCartOrder(String userSubject) {
        Instant now = Instant.now();
        User user = requireUser(userSubject);
        List<CartItem> cart = cartItems.listByUser(user.id);
        if (cart.isEmpty()) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.cart.empty");
        }

        CheckoutOrder order = newOrder(user, CheckoutOrderType.CART, null, now, CART_PAYMENT_WINDOW);
        List<CartItem> orderedCart = cart.stream()
                .sorted((left, right) -> left.painting.id.compareTo(right.painting.id))
                .toList();
        List<Painting> reservedPaintings = new java.util.ArrayList<>(orderedCart.size());
        for (CartItem cartItem : orderedCart) {
            Painting painting = entityManager.find(Painting.class, cartItem.painting.id, LockModeType.PESSIMISTIC_WRITE);
            if (painting == null || painting.status != PaintingStatus.AVAILABLE
                    || auctions.findActiveForPainting(painting.id, now).isPresent()
                    || reservations.isReserved(painting.id)
                    || hasWinnerPaymentWindow(painting.id, now)) {
                throw new DomainException(Response.Status.CONFLICT, "error.checkout.painting.unavailable");
            }
            addItem(order, painting, painting.priceMinor);
            reservedPaintings.add(painting);
        }
        orders.persist(order);
        reservePaintings(order, reservedPaintings);
        entityManager.flush();
        return toDraft(order);
    }

    @Transactional
    public CheckoutDraft createAuctionOrder(String userSubject, UUID auctionId) {
        Instant now = Instant.now();
        User user = requireUser(userSubject);
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.auction.not-found"));
        if (auction.endsAt.isAfter(now)) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.not-ended");
        }
        Instant paymentDeadline = auction.endsAt.plus(AUCTION_PAYMENT_WINDOW);
        if (!paymentDeadline.isAfter(now)) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.payment-expired");
        }
        if (paymentDeadline.isBefore(now.plus(Duration.ofMinutes(30)))) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.payment-window.short");
        }

        Bid winningBid = bids.highestForAuction(auctionId)
                .orElseThrow(() -> new DomainException(Response.Status.CONFLICT, "error.auction.no-bids"));
        if (!winningBid.bidder.id.equals(user.id)) {
            throw new DomainException(Response.Status.FORBIDDEN, "error.auction.not-winner");
        }

        List<CheckoutOrder> existingOrders = orders.list("auctionId", auctionId);
        if (existingOrders.stream().anyMatch(order -> order.status != CheckoutOrderStatus.FAILED)) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.auction.already-checked-out");
        }

        Painting painting = entityManager.find(Painting.class, auction.painting.id, LockModeType.PESSIMISTIC_WRITE);
        if (painting == null || painting.status != PaintingStatus.AVAILABLE
                || reservations.isReserved(painting.id)) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.painting.unavailable");
        }

        CheckoutOrder order = newOrder(user, CheckoutOrderType.AUCTION, auctionId, now, AUCTION_PAYMENT_WINDOW);
        order.expiresAt = paymentDeadline;
        addItem(order, painting, winningBid.amountMinor);
        orders.persist(order);
        reservePaintings(order, List.of(painting));
        entityManager.flush();
        return toDraft(order);
    }

    @Transactional
    public void attachStripeSession(UUID orderId, StripeCheckoutSession session) {
        CheckoutOrder order = requireOrderForUpdate(orderId);
        if (order.status != CheckoutOrderStatus.PENDING_PAYMENT) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.order.not-pending");
        }
        if (order.stripeSessionId != null && !order.stripeSessionId.equals(session.id())) {
            throw new IllegalStateException("Stripe returned a different session for an idempotent checkout order");
        }
        order.stripeSessionId = session.id();
        order.expiresAt = session.expiresAt();
        for (CheckoutOrderItem item : order.items) {
            CheckoutReservation reservation = reservations.findByIdOptional(item.paintingId)
                    .orElseThrow(() -> new IllegalStateException("Checkout reservation is missing"));
            if (!reservation.order.id.equals(order.id)) {
                throw new IllegalStateException("Checkout reservation belongs to another order");
            }
            reservation.expiresAt = session.expiresAt();
        }
    }

    @Transactional
    public void failOrder(UUID orderId) {
        CheckoutOrder order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order != null && order.status == CheckoutOrderStatus.PENDING_PAYMENT) {
            order.status = CheckoutOrderStatus.FAILED;
            reservations.deleteByOrder(order.id);
        }
    }

    @Transactional
    public CheckoutOrderResponse getOrder(String userSubject, UUID orderId) {
        User user = requireUser(userSubject);
        CheckoutOrder order = orders.findByIdOptional(orderId)
                .filter(found -> found.user != null && found.user.id.equals(user.id))
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.checkout.order.not-found"));
        return new CheckoutOrderResponse(
                order.id,
                order.status.name(),
                order.totalMinor,
                order.currency,
                order.fulfillmentMode.name(),
                order.expiresAt
        );
    }

    @Transactional
    public List<CheckoutOrderResponse> getPendingOrders(String userSubject) {
        User user = requireUser(userSubject);
        return orders.listPendingByUser(user.id).stream()
                .map(order -> new CheckoutOrderResponse(
                        order.id,
                        order.status.name(),
                        order.totalMinor,
                        order.currency,
                        order.fulfillmentMode.name(),
                        order.expiresAt
                ))
                .toList();
    }

    @Transactional
    public PageResponse<OrderHistoryResponse> getOrderHistory(String userSubject, int page, int size) {
        validatePage(page, size);
        User user = requireUser(userSubject);
        List<OrderHistoryResponse> pageItems = orders.listByUser(user.id, page, size).stream()
                .map(OrderHistoryResponse::from)
                .toList();
        long total = orders.countByUser(user.id);
        return new PageResponse<>(pageItems, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public PageResponse<AdminOrderResponse> getAdminOrders(int page, int size) {
        validatePage(page, size);
        List<AdminOrderResponse> pageItems = orders.listAll(page, size).stream()
                .map(order -> new AdminOrderResponse(
                        OrderHistoryResponse.from(order),
                        order.user == null ? null : order.user.id,
                        order.user == null ? null : order.user.displayName,
                        order.user == null ? null : order.user.email,
                        order.user == null ? null : order.user.phone
                ))
                .toList();
        long total = orders.count();
        return new PageResponse<>(pageItems, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public CheckoutDraft prepareRetry(String userSubject, UUID orderId) {
        User user = requireUser(userSubject);
        CheckoutOrder order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.user != null && found.user.id.equals(user.id))
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.checkout.order.not-found"));
        if (order.status != CheckoutOrderStatus.PENDING_PAYMENT || !order.expiresAt.isAfter(Instant.now())) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.order.not-pending");
        }
        return toDraft(order);
    }

    @Transactional
    public CollectionDetailsResponse getCollectionDetails(String userSubject, UUID orderId) {
        User requester = requireUser(userSubject);
        CheckoutOrder order = orders.findByIdOptional(orderId)
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.checkout.order.not-found"));
        boolean isOwner = order.user != null && order.user.id.equals(requester.id);
        if (!isOwner && requester.role != com.robert.gallery.domain.UserRole.ADMIN) {
            throw new DomainException(Response.Status.NOT_FOUND, "error.checkout.order.not-found");
        }
        if (!isOwner && requester.role == com.robert.gallery.domain.UserRole.ADMIN) {
            return collectionDetails();
        }
        if (order.status != CheckoutOrderStatus.PAID) {
            throw new DomainException(Response.Status.CONFLICT, "error.checkout.collection.not-ready");
        }
        return collectionDetails();
    }

    @Transactional
    public List<PendingCheckoutSession> expiredPendingSessions(Instant cutoff, int limit) {
        return orders.listPendingExpired(cutoff, limit).stream()
                .map(order -> new PendingCheckoutSession(order.id, order.stripeSessionId))
                .toList();
    }

    @Transactional
    public void expirePendingOrder(UUID orderId) {
        CheckoutOrder order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order != null && isAwaitingPayment(order.status)
                && !order.expiresAt.isAfter(Instant.now())) {
            order.status = CheckoutOrderStatus.EXPIRED;
            reservations.deleteByOrder(order.id);
        }
    }

    @Transactional
    public void processWebhook(StripeWebhookEvent event) {
        if (event.orderId() == null) {
            return;
        }
        CheckoutOrder order = requireOrderForUpdate(event.orderId());
        if (order.stripeSessionId != null && !order.stripeSessionId.equals(event.sessionId())) {
            throw new IllegalStateException("Stripe Checkout session does not match the order");
        }
        if (order.stripeSessionId == null && order.status == CheckoutOrderStatus.PENDING_PAYMENT) {
            order.stripeSessionId = event.sessionId();
        }

        if (event.type().equals("checkout.session.expired")
                || event.type().equals("checkout.session.async_payment_failed")) {
            if (isAwaitingPayment(order.status)) {
                order.status = CheckoutOrderStatus.EXPIRED;
                reservations.deleteByOrder(order.id);
            }
            return;
        }

        if (!event.type().equals("checkout.session.completed")
                && !event.type().equals("checkout.session.async_payment_succeeded")) {
            return;
        }
        if (!"paid".equals(event.paymentStatus())) {
            if (event.type().equals("checkout.session.completed")
                    && order.status == CheckoutOrderStatus.PENDING_PAYMENT) {
                Instant asyncPaymentDeadline = Instant.now().plus(ASYNC_PAYMENT_WINDOW);
                order.status = CheckoutOrderStatus.ASYNC_PAYMENT_PENDING;
                order.expiresAt = asyncPaymentDeadline;
                for (CheckoutOrderItem item : order.items) {
                    CheckoutReservation reservation = reservations.findByIdOptional(item.paintingId)
                            .orElseThrow(() -> new IllegalStateException("Checkout reservation is missing"));
                    if (!reservation.order.id.equals(order.id)) {
                        throw new IllegalStateException("Checkout reservation belongs to another order");
                    }
                    reservation.expiresAt = asyncPaymentDeadline;
                }
            } else if (order.status == CheckoutOrderStatus.ASYNC_PAYMENT_PENDING
                    && !order.expiresAt.isAfter(Instant.now())) {
                order.status = CheckoutOrderStatus.EXPIRED;
                reservations.deleteByOrder(order.id);
            }
            return;
        }
        if (order.status == CheckoutOrderStatus.PAID) {
            return;
        }
        if (!isAwaitingPayment(order.status)
                || event.amountTotal() == null
                || event.amountTotal() != order.totalMinor
                || !order.currency.equalsIgnoreCase(event.currency())) {
            throw new IllegalStateException("Stripe payment details do not match the pending order");
        }

        for (CheckoutOrderItem item : order.items) {
            CheckoutReservation reservation = reservations.findByIdOptional(item.paintingId)
                    .orElseThrow(() -> new IllegalStateException("Paid order has no inventory reservation"));
            if (!reservation.order.id.equals(order.id)) {
                throw new IllegalStateException("Paid order does not own its inventory reservation");
            }
            Painting painting = entityManager.find(Painting.class, item.paintingId, LockModeType.PESSIMISTIC_WRITE);
            if (painting == null || painting.status != PaintingStatus.AVAILABLE) {
                throw new IllegalStateException("Reserved painting is no longer available");
            }
            painting.status = PaintingStatus.SOLD;
            if (order.type == CheckoutOrderType.CART && order.user != null) {
                cartItems.deleteByUserAndPainting(order.user.id, item.paintingId);
            }
        }
        order.status = CheckoutOrderStatus.PAID;
        reservations.deleteByOrder(order.id);
    }

    private CheckoutOrder newOrder(
            User user,
            CheckoutOrderType type,
            UUID auctionId,
            Instant now,
            Duration paymentWindow
    ) {
        CheckoutOrder order = new CheckoutOrder();
        order.user = user;
        order.type = type;
        order.auctionId = auctionId;
        order.currency = currency();
        order.expiresAt = now.plus(paymentWindow);
        return order;
    }

    private void addItem(CheckoutOrder order, Painting painting, long amountMinor) {
        CheckoutOrderItem item = new CheckoutOrderItem();
        item.order = order;
        item.paintingId = painting.id;
        item.paintingTitle = painting.title;
        item.amountMinor = amountMinor;
        order.items.add(item);
        order.totalMinor = Math.addExact(order.totalMinor, amountMinor);
    }

    private void reservePaintings(CheckoutOrder order, List<Painting> paintings) {
        for (Painting painting : paintings) {
            CheckoutReservation reservation = new CheckoutReservation();
            reservation.paintingId = painting.id;
            reservation.order = order;
            reservation.expiresAt = order.expiresAt;
            entityManager.persist(reservation);
        }
    }

    private CheckoutDraft toDraft(CheckoutOrder order) {
        return new CheckoutDraft(
                order.id,
                order.type,
                order.auctionId,
                order.totalMinor,
                order.currency,
                order.expiresAt,
                order.items.stream()
                        .map(item -> new CheckoutLineItem(item.paintingTitle, item.amountMinor))
                        .toList()
        );
    }

    private CheckoutOrder requireOrderForUpdate(UUID orderId) {
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.checkout.order.not-found"));
    }

    private boolean isAwaitingPayment(CheckoutOrderStatus status) {
        return status == CheckoutOrderStatus.PENDING_PAYMENT
                || status == CheckoutOrderStatus.ASYNC_PAYMENT_PENDING;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > MAX_PAGE_INDEX || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.pagination.invalid");
        }
    }

    private CollectionDetailsResponse collectionDetails() {
        if (collectionAddress == null || collectionAddress.isBlank()
                || "not-configured".equalsIgnoreCase(collectionAddress.trim())) {
            throw new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.collection.address.unconfigured");
        }
        return new CollectionDetailsResponse(collectionAddress.trim(), "PICKUP");
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

    private boolean hasWinnerPaymentWindow(UUID paintingId, Instant now) {
        return auctions.findLatestForPainting(paintingId)
                .filter(auction -> auction.endsAt.plus(AUCTION_PAYMENT_WINDOW).isAfter(now))
                .filter(auction -> bids.countByAuction(auction.id) > 0)
                .isPresent();
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
