package uz.app.agro.problem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Har kecha: 90 kundan eski hal qilingan muammolar o'chiriladi (jadval cheksiz o'smasin; ochiqlarga tegilmaydi) */
@Slf4j
@Component
@RequiredArgsConstructor
class ProblemCleanupJob {

    private final ProblemRepository problems;

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanup() {
        int n = problems.deleteResolvedBefore(LocalDateTime.now().minusDays(90));
        if (n > 0) log.info("muammolar: {} ta eski (hal qilingan) yozuv o'chirildi", n);
    }
}
