package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Pro sinovi tugashi haqida eslatma — kunduzi (9:50–19:50) har soat; takror yubormaslik TrialService'da */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialReminderJob {

    private final TrialService trials;

    @Scheduled(cron = "${trial.reminder-cron:0 50 9-19 * * *}")
    public void run() {
        try {
            int n = trials.sendReminders(LocalDate.now());
            if (n > 0) log.info("Pro sinovi eslatmasi: {} ta", n);
        } catch (RuntimeException e) {
            log.error("Pro sinovi eslatmalari yuborilmadi", e);
        }
    }
}
