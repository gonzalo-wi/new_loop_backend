package com.loop.new_loop_api.appupdate.controller;

import com.loop.new_loop_api.appupdate.dto.AppVersionResponse;
import com.loop.new_loop_api.appupdate.service.iService.AppVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppVersionController {

    private static final MediaType ANDROID_PACKAGE = MediaType.parseMediaType("application/vnd.android.package-archive");

    private final AppVersionService appVersionService;

    // Public — the mobile app checks this before login.
    @GetMapping("/version")
    public ResponseEntity<AppVersionResponse> getLatestVersion() {
        return ResponseEntity.ok(appVersionService.getLatestVersion(currentBaseUrl()));
    }

    // Publishes a new APK release. Not role-gated yet (same posture as the rest of the API today).
    @PostMapping(value = "/version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppVersionResponse> publishVersion(
            @RequestParam String version,
            @RequestParam(defaultValue = "false") boolean mandatory,
            @RequestParam(required = false) String notes,
            @RequestPart MultipartFile file) {
        var response = appVersionService.publishVersion(version, mandatory, notes, file, currentBaseUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Public direct download — no HTML wrapper, no auth required.
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        var resource = appVersionService.loadApkFile(fileName);
        return ResponseEntity.ok()
                .contentType(ANDROID_PACKAGE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private String currentBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }
}
