package my.savingbuddy;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * Base for full-stack API tests.
 *
 * <p>Each test class gets its own database. Two things are needed for that, and the
 * second is easy to miss:
 *
 * <ul>
 *   <li>A unique in-memory database per context — {@code DB_CLOSE_DELAY=-1} keeps H2
 *       alive for the whole JVM, so a shared name means shared data.</li>
 *   <li>A context that is not shared in the first place. Spring caches contexts by
 *       configuration, so two classes with the same profiles and annotations get the
 *       <em>same</em> context — and therefore the same database, unique name or not.
 *       {@code @DirtiesContext} closes it after each class so the next one rebuilds.</li>
 * </ul>
 *
 * <p>Within a single class the context is reused, so ordered tests can build on each
 * other deliberately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApiTestBase {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String name = "sb-" + UUID.randomUUID();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
}
