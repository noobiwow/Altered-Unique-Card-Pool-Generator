package com.cardpool.backend.model.verdict;

/** The team decision about a card. A card without a verdict is pending. */
public enum VerdictStatus {
    /** Reviewed and accepted in the pool despite its warnings. */
    APPROVED,
    /** Reviewed and refused: excluded from future generated pools. */
    REJECTED
}
