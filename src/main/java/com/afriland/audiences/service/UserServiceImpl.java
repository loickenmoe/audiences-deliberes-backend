package com.afriland.audiences.service;

import com.afriland.audiences.model.security.Role;
import com.afriland.audiences.model.security.User;
import com.afriland.audiences.repository.security.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Needed for encoding password on create/update

    @Override
    @Transactional
    public User createUser(User user) {
        // Basic validation (more robust validation can be added)
        Objects.requireNonNull(user, "User cannot be null");
        if (!StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("Password cannot be empty for new user");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + user.getEmail());
        }
//         Add username check if username is intended to be unique and is provided
         if (StringUtils.hasText(user.getUsername()) && userRepository.findByUsername(user.getUsername()).isPresent()) {
             throw new IllegalArgumentException("Username already exists: " + user.getUsername());
         }

        // Encode password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setId(null); // Ensure ID is null for creation
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userUpdates) {
        Objects.requireNonNull(userId, "User ID cannot be null for update");
        Objects.requireNonNull(userUpdates, "User updates cannot be null");

        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId)); // Throws exception if not found

        // Update mutable fields (prevent changing email/username for now, handle password separately)
        existingUser.setFirstName(userUpdates.getFirstName());
        existingUser.setLastName(userUpdates.getLastName());
        existingUser.setRole(userUpdates.getRole());
        // Add other updatable fields here (e.g., enabled status)

        // Handle password update only if a new password is provided
        if (StringUtils.hasText(userUpdates.getPassword())) {
            existingUser.setPassword(passwordEncoder.encode(userUpdates.getPassword()));
        }

        return userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with ID: " + userId);
        }
        // Consider adding checks (e.g., cannot delete the last admin)
        userRepository.deleteById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersByRole(Role role) {
        Objects.requireNonNull(role, "Role cannot be null for filtering");
        return userRepository.findByRole(role);
    }
}