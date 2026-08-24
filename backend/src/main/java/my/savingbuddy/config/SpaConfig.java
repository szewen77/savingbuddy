package my.savingbuddy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React app from the JAR and hands client-side routes back to it.
 *
 * <p>The router owns paths like {@code /goals}, which exist in the browser but not on
 * disk. Without this, opening or refreshing one directly would 404. Requests under
 * {@code /api} are left alone so real controllers — and real 404s — still work.
 *
 * <p>Inert when the frontend was not bundled (a {@code -DskipFrontend} build).
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {
    private static final String STATIC_ROOT = "classpath:/static/";
    private static final String INDEX = "/static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations(STATIC_ROOT)
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requested = location.createRelative(resourcePath);
                    if (requested.exists() && requested.isReadable()) return requested;

                    // Never let a mistyped API path resolve to the HTML shell.
                    if (resourcePath.startsWith("api/")) return null;

                    Resource index = new ClassPathResource(INDEX);
                    return index.exists() ? index : null;
                }
            });
    }
}
