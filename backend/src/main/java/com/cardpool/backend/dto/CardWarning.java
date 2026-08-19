package com.cardpool.backend.dto;

import java.util.List;

public record CardWarning(String reference, String name, List<EffectWarning> warnings) {
}
