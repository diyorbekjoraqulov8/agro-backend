package uz.app.agro.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Usiz @Scheduled jimgina ishlamaydi (CLAUDE.md 5.1 — "annotatsiya yozilgan, lekin ishlamaydi")
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
