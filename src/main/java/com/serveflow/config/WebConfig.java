package com.serveflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * WebConfig — customizes Spring MVC configuration.
 *
 * Here we map the URL path /uploads/** to a physical directory on the server
 * (a folder named "uploads" in the root of the project).
 * This allows the biller to upload new food item images at runtime and have
 * them served immediately, without needing to restart the server or rebuild
 * the static resources jar.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ensure the directory exists
        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // Map requests to /uploads/** to the local physical folder
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
