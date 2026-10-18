package dev.olympus.hermes.chapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

	List<Chapter> findByAudiobookIdOrderByPositionAsc(UUID audiobookId);

	Optional<Chapter> findByIdAndAudiobookId(UUID id, UUID audiobookId);

	boolean existsByAudiobookIdAndPosition(UUID audiobookId, int position);

	boolean existsByAudiobookIdAndPositionAndIdNot(UUID audiobookId, int position, UUID id);

	@Query("select coalesce(max(c.position), 0) from Chapter c where c.audiobookId = :audiobookId")
	int maxPosition(@Param("audiobookId") UUID audiobookId);

	@Query("select c.audioKey from Chapter c where c.audiobookId = :audiobookId")
	List<String> findAudioKeys(@Param("audiobookId") UUID audiobookId);
}
