package com.pulsehub.controller;

import com.pulsehub.dto.request.ChangePasswordRequest;
import com.pulsehub.dto.request.UpdateProfileRequest;
import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<UserResponse>> listContacts() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userService.listContacts(currentUserId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userService.updateName(userId, request.name()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public ResponseEntity<UserResponse> updateAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(userService.updateAvatar(userId, file));
    }

}
