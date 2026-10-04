package uz.app.agro.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleApp(AppException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Bu amal uchun huquqingiz yo'q");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Kutilmagan xato", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ichki xatolik yuz berdi"
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email yoki parol noto'g'ri");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "noto'g'ri qiymat");
        }

        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Kiritilgan ma'lumot noto'g'ri");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    /**
     * Spring MVC o'zi tashlaydigan barcha xatolar (tip mos kelmasligi, buzuq JSON,
     * noto'g'ri HTTP metod, topilmagan yo'l, ...) shu yerdan o'tadi.
     * Vazifasi — ota-klass yasagan ProblemDetail'ga `code` va `timestamp` qo'shish.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        // 5xx — bizning aybimiz, stacktrace bilan log'ga. 4xx — mijoz aybi, shovqin qilmasin.
        if (status.is5xxServerError()) {
            log.error("Server xatosi: {}", ex.getMessage(), ex);
        } else {
            log.debug("Mijoz xatosi [{}]: {}", status.value(), ex.getMessage());
        }

        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);

        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            pd.setProperty("code", codeOf(status));
            pd.setProperty("timestamp", Instant.now());
        }
        return response;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Ma'lumot yaxlitligi buzildi: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", "Bunday yozuv allaqachon mavjud");
    }

    /**
     * HTTP statusdan mashina o'qiydigan kod yasaydi:
     * 404 -> "NOT_FOUND", 405 -> "METHOD_NOT_ALLOWED", 415 -> "UNSUPPORTED_MEDIA_TYPE".
     * HttpStatus.resolve() nostandart kodda null qaytaradi — shuning uchun zaxira variant bor.
     */
    private String codeOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : "HTTP_" + status.value();
    }

    private ProblemDetail build(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
