package io.github.usmanovmahmudkhan.realtimechat.core.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        long timestamp = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | RANDOM.nextInt(1 << 12);
        long leastSignificantBits = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
