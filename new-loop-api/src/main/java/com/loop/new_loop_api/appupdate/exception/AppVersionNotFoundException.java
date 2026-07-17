package com.loop.new_loop_api.appupdate.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

public class AppVersionNotFoundException extends NotFoundException {
    public AppVersionNotFoundException() {
        super("No app version has been published yet");
    }
}
