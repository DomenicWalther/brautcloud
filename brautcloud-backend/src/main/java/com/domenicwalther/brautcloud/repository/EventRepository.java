package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

}
