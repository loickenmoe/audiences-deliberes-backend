package com.afriland.audiences.repository.security;

import com.afriland.audiences.model.security.Role;
import com.afriland.audiences.model.security.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their username.
     *
     * @param username The username to search for.
     * @return An Optional containing the user if found, otherwise empty.
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their email address.
     *
     * @param email The email address to search for.
     * @return An Optional containing the user if found, otherwise empty.
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds all users with a specific role.
     *
     * @param role The role to search for.
     * @return A List containing the users with the specified role.
     */
    List<User> findByRole(Role role);

    /**
     * Counts users whose role is one of the specified roles.
     *
     * @param roles A collection of roles to count.
     * @return The number of users matching the roles.
     */
    long countByRoleIn(Collection<Role> roles); // Add countByRoleIn
}