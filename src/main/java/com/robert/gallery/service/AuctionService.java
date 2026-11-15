package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.domain.Auction;
import com.robert.gallery.domain.Bid;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.dto.AuctionResponse;
import com.robert.gallery.dto.BidResponse;
import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.dto.PlaceBidRequest;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.BidRepository;
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
public class AuctionService {

    public static final Duration AUCTION_DURATION = Duration.ofHours(1);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_INDEX = 10_000;
    private static final int MAX_BID_HISTORY = 100;

    @Inject
    AuctionRepository auctions;

    @Inject
    BidRepository bids;

    @Inject
    UserRepository users;

    @Inject
    CheckoutReservationRepository checkoutReservations;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.gallery.currency")
    String currencyCode;

    @Transactional
    public AuctionResponse start(UUID paintingId) {
        Painting painting = entityManager.find(Painting.class, paintingId, LockModeType.PESSIMISTIC_WRITE);
        if (painting == null) {
            throw new DomainException(Response.Status.NOT_FOUND, "error.painting.not-found");
        }
        if (painting.status != PaintingStatus.AVAILABLE) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.painting.unavailable");
        }
        if (checkoutReservations.isReserved(paintingId)) {
            throw new DomainException(Response.Status.CONFLICT, "error.painting.checkout.active");
        }
        boolean winnerPaymentWindow = auctions.findLatestForPainting(paintingId)
                .filter(auction -> auction.endsAt.plus(Duration.ofHours(24)).isAfter(Instant.now()))
                .filter(auction -> bids.countByAuction(auction.id) > 0)
                .isPresent();
        if (winnerPaymentWindow) {
            throw new DomainException(Response.Status.CONFLICT, "error.painting.auction.payment-window");
        }

        Instant now = Instant.now();
        if (auctions.findActiveForPainting(paintingId, now).isPresent()) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.already-active");
        }

        Auction auction = new Auction();
        auction.painting = painting;
        auction.startingPriceMinor = painting.priceMinor;
        auction.startedAt = now;
        auction.endsAt = now.plus(AUCTION_DURATION);
        auctions.persist(auction);
        return toResponse(auction, now);
    }

    @Transactional
    public PageResponse<AuctionResponse> listActive(int page, int size) {
        validatePage(page, size);
        Instant now = Instant.now();
        List<AuctionResponse> results = auctions.listActive(now, page, size).stream()
                .map(auction -> toResponse(auction, now))
                .toList();
        long total = auctions.countActive(now);
        return new PageResponse<>(results, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public AuctionResponse get(UUID id) {
        Auction auction = auctions.findByIdOptional(id)
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.auction.not-found"));
        return toResponse(auction, Instant.now());
    }

    @Transactional
    public List<BidResponse> recentBids(UUID auctionId) {
        if (!auctions.findByIdOptional(auctionId).isPresent()) {
            throw new DomainException(Response.Status.NOT_FOUND, "error.auction.not-found");
        }
        return bids.recentByAuction(auctionId, MAX_BID_HISTORY).stream().map(BidResponse::from).toList();
    }

    @Transactional
    public AuctionResponse placeBid(UUID auctionId, String userSubject, PlaceBidRequest request) {
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.auction.not-found"));
        Instant now = Instant.now();
        if (!auction.endsAt.isAfter(now)) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.ended");
        }

        UUID userId;
        try {
            userId = UUID.fromString(userSubject);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid");
        }
        User bidder = users.findByIdOptional(userId)
                .orElseThrow(() -> new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid"));

        long currentPrice = bids.currentPrice(auctionId, auction.startingPriceMinor);
        if (request.amountMinor() <= currentPrice) {
            throw new DomainException(Response.Status.CONFLICT, "error.auction.bid.too-low");
        }

        Bid bid = new Bid();
        bid.auction = auction;
        bid.bidder = bidder;
        bid.amountMinor = request.amountMinor();
        bid.placedAt = now;
        bids.persist(bid);
        entityManager.flush();
        return toResponse(auction, now);
    }

    private AuctionResponse toResponse(Auction auction, Instant now) {
        return AuctionResponse.from(
                auction,
                bids.currentPrice(auction.id, auction.startingPriceMinor),
                bids.countByAuction(auction.id),
                currency(),
                now
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > MAX_PAGE_INDEX || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.pagination.invalid");
        }
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
