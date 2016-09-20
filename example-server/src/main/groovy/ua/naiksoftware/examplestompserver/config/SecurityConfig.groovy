package ua.naiksoftware.examplestompserver.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import ua.naiksoftware.examplestompserver.service.UserAuthService

import javax.servlet.MultipartConfigElement

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired UserAuthService userAuthService

    /**
     * Mapping:
     *     - /api/* for public HTTP requests
     *     - /api/private/* for private HTTP requests
     *     - /stomp/* for private STOMP requests
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                    .authorizeRequests()
                    .regexMatchers("/api/.*").permitAll()
                .and()
                    .regexMatcher("/api/private/.*|/stomp/.*")
                    .addFilterBefore(new AuthTokenFilter(authenticationManager()), BasicAuthenticationFilter.class)
                    .authorizeRequests()
                    .anyRequest().authenticated()
                .and()
                    .csrf().disable()
    }

    @Bean
    public AuthenticationProvider tokenAuthenticationProvider() {
        return new TokenAuthenticationProvider(userAuthService)
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(tokenAuthenticationProvider())
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize("128MB");
        factory.setMaxRequestSize("128MB");
        return factory.createMultipartConfig();
    }
}
