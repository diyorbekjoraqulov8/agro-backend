package uz.app.agro.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID    = "userId";

    private static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = sanitize(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();                     // 🔴 SHART — sababi pastda
        }
    }

    /** Faqat harf/raqam/chiziqcha, cheklangan uzunlik. Boshqasi — rad etiladi. */
    private String sanitize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) return null;
        return value.matches("[A-Za-z0-9._-]+") ? value : null;
    }
}