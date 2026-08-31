package com.afriland.audiences.service.security;

import com.afriland.audiences.repository.security.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor // Lombok: Constructor injection for final fields
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads the user by username. This method is called by Spring Security during authentication.
     *
     * @param username The username identifying the user whose data is required.
     * @return a fully populated {@link UserDetails} object (never {@code null})
     * @throws UsernameNotFoundException if the user could not be found or the user has no GrantedAuthority
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException { // Parameter name is conventional, but it holds the email now
        return userRepository.findByEmail(email) // Use findByEmail repository method
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email)); // Update exception message
        // The User entity already implements UserDetails, so we can return it directly.
    }
}