package com.pulsehub.service;

import com.pulsehub.dto.response.DashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboard(Long userId);
}
