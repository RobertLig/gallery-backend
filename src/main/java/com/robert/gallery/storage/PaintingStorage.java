package com.robert.gallery.storage;

import com.robert.gallery.api.error.DomainException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class PaintingStorage {

    private static final Logger LOG = Logger.getLogger(PaintingStorage.class);
    public static final long MAX_PHOTO_BYTES = 5_000_000L;
    public static final int UPLOAD_URL_SECONDS = 300;
    public static final int READ_URL_SECONDS = 900;

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "bucket.name")
    Optional<String> configuredBucket;

    private volatile S3Presigner presigner;

    public String presignUpload(String key, String contentType, long size) {
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();
            return presigner().presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(UPLOAD_URL_SECONDS))
                    .putObjectRequest(put)
                    .build()).url().toString();
        } catch (SdkException exception) {
            throw storageUnavailable(exception);
        }
    }

    public String presignRead(String key) {
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket()).key(key).build();
            return presigner().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(READ_URL_SECONDS))
                    .getObjectRequest(get)
                    .build()).url().toString();
        } catch (SdkException exception) {
            throw storageUnavailable(exception);
        }
    }

    public HeadObjectResponse verifyUploadedObject(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new DomainException(Response.Status.BAD_REQUEST, "error.photo.upload.missing");
            }
            throw storageUnavailable(exception);
        } catch (SdkException exception) {
            throw storageUnavailable(exception);
        }
    }

    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (SdkException exception) {
            throw storageUnavailable(exception);
        }
    }

    private String bucket() {
        return configuredBucket.filter(value -> !value.isBlank())
                .orElseThrow(() -> new DomainException(
                        Response.Status.SERVICE_UNAVAILABLE,
                        "error.storage.unconfigured"
                ));
    }

    private S3Presigner presigner() {
        S3Presigner current = presigner;
        if (current == null) {
            synchronized (this) {
                current = presigner;
                if (current == null) {
                    var config = s3Client.serviceClientConfiguration();
                    var builder = S3Presigner.builder()
                            .region(config.region() == null ? Region.US_EAST_1 : config.region())
                            .credentialsProvider(config.credentialsProvider())
                            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
                    config.endpointOverride().ifPresent(builder::endpointOverride);
                    presigner = current = builder.build();
                }
            }
        }
        return current;
    }

    private DomainException storageUnavailable(SdkException cause) {
        LOG.error("S3 storage request failed", cause);
        return new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.storage.unavailable");
    }

    @PreDestroy
    void close() {
        if (presigner != null) {
            presigner.close();
        }
    }
}
