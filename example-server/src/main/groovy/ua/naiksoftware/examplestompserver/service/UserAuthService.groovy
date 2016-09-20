package ua.naiksoftware.examplestompserver.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import ua.naiksoftware.examplestompserver.UserPrincipal
import ua.naiksoftware.examplestompserver.repository.UserRepository

@Service
class UserAuthService implements UserDetailsService {

    Logger logger = LoggerFactory.getLogger(UserAuthService)

    @Autowired UserRepository userRepository

    @Override
    UserDetails loadUserByUsername(String authToken) throws UsernameNotFoundException {

        def user =  userRepository.findByToken(authToken)

        if (user == null) {

            logger.warn("Load User by user name: User not found, user authToken: $authToken")
            return null
        }

        def currentUser = new UserPrincipal(
                authToken: authToken,
                active: user.activated,
                phone: user.phone,
                email: user.email,
                userId: user.id,
                avatarPath: user.avatarPath,
                nickname: user.nickName,
                userPassword: user.password
        )

        return currentUser
    }

}
