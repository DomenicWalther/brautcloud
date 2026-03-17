package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByToken(String token);

	@Modifying
	@Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
	void deleteByUser(User user);

}
