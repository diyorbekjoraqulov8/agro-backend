package uz.app.agro.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException{
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}

