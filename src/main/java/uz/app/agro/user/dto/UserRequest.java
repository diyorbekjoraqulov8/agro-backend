package uz.app.agro.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(

        @NotBlank(message = "Email bo'sh bo'lmasligi kerak")
        @Email(message = "Email formati noto'g'ri")
        @Size(max = 120)
        String email,

        @NotBlank(message = "Parol bo'sh bo'lmasligi kerak")
        @Size(min = 8, max = 64, message = "Parol 8–64 belgi bo'lishi kerak")
        String password
) {}
