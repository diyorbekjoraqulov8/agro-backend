package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: o'zgarish xaritasi qaysi dalaning monitoringi */
@Component
@RequiredArgsConstructor
class MonitoringFileUsage implements FileUsageProvider {

    private final MonitoringEventRepository events;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return events.fileUses(hashIds).stream().collect(Collectors.groupingBy(MonitoringEventRepository.FileUseRow::getHashId,
                Collectors.mapping(r -> new FileUsage("FIELD", r.getFieldId(), "Monitoring · " + r.getFieldName() + " · " + r.getSceneDate()),
                        Collectors.toList())));
    }
}
