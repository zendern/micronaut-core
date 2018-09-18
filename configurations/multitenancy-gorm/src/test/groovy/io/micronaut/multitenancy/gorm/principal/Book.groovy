package io.micronaut.multitenancy.gorm.principal

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class Book implements GormEntity<Book>, MultiTenant<Book> {
    String title
    String username

    static mapping = {
        tenantId name:'username'
    }
}