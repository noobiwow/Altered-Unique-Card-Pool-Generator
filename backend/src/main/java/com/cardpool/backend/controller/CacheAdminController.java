package com.cardpool.backend.controller;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.service.CardCacheService;

@RestController
@RequestMapping("/admin/cache")
public class CacheAdminController {

    private final CardCacheService cardCacheService;

    public CacheAdminController(CardCacheService cardCacheService) {
        this.cardCacheService = cardCacheService;
    }

    @PostMapping("/refresh")
    public void refreshDat(@RequestParam("path") String path) throws IOException {
        cardCacheService.loadFromUniquesFolder(Path.of(path));
    }
}
