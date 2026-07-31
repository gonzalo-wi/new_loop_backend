package com.loop.new_loop_api.users.repository;

import com.loop.new_loop_api.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsername(String username);

    /** Fetches branch eagerly: callers (JWT filter, login) read it outside any open Hibernate session. */
    @Query("select u from User u left join fetch u.branch where u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);
}
