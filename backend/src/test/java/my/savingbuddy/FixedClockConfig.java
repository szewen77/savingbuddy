package my.savingbuddy;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Pins "now" to Saturday 22 August 2026, 10:30 in Kuala Lumpur — the day the design mockup depicts. */
@TestConfiguration
public class FixedClockConfig {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    public static final Instant NOW = Instant.parse("2026-08-22T02:30:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW, ZONE);
    }
}
