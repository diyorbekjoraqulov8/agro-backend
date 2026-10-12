package uz.app.agro.feedback;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uz.app.agro.feedback.dto.FeedbackResponse;
import uz.app.agro.feedback.dto.MessageResponse;
import uz.app.agro.file.dto.FileResponse;

import java.util.Map;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FeedbackMapper {

    @Mapping(target = "farmerId", source = "farmer.id")
    @Mapping(target = "farmerName", source = "farmer.fullName")
    @Mapping(target = "farmerUsername", source = "farmer.telegramUsername")
    @Mapping(target = "farmerLanguage", source = "farmer.language")
    FeedbackResponse toResponse(FeedbackEntity f);

    /** @param files hashId → fayl; xabarlar ro'yxati uchun bitta so'rovda olinadi (N+1 emas) */
    @Mapping(target = "file", expression = "java(m.getFileHashId() == null ? null : files.get(m.getFileHashId()))")
    @Mapping(target = "photoPending", expression = "java(m.getFileHashId() == null && m.getPhotoFileId() != null)")
    MessageResponse toResponse(FeedbackMessageEntity m, @Context Map<String, FileResponse> files);
}
