package com.afriland.audiences.controller;


import com.afriland.audiences.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Ensure only ADMIN can access dashboard data
public class AdminDashboardController {
    private final DashboardService dashboardService;
}