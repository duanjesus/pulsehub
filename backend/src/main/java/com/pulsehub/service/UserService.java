package com.pulsehub.service;

import com.pulsehub.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> listContacts(Long currentUserId);
}
