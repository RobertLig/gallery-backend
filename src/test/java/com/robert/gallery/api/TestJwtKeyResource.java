package com.robert.gallery.api;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

public class TestJwtKeyResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();

            Path directory = Path.of("target", "test-jwt").toAbsolutePath();
            Files.createDirectories(directory);
            Path privateKey = directory.resolve("private.pem");
            Path publicKey = directory.resolve("public.pem");
            Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));

            return Map.of(
                    "mp.jwt.verify.publickey.location", publicKey.toString(),
                    "mp.jwt.verify.issuer", "gallery-app",
                    "smallrye.jwt.sign.key.location", privateKey.toString(),
                    "smallrye.jwt.sign.algorithm", "RS256"
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Could not create test JWT signing keys", exception);
        }
    }

    @Override
    public void stop() {
    }

    private String pem(String type, byte[] encodedKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encodedKey);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
