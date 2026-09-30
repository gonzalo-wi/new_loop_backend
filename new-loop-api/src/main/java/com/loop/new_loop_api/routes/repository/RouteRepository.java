package com.loop.new_loop_api.routes.repository;

import com.loop.new_loop_api.routes.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    boolean existsByCode(String code);

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
}
