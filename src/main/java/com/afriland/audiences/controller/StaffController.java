package com.afriland.audiences.controller;

import com.afriland.audiences.model.security.Role;
import com.afriland.audiences.model.security.User;
import com.afriland.audiences.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff") // New base path for staff-related info
@RequiredArgsConstructor
public class StaffController {

    private final UserService userService;

    // Endpoint specifically for getting users, accessible by multiple roles
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')") // Allow relevant roles
    public ResponseEntity<List<User>> getAllStaff() {
        List<User> staff = userService.getAllUsers();
        // Consider returning a simpler DTO instead of the full User object if needed
        return ResponseEntity.ok(staff);
    }

    // Endpoint specifically for getting users, accessible by multiple roles
    @GetMapping("/{role}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')") // Allow relevant roles
    public ResponseEntity<List<User>> getStaffByRole(@PathVariable Role role) {
        List<User> staff = userService.getUsersByRole(role);
        // Consider returning a simpler DTO instead of the full User object if needed
        return ResponseEntity.ok(staff);
    }

    // Could add endpoints for other roles or staff lists here if needed
}