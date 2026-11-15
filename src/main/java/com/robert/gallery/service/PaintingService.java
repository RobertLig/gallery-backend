package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingPhoto;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.dto.PaintingPhotoResponse;
import com.robert.gallery.dto.PaintingRequest;
import com.robert.gallery.dto.PaintingResponse;
import com.robert.gallery.dto.PhotoOrderRequest;
import com.robert.gallery.dto.PhotoUploadRequest;
import com.robert.gallery.dto.PhotoUploadResponse;
import com.robert.gallery.repository.PaintingPhotoRepository;
import com.robert.gallery.repository.PaintingRepository;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.BidRepository;
import com.robert.gallery.repository.CheckoutReservationRepository;
import com.robert.gallery.storage.PaintingStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class PaintingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_INDEX = 10_000;
    private static final int MAX_PHOTOS = 10;
    private static final Pattern ALLOWED_IMAGE_TYPE = Pattern.compile("image/(jpeg|png|webp)");

    @Inject
    PaintingRepository paintings;

    @Inject
    PaintingPhotoRepository photos;

    @Inject
    AuctionRepository auctions;

    @Inject
    BidRepository bids;

    @Inject
    CheckoutReservationRepository checkoutReservations;

    @Inject
    PaintingStorage storage;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.gallery.currency")
    String currencyCode;

    @Transactional
    public PageResponse<PaintingResponse> list(
            boolean includeDrafts,
            String search,
            String medium,
            Long minPrice,
            Long maxPrice,
            Integer minYear,
            Integer maxYear,
            String status,
            String sort,
            int page,
            int size
    ) {
        if (page < 0 || page > MAX_PAGE_INDEX || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.pagination.invalid");
        }
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0
                || minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.price-range.invalid");
        }
        if (minYear != null && maxYear != null && minYear > maxYear) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.year-range.invalid");
        }
        if (minYear != null && (minYear < 1000 || minYear > 9999)
                || maxYear != null && (maxYear < 1000 || maxYear > 9999)) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.year-range.invalid");
        }
        if (search != null && search.length() > 100 || medium != null && medium.length() > 120) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.filter.invalid");
        }

        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> parameters = new java.util.HashMap<>();
        if (!includeDrafts) {
            query.append(" and status <> :draft");
            parameters.put("draft", PaintingStatus.DRAFT);
        }
        if (search != null && !search.isBlank()) {
            query.append(" and (lower(title) like :search or lower(description) like :search or lower(medium) like :search)");
            parameters.put("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (medium != null && !medium.isBlank()) {
            query.append(" and lower(medium) like :medium");
            parameters.put("medium", "%" + medium.trim().toLowerCase(Locale.ROOT) + "%");
        }
        appendRange(query, parameters, "priceMinor", "minPrice", minPrice, ">=");
        appendRange(query, parameters, "priceMinor", "maxPrice", maxPrice, "<=");
        appendRange(query, parameters, "artworkYear", "minYear", minYear, ">=");
        appendRange(query, parameters, "artworkYear", "maxYear", maxYear, "<=");
        if (status != null && !status.isBlank()) {
            try {
                query.append(" and status = :status");
                parameters.put("status", PaintingStatus.valueOf(status.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.status.invalid");
            }
        }

        String order = switch (sort == null || sort.isBlank() ? "recent" : sort) {
            case "recent" -> "updatedAt desc";
            case "oldest" -> "createdAt asc";
            case "price-asc" -> "priceMinor asc, id asc";
            case "price-desc" -> "priceMinor desc, id asc";
            default -> throw new DomainException(Response.Status.BAD_REQUEST, "error.painting.sort.invalid");
        };
        var result = paintings.find(query + " order by " + order, parameters);
        long total = result.count();
        List<PaintingResponse> items = result.page(page, size).list().stream().map(this::toResponse).toList();
        int pages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }

    @Transactional
    public PaintingResponse get(UUID id, boolean includeDrafts) {
        Painting painting = requirePainting(id);
        if (!includeDrafts && painting.status == PaintingStatus.DRAFT) {
            throw notFound();
        }
        return toResponse(painting);
    }

    @Transactional
    public PaintingResponse create(PaintingRequest request) {
        Painting painting = new Painting();
        apply(painting, request);
        paintings.persist(painting);
        return toResponse(painting);
    }

    @Transactional
    public PaintingResponse update(UUID id, PaintingRequest request) {
        Painting painting = requirePaintingForUpdate(id);
        ensureNoActiveAuction(id);
        apply(painting, request);
        return toResponse(painting);
    }

    @Transactional
    public void delete(UUID id) {
        Painting painting = requirePaintingForUpdate(id);
        ensureNoActiveAuction(id);
        for (PaintingPhoto photo : painting.photos) {
            storage.deleteObject(photo.objectKey);
        }
        paintings.delete(painting);
    }

    @Transactional
    public PhotoUploadResponse presignPhoto(UUID paintingId, PhotoUploadRequest request) {
        Painting painting = requirePaintingForUpdate(paintingId);
        validateImage(request);
        long photoCount = photos.countByPaintingId(paintingId);
        if (photoCount >= MAX_PHOTOS) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.limit");
        }

        String key = "paintings/" + painting.id + "/" + UUID.randomUUID();
        PaintingPhoto photo = new PaintingPhoto();
        photo.painting = painting;
        photo.objectKey = key;
        photo.contentType = request.contentType();
        photo.fileSize = request.fileSize();
        photo.sortOrder = (int) photoCount;
        photos.persist(photo);

        String url = storage.presignUpload(key, photo.contentType, photo.fileSize);
        return new PhotoUploadResponse(
                photo.id,
                key,
                url,
                PaintingStorage.UPLOAD_URL_SECONDS,
                Map.of("Content-Type", photo.contentType)
        );
    }

    @Transactional
    public PaintingResponse completePhoto(UUID paintingId, UUID photoId) {
        PaintingPhoto photo = requirePhoto(paintingId, photoId);
        if (!photo.uploaded) {
            var object = storage.verifyUploadedObject(photo.objectKey);
            if (object.contentLength() != photo.fileSize || !photo.contentType.equals(object.contentType())) {
                throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.upload.invalid");
            }
            photo.uploaded = true;
        }
        return toResponse(photo.painting);
    }

    @Transactional
    public PaintingResponse reorderPhotos(UUID paintingId, PhotoOrderRequest request) {
        Painting painting = requirePaintingForUpdate(paintingId);
        List<PaintingPhoto> uploaded = painting.photos.stream().filter(photo -> photo.uploaded).toList();
        Set<UUID> requestedIds = new HashSet<>(request.photoIds());
        Set<UUID> currentIds = uploaded.stream().map(photo -> photo.id).collect(java.util.stream.Collectors.toSet());
        if (requestedIds.size() != request.photoIds().size() || !requestedIds.equals(currentIds)) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.order.invalid");
        }
        List<PaintingPhoto> ordered = new ArrayList<>(request.photoIds().size());
        for (UUID id : request.photoIds()) {
            ordered.add(uploaded.stream().filter(photo -> photo.id.equals(id)).findFirst().orElseThrow());
        }
        int offset = painting.photos.size() + MAX_PHOTOS;
        painting.photos.forEach(photo -> photo.sortOrder += offset);
        entityManager.flush();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).sortOrder = index;
        }
        int pendingOrder = ordered.size();
        for (PaintingPhoto photo : painting.photos) {
            if (!photo.uploaded) {
                photo.sortOrder = pendingOrder++;
            }
        }
        return toResponse(painting);
    }

    @Transactional
    public PaintingResponse deletePhoto(UUID paintingId, UUID photoId) {
        Painting painting = requirePaintingForUpdate(paintingId);
        PaintingPhoto photo = requirePhoto(paintingId, photoId);
        storage.deleteObject(photo.objectKey);
        painting.photos.remove(photo);

        int offset = painting.photos.size() + MAX_PHOTOS;
        painting.photos.forEach(remaining -> remaining.sortOrder += offset);
        entityManager.flush();
        for (int index = 0; index < painting.photos.size(); index++) {
            painting.photos.get(index).sortOrder = index;
        }
        return toResponse(painting);
    }

    private void validateImage(PhotoUploadRequest request) {
        if (!ALLOWED_IMAGE_TYPE.matcher(request.contentType()).matches()) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.content-type.invalid");
        }
        if (request.fileSize() > PaintingStorage.MAX_PHOTO_BYTES) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.size.invalid");
        }
    }

    private Painting requirePainting(UUID id) {
        return paintings.findByIdOptional(id).orElseThrow(this::notFound);
    }

    private Painting requirePaintingForUpdate(UUID id) {
        Painting painting = entityManager.find(Painting.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (painting == null) {
            throw notFound();
        }
        return painting;
    }

    private void ensureNoActiveAuction(UUID paintingId) {
        java.time.Instant now = java.time.Instant.now();
        if (auctions.findActiveForPainting(paintingId, now).isPresent()) {
            throw new DomainException(Response.Status.CONFLICT, "error.painting.auction.active");
        }
        boolean winnerPaymentWindow = auctions.findLatestForPainting(paintingId)
                .filter(auction -> auction.endsAt.plus(java.time.Duration.ofHours(24)).isAfter(now))
                .filter(auction -> bids.countByAuction(auction.id) > 0)
                .isPresent();
        if (winnerPaymentWindow) {
            throw new DomainException(Response.Status.CONFLICT, "error.painting.auction.payment-window");
        }
        if (checkoutReservations.isReserved(paintingId)) {
            throw new DomainException(Response.Status.CONFLICT, "error.painting.checkout.active");
        }
    }

    private PaintingPhoto requirePhoto(UUID paintingId, UUID photoId) {
        return photos.findByIdOptional(photoId)
                .filter(photo -> photo.painting.id.equals(paintingId))
                .orElseThrow(() -> new DomainException(Response.Status.NOT_FOUND, "error.photo.not-found"));
    }

    private DomainException notFound() {
        return new DomainException(Response.Status.NOT_FOUND, "error.painting.not-found");
    }

    private PaintingResponse toResponse(Painting painting) {
        List<PaintingPhotoResponse> imageResponses = painting.photos.stream()
                .filter(photo -> photo.uploaded)
                .sorted(java.util.Comparator.comparingInt(photo -> photo.sortOrder))
                .map(photo -> PaintingPhotoResponse.from(photo, storage.presignRead(photo.objectKey)))
                .toList();
        return PaintingResponse.from(painting, currency(), imageResponses);
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

    private void apply(Painting painting, PaintingRequest request) {
        painting.title = request.title().trim();
        painting.description = request.description().trim();
        painting.medium = request.medium().trim();
        painting.widthCm = request.widthCm();
        painting.heightCm = request.heightCm();
        painting.artworkYear = request.artworkYear();
        painting.priceMinor = request.priceMinor();
        painting.status = request.status();
    }

    private void appendRange(
            StringBuilder query,
            Map<String, Object> parameters,
            String field,
            String parameter,
            Object value,
            String operator
    ) {
        if (value != null) {
            query.append(" and ").append(field).append(' ').append(operator).append(" :").append(parameter);
            parameters.put(parameter, value);
        }
    }
}
