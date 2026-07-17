package com.loop.new_loop_api.appupdate.service.iService;

import com.loop.new_loop_api.appupdate.dto.AppVersionResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AppVersionService {

    AppVersionResponse getLatestVersion(String baseUrl);

    AppVersionResponse publishVersion(String version, boolean mandatory, String notes,
                                      MultipartFile file, String baseUrl);

    Resource loadApkFile(String fileName);
}
