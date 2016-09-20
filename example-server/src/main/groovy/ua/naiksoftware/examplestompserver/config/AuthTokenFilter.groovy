package ua.naiksoftware.examplestompserver.config

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Created by naik
 */
public class AuthTokenFilter extends AbstractAuthenticationProcessingFilter {

    public AuthTokenFilter(AuthenticationManager authenticationManager)  throws AuthenticationException {
        super(new AntPathRequestMatcher("/**"));
        setAuthenticationManager(authenticationManager)
        setAuthenticationSuccessHandler(new AuthenticationSuccessHandler() {
            @Override
            void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
                // ignore
            }
        })
    }

    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        super.doFilter(req, res, chain)
    }

    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {

        String token = obtainToken(request)

        if (token == null) {
            throw new AuthenticationServiceException(
                    "Authentication credentials wrong: token=${token}")
        }

        AuthToken authToken = new AuthToken(token);

        // Allow subclasses to set the "details" property
        setDetails(request, authToken);

        return this.getAuthenticationManager().authenticate(authToken);
    }

    String obtainToken(HttpServletRequest request) {
        return request.getHeader('X-Authentication')
    }

    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult)
        chain.doFilter(request, response)
    }

     /**
     * Provided so that subclasses may configure what is put into the authentication
     * request's details property.
     *
     * @param request that an authentication request is being created for
     * @param authRequest the authentication request object that should have its details
     * set
     */
    protected void setDetails(HttpServletRequest request, AuthToken authRequest) {
        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
    }
}
