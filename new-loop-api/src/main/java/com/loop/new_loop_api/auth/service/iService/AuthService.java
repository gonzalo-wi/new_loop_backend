package com.loop.new_loop_api.auth.service.iService;

import com.loop.new_loop_api.auth.dto.LoginRequest;
import com.loop.new_loop_api.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
