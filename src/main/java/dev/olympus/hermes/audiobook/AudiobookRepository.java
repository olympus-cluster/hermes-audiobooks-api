package dev.olympus.hermes.audiobook;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudiobookRepository extends JpaRepository<Audiobook, UUID> {

	/**
	 * {@code pattern} must already be LIKE-escaped (with {@code \}) and wrapped in {@code %}.
	 * Both sides are case-folded in PostgreSQL 18 with the ICU collation: the database's C locale only folds ASCII,
	 * and lower-casing (in Java or SQL) turns a word-final Greek "Σ" into "ς", which then never matches "σ".
	 */
	@Query("""
			select a from Audiobook a
			where function('casefold' as String, collate(a.title as `und-x-icu`))
			        like function('casefold' as String, collate(:pattern as `und-x-icu`)) escape '\\'
			   or function('casefold' as String, collate(a.author as `und-x-icu`))
			        like function('casefold' as String, collate(:pattern as `und-x-icu`)) escape '\\'
			""")
	Page<Audiobook> search(@Param("pattern") String pattern, Pageable pageable);
}
