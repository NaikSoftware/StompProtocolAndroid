package ua.naiksoftware.examplestompserver.config

import org.springframework.security.authentication.AbstractAuthenticationToken
import ua.naiksoftware.examplestompserver.UserPrincipal

/**
 * Created by naik
 */
class AuthToken extends AbstractAuthenticationToken {

    private String token;
    private Long userId

    AuthToken(String token) {
        super(null)
        this.token = token
    }

    void setDetails(Object details) {
        if (details instanceof UserPrincipal) {
            userId = details.userId
        }
        super.setDetails(details)
    }

    @Override
    Object getCredentials() {
        return token
    }

    @Override
    String getName() {
        return userId as String // return unique identifier
    }

    @Override
    Object getPrincipal() {
        return getDetails()
    }

    void eraseCredentials() {
        super.eraseCredentials()
        token = null
    }
}
