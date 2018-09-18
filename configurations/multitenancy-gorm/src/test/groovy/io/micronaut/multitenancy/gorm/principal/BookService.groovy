package io.micronaut.multitenancy.gorm.principal

import io.micronaut.context.annotation.Requires
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@Service(Book)
@CurrentTenant
interface BookService {
    List<Book> list()
    Book save(String title)
}



