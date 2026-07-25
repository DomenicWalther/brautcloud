package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByUser(User user);

	Optional<Event> findFirstByUserOrderByCreatedAtAsc(User user);

	@Modifying
	@Query("UPDATE Event e SET e.viewCount = e.viewCount + 1 WHERE e.id = :id")
	int incrementViewCount(@Param("id") UUID id);

}
