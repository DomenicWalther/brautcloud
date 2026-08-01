package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByUser(User user);

	List<Event> findByDeletionRequestedTrue();

	Optional<Event> findFirstByUserOrderByCreatedAtAsc(User user);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Event e where e.id = :id")
	Optional<Event> findByIdForUpdate(@Param("id") UUID id);

	@Modifying
	@Query("UPDATE Event e SET e.viewCount = e.viewCount + 1 WHERE e.id = :id")
	int incrementViewCount(@Param("id") UUID id);

}
