package uz.app.agro.file;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// O'zgarmas yozuv: fayl almashtirilmaydi — yangisi yuklanadi (hash_id bir marta beriladi)
@Entity
@Table(name = "files")
@Getter
@Setter
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash_id", nullable = false, length = 32, updatable = false)
    private String hashId;

    /** Papkaga nisbiy yo'l — HECH QACHON tashqariga chiqmaydi */
    @Column(nullable = false, length = 255, updatable = false)
    private String path;

    @Column(name = "original_name", nullable = false, length = 255, updatable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(updatable = false)
    private Integer width;

    @Column(updatable = false)
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private FileSource source;

    @Column(name = "uploaded_by_id", updatable = false)
    private Long uploadedById;

    @Column(name = "uploaded_by_email", length = 120, updatable = false)
    private String uploadedByEmail;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();
}
