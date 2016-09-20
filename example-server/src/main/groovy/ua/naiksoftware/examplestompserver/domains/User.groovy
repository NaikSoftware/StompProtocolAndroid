package ua.naiksoftware.examplestompserver.domains

import groovy.transform.CompileStatic
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.NotBlank

import javax.persistence.Entity
import javax.persistence.Table
import javax.validation.constraints.NotNull

/**
 * Created by naik on 18.09.16.
 */
@Entity
@CompileStatic
@Table(name = "users")
class User extends Base {

    @NotBlank
    @Length(max = 32)
    String nickName

    @Length(max = 100)
    String email

    @Length(max = 32)
    String phone

    @Length(max = 250)
    String token

    @Length(max = 250)
    String avatarPath

    @NotNull
    boolean activated

//    @OneToMany(fetch = FetchType.LAZY, mappedBy = "user")
//    Set<UserPreference> userPreferences = []

    Long refer

    Date birthday

    @Length(max = 32)
    String inviteCode

    String password
}
