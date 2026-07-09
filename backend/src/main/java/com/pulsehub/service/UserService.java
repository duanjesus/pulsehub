package com.pulsehub.service;

import com.pulsehub.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    List<UserResponse> listContacts(Long currentUserId);

    UserResponse getProfile(Long userId);

    UserResponse updateName(Long userId, String name);

    void changePassword(Long userId, String currentPassword, String newPassword);

    UserResponse updateAvatar(Long userId, MultipartFile file);
}
