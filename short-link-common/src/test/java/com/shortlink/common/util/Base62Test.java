package com.shortlink.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodeShouldPadToSevenChars() {
        assertEquals("0000000", Base62.encode(0));
        assertEquals(7, Base62.encode(1_000_000_000L).length());
    }

    @Test
    void encodeDecodeShouldRoundTrip() {
        for (int i = 0; i < 10_000; i++) {
            long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, 3_500_000_000_000L);
            assertEquals(id, Base62.decode(Base62.encode(id)));
        }
    }

    @Test
    void encodeShouldRejectOutOfRangeId() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(3_521_614_606_208L));
    }

    @Test
    void decodeShouldRejectIllegalInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc"));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("ab!defg"));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc ef1"));
    }

    @Test
    void encodeSequentialIdsShouldBeUnique() {
        Set<String> codes = new HashSet<>();
        for (long id = 1_000_000_000L; id < 1_000_100_000L; id++) {
            codes.add(Base62.encode(id));
        }
        assertEquals(100_000, codes.size());
    }
}
