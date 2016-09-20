package ua.naiksoftware.examplestompserver

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserPrincipal implements UserDetails {

    String userPassword
    Long userId
    String email
    String phone
    String avatarPath
    String authToken
    boolean active
    String nickname

    @Override
    Collection<? extends GrantedAuthority> getAuthorities() {
        return null
    }

    @Override
    String getPassword() {
        return userPassword
    }

    /**
     * @return userId because its used in socket identifying users
     */
    @Override
    String getUsername() {
        return userId as String
    }

    @Override
    boolean isAccountNonExpired() {
        return true
    }

    @Override
    boolean isAccountNonLocked() {
        return true
    }

    @Override
    boolean isCredentialsNonExpired() {
        return true
    }

    @Override
    boolean isEnabled() {
        return active
    }

    public String getNickname() {
        return nickname
    }
}

