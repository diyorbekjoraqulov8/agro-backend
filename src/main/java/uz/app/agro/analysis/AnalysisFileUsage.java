package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: fayl qaysi tahlilning xaritasi/grafigi */
@Component
@RequiredArgsConstructor
class AnalysisFileUsage implements FileUsageProvider {

    private final AnalysisImageRepository images;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return images.fileUses(hashIds).stream().collect(Collectors.groupingBy(AnalysisImageRepository.FileUseRow::getHashId,
                Collectors.mapping(r -> new FileUsage("ANALYSIS", r.getJobId(), "Tahlil №" + r.getJobId()
                        + (r.getFieldName() != null ? " · " + r.getFieldName() : "")), Collectors.toList())));
    }
}
