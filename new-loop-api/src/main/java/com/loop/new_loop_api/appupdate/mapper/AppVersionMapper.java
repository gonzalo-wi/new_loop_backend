package com.loop.new_loop_api.appupdate.mapper;

import com.loop.new_loop_api.appupdate.dto.AppVersionResponse;
import com.loop.new_loop_api.appupdate.entity.AppVersion;
import org.springframework.stereotype.Component;

@Component
public class AppVersionMapper {

    public AppVersionResponse toResponse(AppVersion version, String baseUrl) {
        return AppVersionResponse.builder()
                .latestVersion(version.getVersion())
                .apkUrl(baseUrl + "/app/download/" + version.getApkFileName())
                .mandatory(version.getMandatory())
                .notes(version.getNotes())
                .build();
    }
}
