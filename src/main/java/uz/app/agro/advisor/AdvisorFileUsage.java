package uz.app.agro.advisor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: rasm qaysi AI maslahat so'roviniki */
@Component
@RequiredArgsConstructor
class AdvisorFileUsage implements FileUsageProvider {

    private final AdvisorRequestRepository requests;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return requests.fileUses(hashIds).stream().collect(Collectors.groupingBy(AdvisorRequestRepository.FileUseRow::getHashId,
                Collectors.mapping(r -> new FileUsage("ADVISOR", r.getId(), "AI maslahat №" + r.getId()), Collectors.toList())));
    }
}
