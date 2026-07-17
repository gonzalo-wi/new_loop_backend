package com.loop.new_loop_api.appupdate.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

public class ApkFileNotFoundException extends NotFoundException {
    public ApkFileNotFoundException(String fileName) {
        super("APK file not found: " + fileName);
    }
}
