package ua.naiksoftware.examplestompserver.domains
import groovy.transform.CompileStatic

import javax.persistence.*
import javax.validation.constraints.NotNull
/**
 * ============================================================================
 * Package       : com.riversoft.eventssion.domains
 * Created       : yvshvets, 04.06.15
 * Modifications :
 * Description   :
 * <p/>
 * <p/>
 * ============================================================================
 * Copyright(c) 2013 - 2015 Riversoft, Ukraine
 * ============================================================================
 */

@CompileStatic
@MappedSuperclass
abstract class Base {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id

    @NotNull
    Date createDate = new Date()

    @NotNull
    Date updateDate = new Date()

    @NotNull
    boolean hidden

    @PreUpdate
    private void onUpdate() {

        updateDate = new Date();
    }

    @PrePersist
    private void onPersist() {

        createDate = new Date();
        updateDate = new Date();
    }
}
