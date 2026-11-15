package com.robert.gallery.storage;

import com.robert.gallery.api.TestJwtKeyResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class PaintingStorageTest {

    @Inject
    PaintingStorage storage;

    @Test
    void createsShortLivedPrivateUploadAndReadUrls() {
        String key = "paintings/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        URI upload = URI.create(storage.presignUpload(key, "image/jpeg", 1024));
        URI read = URI.create(storage.presignRead(key));

        assertTrue(upload.getRawQuery().contains("X-Amz-Signature="));
        assertTrue(upload.getRawQuery().contains("X-Amz-Expires=300"));
        assertTrue(upload.getPath().endsWith(key));
        assertTrue(read.getRawQuery().contains("X-Amz-Signature="));
        assertTrue(read.getRawQuery().contains("X-Amz-Expires=900"));
        assertTrue(read.getPath().endsWith(key));
        assertEquals("https", upload.getScheme());
    }
}
