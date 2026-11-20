package com.cardpool.backend.exception;

/** The card source could not be loaded (missing or invalid CardsData folder...). */
public class CardDataUnavailableException extends RuntimeException {

    public CardDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
