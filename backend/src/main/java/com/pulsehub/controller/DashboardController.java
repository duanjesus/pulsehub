package com.pulsehub.controller;

import com.pulsehub.dto.response.DashboardResponse;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(dashboardService.getDashboard(userId));
    }

}
