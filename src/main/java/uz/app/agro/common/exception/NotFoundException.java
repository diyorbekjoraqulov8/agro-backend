package uz.app.agro.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends AppException{

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " topilmadi: " + id);
    }
}
