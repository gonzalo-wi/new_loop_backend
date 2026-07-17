package com.loop.new_loop_api.appupdate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Each row is a published APK release. The most recent one (by createdAt) is "the latest version". */
@Entity
@Table(name = "app_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "apk_file_name", nullable = false)
    private String apkFileName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean mandatory = false;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
