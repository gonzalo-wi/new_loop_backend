package com.loop.new_loop_api.routes.repository;

import com.loop.new_loop_api.routes.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    boolean existsByCode(String code);

    /**
     * Routes whose code has this number once letters are ignored ("Rto 179" for 179); see RouteNumber.
     * The CASE keeps the cast from failing on codes with no digits or too many of them.
     */
    @Query(
            value = """
                    SELECT r.* FROM routes r
                    WHERE CASE
                              WHEN length(regexp_replace(r.code, '[^0-9]', '', 'g')) BETWEEN 1 AND 18
                              THEN CAST(regexp_replace(r.code, '[^0-9]', '', 'g') AS BIGINT)
                          END = :number
                    """,
            nativeQuery = true)
    List<Route> findByRouteNumber(@Param("number") long number);

    /** Route counts grouped by branch and active flag, so summaries don't load every route. */
    @Query("""
            select new com.loop.new_loop_api.routes.repository.RouteBranchCount(b.id, b.name, r.active, count(r))
            from Route r join r.branch b
            group by b.id, b.name, r.active
            """)
    List<RouteBranchCount> countByBranchAndActive();

    /**
     * Optional case-insensitive search over code, truck plate, branch and driver, ordered naturally
     * so numeric suffixes sort as numbers ("Rto 2" before "Rto 10"). The Pageable's own sort is
     * ignored on purpose: ordering lives in this native query. Pass {@code null} search to list all.
     */
    @Query(
            value = """
                    SELECT r.* FROM routes r
                    LEFT JOIN branches b ON r.branch_id = b.id
                    LEFT JOIN users d ON r.driver_id = d.id
                    WHERE (CAST(:search AS text) IS NULL
                           OR r.code ILIKE '%' || :search || '%'
                           OR r.truck_plate ILIKE '%' || :search || '%'
                           OR b.name ILIKE '%' || :search || '%'
                           OR b.code ILIKE '%' || :search || '%'
                           OR d.name ILIKE '%' || :search || '%')
                    ORDER BY regexp_replace(lower(r.code), '[0-9]', '', 'g') ASC,
                             COALESCE(NULLIF(regexp_replace(r.code, '[^0-9]', '', 'g'), '')::numeric, 0) ASC,
                             lower(r.code) ASC
                    """,
            countQuery = """
                    SELECT count(*) FROM routes r
                    LEFT JOIN branches b ON r.branch_id = b.id
                    LEFT JOIN users d ON r.driver_id = d.id
                    WHERE (CAST(:search AS text) IS NULL
                           OR r.code ILIKE '%' || :search || '%'
                           OR r.truck_plate ILIKE '%' || :search || '%'
                           OR b.name ILIKE '%' || :search || '%'
                           OR b.code ILIKE '%' || :search || '%'
                           OR d.name ILIKE '%' || :search || '%')
                    """,
            nativeQuery = true)
    Page<Route> searchRoutes(@Param("search") String search, Pageable pageable);

    /** The routes a driver drives today (spec 007). */
    List<Route> findByDriverIdAndActiveTrueOrderByCodeAsc(UUID driverId);

    /**
     * One route of that driver, only if active (spec 009). Missing, someone else's, unassigned and inactive all
     * come back empty, so the caller cannot tell them apart and other drivers' routes are not revealed.
     */
    Optional<Route> findByIdAndDriverIdAndActiveTrue(UUID id, UUID driverId);
}
