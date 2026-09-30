package com.loop.new_loop_api.users.repository;

import com.loop.new_loop_api.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Optional case-insensitive search over name/username, ordered naturally so numeric suffixes
     * sort as numbers ("Rto 2" before "Rto 10", not lexicographically). The Pageable's own sort is
     * ignored on purpose: ordering lives in this native query. Pass {@code null} search to list all.
     */
    @Query(
            value = """
                    SELECT u.* FROM users u
                    WHERE (CAST(:search AS text) IS NULL
                           OR u.name ILIKE '%' || :search || '%'
                           OR u.username ILIKE '%' || :search || '%')
                    ORDER BY regexp_replace(lower(u.name), '[0-9]', '', 'g') ASC,
                             COALESCE(NULLIF(regexp_replace(u.name, '[^0-9]', '', 'g'), '')::numeric, 0) ASC,
                             lower(u.name) ASC
                    """,
            countQuery = """
                    SELECT count(*) FROM users u
                    WHERE (CAST(:search AS text) IS NULL
                           OR u.name ILIKE '%' || :search || '%'
                           OR u.username ILIKE '%' || :search || '%')
                    """,
            nativeQuery = true)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}
