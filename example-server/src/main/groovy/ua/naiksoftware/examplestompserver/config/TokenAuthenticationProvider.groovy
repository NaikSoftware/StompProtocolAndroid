package ua.naiksoftware.examplestompserver.config

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import ua.naiksoftware.examplestompserver.service.UserAuthService

/**
 * Created by serebryakov_a on 21.01.2016.
 */

class TokenAuthenticationProvider implements AuthenticationProvider {

    private UserAuthService userAuthService

    public TokenAuthenticationProvider(UserAuthService userAuthService) {
        this.userAuthService = userAuthService
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        AuthToken tokenAuthentication = authentication as AuthToken

        String token = (String) tokenAuthentication.getCredentials();
        UserDetails userDetails = userAuthService.loadUserByUsername(token);
        if (userDetails == null) {
            throw new UsernameNotFoundException("Unknown token");
        }

        authentication.setAuthenticated(true);
        tokenAuthentication.setDetails(userDetails);

        return authentication;
    }

    @Override
    boolean supports(Class<?> authClass) {
        return AuthToken.class == authClass;
    }
}
