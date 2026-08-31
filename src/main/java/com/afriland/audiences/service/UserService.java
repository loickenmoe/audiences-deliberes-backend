package com.afriland.audiences.service;

import com.afriland.audiences.model.security.Role;
import com.afriland.audiences.model.security.User;

import java.util.List;

public interface UserService {

    User createUser(User user);

    User updateUser(Long userId, User user);

    void deleteUser(Long userId);

    User getUserById(Long userId);

    List<User> getAllUsers();

    List<User> getUsersByRole(Role role);

    // Potentially add methods for password reset, enabling/disabling users etc.
}