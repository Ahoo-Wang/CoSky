package me.ahoo.cosky.rest.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * for Dashboard-UI
 *
 * @author ahoo wang
 */
@Component
public class DashboardConfig implements ErrorPageRegistrar {
    /**
     * Register pages as required with the given registry.
     *
     * @param registry the error page registry
     */
    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        ErrorPage error404Page = new ErrorPage(HttpStatus.NOT_FOUND, "/index.html");
        registry.addErrorPages(error404Page);
    }
}
