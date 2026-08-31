package com.afriland.audiences.controller;

import com.afriland.audiences.model.security.Role;
import com.afriland.audiences.model.security.User;
import com.afriland.audiences.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
//@PreAuthorize("hasRole('ADMIN')") // Ensure only ADMIN can access these endpoints
public class AdminUserController {

    private final UserService userService; // Inject the user service

    // GET /api/v1/admin/users - List users (optionally filtered by role)
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(@RequestParam(required = false) Role role) {
        List<User> users = (role == null) ? userService.getAllUsers() : userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }

    // GET /api/v1/admin/users/{id} - Get a single user by ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id); // Assumes service throws exception if not found
        return ResponseEntity.ok(user);
    }

    // POST /api/v1/admin/users - Create a new user (TEMPORARY DIAGNOSTIC VERSION)
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) { // Temporarily remove RequestBody
        System.out.println(">>> DIAGNOSTIC: createUser endpoint reached by authenticated user."); // Add log
         User createdUser = userService.createUser(user); // Temporarily comment out service call
         return new ResponseEntity<>(createdUser, HttpStatus.CREATED); // Temporarily comment out original return
    }

    // PUT /api/v1/admin/users/{id} - Update an existing user
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);
    }

    // DELETE /api/v1/admin/users/{id} - Delete a user
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build(); // Standard response for successful delete
    }
}