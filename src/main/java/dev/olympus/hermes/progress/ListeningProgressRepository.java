package dev.olympus.hermes.progress;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListeningProgressRepository extends JpaRepository<ListeningProgress, UUID> {
}
