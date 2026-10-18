package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: rasm qaysi dalaning "Bugungi rasm"i */
@Component
@RequiredArgsConstructor
class FieldSnapshotFileUsage implements FileUsageProvider {

    private final FieldSnapshotRepository snapshots;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return snapshots.findByFileHashIdIn(hashIds).stream().collect(Collectors.groupingBy(FieldSnapshotEntity::getFileHashId,
                Collectors.mapping(s -> new FileUsage("FIELD", s.getFieldId(), "Bugungi rasm · dala #" + s.getFieldId() + " · " + s.getSceneDate()),
                        Collectors.toList())));
    }
}
