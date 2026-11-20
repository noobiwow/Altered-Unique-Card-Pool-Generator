package com.cardpool.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vue app's {@code index.html} for its client-side routes when the
 * built frontend is bundled in the jar ({@code classpath:/static}). In
 * development the Vite dev server handles these routes instead.
 */
@Controller
public class SpaController {

    @GetMapping({ "/generate", "/pool", "/report/{id}" })
    public String index() {
        return "forward:/index.html";
    }
}
