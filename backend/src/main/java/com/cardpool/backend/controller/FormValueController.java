package com.cardpool.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.config.CardPoolProperties;
import com.cardpool.backend.dto.Catalog;
import com.cardpool.backend.dto.EffectFragment;
import com.cardpool.backend.dto.SetDTO;
import com.cardpool.backend.source.CardSource;

/** Values the generator form offers, taken from the card source. */
@RestController
@RequestMapping("/api/form")
public class FormValueController {

    private final CardSource cardSource;
    private final CardPoolProperties properties;

    public FormValueController(CardSource cardSource, CardPoolProperties properties) {
        this.cardSource = cardSource;
        this.properties = properties;
    }

    /** Effect fragments ability rules can match by id (empty for sources without ids). */
    @GetMapping("/fragments")
    public List<EffectFragment> fragments() {
        return cardSource.fragments();
    }

    @GetMapping("/formValues")
    public Catalog getFormValues() {
        Catalog catalog = cardSource.catalog();
        return new Catalog(catalog.factions(),
                catalog.sets().stream()
                        .map(s -> new SetDTO(s.reference(), s.name(),
                                properties.defaultSetWeights().getOrDefault(s.reference(), 0.0)))
                        .toList(),
                catalog.subtypes());
    }
}
