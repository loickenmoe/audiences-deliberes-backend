package com.afriland.audiences.service.security;

import com.afriland.audiences.model.security.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // TODO: Externalize this secret key and make it much stronger!
    // Generate a secure key, e.g., using: https://www.allkeysgenerator.com/Random/Security-Encryption-Key-Generator
    // Store it securely, e.g., in application properties or environment variables.
    @Value("${application.security.jwt.secret-key:placeholderSecretKeyThatIsVeryLongAndSecure1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz}") // Example placeholder
    private String secretKeyString;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration; // e.g., 86400000 for 24 hours

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration; // e.g., 604800000 for 7 days

    /**
     * Extracts the username (subject) from the JWT token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from the JWT token using a claims resolver function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a JWT token for the given UserDetails.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a JWT token with extra claims for the given UserDetails.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Generates a refresh token for the given UserDetails.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        // Cast UserDetails to our User type to access email
        String email = (userDetails instanceof User) ? ((User) userDetails).getEmail() : userDetails.getUsername();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email) // Use email as subject
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256) // Use the signing key
                .compact();
    }

    /**
     * Validates the JWT token against the UserDetails.
     * Checks if the username matches and the token is not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractUsername(token); // This now extracts the email (subject)
        // Compare extracted email with the email from UserDetails (after casting)
        String userDetailsEmail = (userDetails instanceof User) ? ((User) userDetails).getEmail() : userDetails.getUsername();
        return (email.equals(userDetailsEmail)) && !isTokenExpired(token);
    }

    /**
     * Checks if the JWT token has expired.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from the JWT token.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts all claims from the JWT token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // Verify using the signing key
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generates the signing key from the base64 encoded secret string.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}