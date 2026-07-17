package com.loop.new_loop_api.appupdate.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AppVersionResponse {
    private String  latestVersion;
    private String  apkUrl;
    private Boolean mandatory;
    private String  notes;
}
