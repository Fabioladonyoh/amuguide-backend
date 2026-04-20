package com.amuguide.backend.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {

        super(message);
    }
}
