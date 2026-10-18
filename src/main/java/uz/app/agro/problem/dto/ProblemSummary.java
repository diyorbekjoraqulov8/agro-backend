package uz.app.agro.problem.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Sahifa tepasi: tashqi xizmatlar holati va har manba bo'yicha ochiq muammolar */
public record ProblemSummary(List<ServiceHealth> services, List<SourceCount> sources) {

    public enum Status { UP, WARN, DOWN, OFF }

    /** detail — odam o'qiydigan izoh ("oxirgi prognoz 2 soat oldin", "kalit yo'q") */
    public record ServiceHealth(String name, Status status, String detail) {}

    /** recent — ochiqlardan oxirgi 24 soatda takrorlanganlari */
    public record SourceCount(String source, long open, long recent, LocalDateTime lastSeen) {}
}
