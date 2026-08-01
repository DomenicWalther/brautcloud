package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.dto.EventSummary;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByUser(User user);

	@Query("select new com.domenicwalther.brautcloud.dto.EventSummary(e.id, e.eventName, e.location, e.date, e.user.id, "
			+ "e.firstNameCoupleOne, e.firstNameCoupleTwo, e.viewCount, count(v.id), e.password) "
			+ "from Event e left join EventGuestVisit v on v.event.id = e.id "
			+ "where e.user = :user and e.deletionRequested = false "
			+ "group by e.id, e.eventName, e.location, e.date, e.user.id, e.firstNameCoupleOne, "
			+ "e.firstNameCoupleTwo, e.viewCount, e.password, e.createdAt " + "order by e.createdAt asc, e.id asc")
	List<EventSummary> findEventSummariesByUser(@Param("user") User user, Pageable pageable);

	List<Event> findByDeletionRequestedTrue();

	Optional<Event> findFirstByUserOrderByCreatedAtAsc(User user);

	@Modifying
	@Query("UPDATE Event e SET e.viewCount = e.viewCount + 1 WHERE e.id = :id")
	int incrementViewCount(@Param("id") UUID id);

}
