package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	@Query("select u from User u where lower(u.email) = lower(:email) order by u.createdAt asc, u.id asc")
	List<User> findAllByEmailIgnoreCaseOrderByCreatedAtAscIdAsc(@Param("email") String email);

	@Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
	boolean existsByEmailCaseInsensitive(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.email = :email")
	Optional<User> findForUpdateByEmail(@Param("email") String email);

	default Optional<User> findPreferredByEmail(String email) {
		return findByEmail(email)
			.or(() -> findAllByEmailIgnoreCaseOrderByCreatedAtAscIdAsc(email).stream().findFirst());
	}

}
