package uz.app.agro.onboarding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileUsageProvider;
import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** "Fayllar" sahifasi uchun: video qaysi tilning yo'riqnomasi */
@Component
@RequiredArgsConstructor
class TutorialVideoFileUsage implements FileUsageProvider {

    private final TutorialVideoRepository videos;

    @Override
    public Map<String, List<FileUsage>> usages(Collection<String> hashIds) {
        return videos.findByFileHashIdIn(hashIds).stream().collect(Collectors.groupingBy(TutorialVideoEntity::getFileHashId,
                Collectors.mapping(v -> new FileUsage("TUTORIAL", null, "Yo'riqnoma videosi (" + v.getLanguage() + ")"),
                        Collectors.toList())));
    }
}
