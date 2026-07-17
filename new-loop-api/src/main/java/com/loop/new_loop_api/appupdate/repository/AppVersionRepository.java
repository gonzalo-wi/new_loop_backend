package com.loop.new_loop_api.appupdate.repository;

import com.loop.new_loop_api.appupdate.entity.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppVersionRepository extends JpaRepository<AppVersion, UUID> {

    Optional<AppVersion> findTopByOrderByCreatedAtDesc();
}
