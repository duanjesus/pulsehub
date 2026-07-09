package com.pulsehub.controller;

import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
