package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	@Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	default Optional<RefreshToken> findByToken(String token) {
		return findByTokenHash(RefreshToken.hashToken(token));
	}

	default Optional<RefreshToken> findByTokenForUpdate(String token) {
		return findByTokenHashForUpdate(RefreshToken.hashToken(token));
	}

	@Modifying
	@Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
	void deleteByUser(User user);

}
