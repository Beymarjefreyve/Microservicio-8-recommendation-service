package com.microshop.recommendation.ai.exception;

public class InvalidPromptException extends RuntimeException {
    public InvalidPromptException(String message) {
        super(message);
    }
}
