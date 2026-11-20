package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.rule.EffectRule;

public record RulePreviewRequest(EffectRule rule, List<Card> cards) {
}
