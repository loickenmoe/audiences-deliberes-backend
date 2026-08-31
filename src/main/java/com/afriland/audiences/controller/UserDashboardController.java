package com.afriland.audiences.controller;

import com.afriland.audiences.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/dashboard") // change "user" in endpoint depending on role
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')") // Ensure only USER can access
public class UserDashboardController {

    private final DashboardService dashboardService;

}