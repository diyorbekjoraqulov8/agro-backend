package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: fayl qaysi murojaat suhbatida yuborilgan */
@Component
@RequiredArgsConstructor
class FeedbackFileUsage implements FileUsageProvider {

    private final FeedbackMessageRepository messages;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return messages.fileUses(hashIds).stream().collect(Collectors.groupingBy(FeedbackMessageRepository.FileUseRow::getHashId,
                Collectors.mapping(r -> new FileUsage("FEEDBACK", r.getFeedbackId(), "Murojaat №" + r.getFeedbackId() + " · "
                        + (r.getFarmerName() != null ? r.getFarmerName()
                        : r.getFarmerUsername() != null ? "@" + r.getFarmerUsername() : "fermer")), Collectors.toList())));
    }
}
