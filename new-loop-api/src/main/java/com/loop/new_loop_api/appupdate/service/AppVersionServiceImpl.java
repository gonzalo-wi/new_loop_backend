package com.loop.new_loop_api.appupdate.service;

import com.loop.new_loop_api.appupdate.dto.AppVersionResponse;
import com.loop.new_loop_api.appupdate.entity.AppVersion;
import com.loop.new_loop_api.appupdate.exception.ApkFileNotFoundException;
import com.loop.new_loop_api.appupdate.exception.AppVersionNotFoundException;
import com.loop.new_loop_api.appupdate.exception.InvalidApkUploadException;
import com.loop.new_loop_api.appupdate.mapper.AppVersionMapper;
import com.loop.new_loop_api.appupdate.repository.AppVersionRepository;
import com.loop.new_loop_api.appupdate.service.iService.AppVersionService;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    private final AppVersionRepository appVersionRepository;
    private final AppVersionMapper     appVersionMapper;
    private final AuditService         auditService;

    @Value("${app.apk.storage-path}")
    private String storagePathProperty;

    @Override
    @Transactional(readOnly = true)
    public AppVersionResponse getLatestVersion(String baseUrl) {
        var version = appVersionRepository.findTopByOrderByCreatedAtDesc()
                .orElseThrow(AppVersionNotFoundException::new);
        return appVersionMapper.toResponse(version, baseUrl);
    }

    @Override
    @Transactional
    public AppVersionResponse publishVersion(String versionNumber, boolean mandatory, String notes,
                                             MultipartFile file, String baseUrl) {
        validateVersionNumber(versionNumber);
        validateApkFile(file);
        var fileName = storeApkFile(versionNumber, file);

        var entity = AppVersion.builder()
                .version(versionNumber)
                .apkFileName(fileName)
                .mandatory(mandatory)
                .notes(notes)
                .build();
        var saved    = appVersionRepository.save(entity);
        var response = appVersionMapper.toResponse(saved, baseUrl);
        auditService.register("PUBLISH_APP_VERSION", "AppVersion", saved.getId(), null, response);
        return response;
    }

    @Override
    public Resource loadApkFile(String fileName) {
        try {
            var file = storagePath().resolve(fileName).normalize();
            if (!file.startsWith(storagePath()) || !Files.exists(file)) {
                throw new ApkFileNotFoundException(fileName);
            }
            return new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new ApkFileNotFoundException(fileName);
        }
    }

    private void validateVersionNumber(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new InvalidApkUploadException("Version must follow the x.y.z format (e.g. 1.2.0)");
        }
    }

    private void validateApkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidApkUploadException("An APK file is required");
        }
        var originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.toLowerCase().endsWith(".apk")) {
            throw new InvalidApkUploadException("The uploaded file must be a .apk");
        }
    }

    /** Stores the file as loop-{version}.apk, overwriting a previous upload of the same version. */
    private String storeApkFile(String versionNumber, MultipartFile file) {
        try {
            var directory = storagePath();
            Files.createDirectories(directory);
            var fileName = "loop-" + versionNumber + ".apk";
            file.transferTo(directory.resolve(fileName));
            return fileName;
        } catch (IOException e) {
            throw new InvalidApkUploadException("Could not store the APK file: " + e.getMessage());
        }
    }

    private Path storagePath() {
        return Path.of(storagePathProperty).toAbsolutePath().normalize();
    }
}
