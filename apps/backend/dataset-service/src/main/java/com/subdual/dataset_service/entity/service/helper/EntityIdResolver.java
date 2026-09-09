package com.subdual.dataset_service.entity.service.helper;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class EntityIdResolver {

    public String resolveScopedEntityId(String rawEntityId, String userId) {
        if (userId == null || userId.isBlank() || rawEntityId == null || rawEntityId.isBlank()) {
            return rawEntityId;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((userId.trim() + ":" + rawEntityId.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
