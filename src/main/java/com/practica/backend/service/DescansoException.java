package com.practica.backend.service;

import org.springframework.http.HttpStatus;

public class DescansoException extends RuntimeException {

    private final HttpStatus status;

    public DescansoException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
