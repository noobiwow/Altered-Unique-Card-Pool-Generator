package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.verdict.VerdictStatus;

public record VerdictRequest(String name, VerdictStatus status, String note, List<String> reviewedRuleIds) {
}
