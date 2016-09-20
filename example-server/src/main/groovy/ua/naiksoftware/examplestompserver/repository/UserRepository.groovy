package ua.naiksoftware.examplestompserver.repository

import groovy.transform.CompileStatic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import ua.naiksoftware.examplestompserver.domains.User

/**
 * Created by naik on 18.09.16.
 */
@CompileStatic
interface UserRepository extends JpaRepository<User, Long> {

    User findByToken(@Param("authToken") String authToken);
}
