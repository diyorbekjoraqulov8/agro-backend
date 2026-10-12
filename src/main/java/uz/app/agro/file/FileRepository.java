package uz.app.agro.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    interface StatsRow {
        long getTotal();
        long getTotalBytes();
        long getImages();
        long getPdfs();
        long getFromTelegram();
        long getFromStaff();
    }

    @Query(value = """
            SELECT count(*)                                              AS total,
                   coalesce(sum(size_bytes), 0)                          AS totalBytes,
                   count(*) FILTER (WHERE content_type LIKE 'image/%')   AS images,
                   count(*) FILTER (WHERE content_type = 'application/pdf') AS pdfs,
                   count(*) FILTER (WHERE source = 'TELEGRAM')           AS fromTelegram,
                   count(*) FILTER (WHERE source = 'STAFF')              AS fromStaff
            FROM files
            """, nativeQuery = true)
    StatsRow stats();

    Optional<FileEntity> findByHashId(String hashId);

    List<FileEntity> findByHashIdIn(Collection<String> hashIds);
}
