package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.EventGuestVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventGuestVisitRepository extends JpaRepository<EventGuestVisit, UUID> {

	boolean existsByEventIdAndVisitorId(UUID eventId, UUID visitorId);

	long countByEventId(UUID eventId);

}
