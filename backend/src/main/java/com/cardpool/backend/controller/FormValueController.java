package com.cardpool.backend.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cardpool.backend.dto.FactionDTO;
import com.cardpool.backend.dto.SetDTO;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.enums.SetEnum;

@RestController
@RequestMapping("/api/form")
public class FormValueController {

    @GetMapping("/formValues")
    public Map<String, Object> getMeta() {
        return Map.of(
                "factions", getFactions(),
                "sets", getSets());
    }

    @GetMapping("/factions")
    public List<FactionDTO> getFactions() {
        return Arrays.stream(FactionEnum.values())
                .map(f -> new FactionDTO(f.getId(), f.getCode(), f.getName()))
                .toList();
    }

    @GetMapping("/sets")
    public List<SetDTO> getSets() {
        return Arrays.stream(SetEnum.values())
                .map(s -> new SetDTO(s.getReference(), s.getName()))
                .toList();
    }
}
