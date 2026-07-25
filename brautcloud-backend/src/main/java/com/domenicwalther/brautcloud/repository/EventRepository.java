package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByUser(User user);

	Optional<Event> findFirstByUserOrderByCreatedAtAsc(User user);

}
