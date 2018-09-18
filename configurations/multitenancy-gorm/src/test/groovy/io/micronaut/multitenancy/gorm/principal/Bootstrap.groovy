package io.micronaut.multitenancy.gorm.principal

import grails.gorm.multitenancy.Tenants
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import javax.inject.Singleton
import javax.inject.Inject

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@Singleton
class Bootstrap implements ApplicationEventListener<StartupEvent> {

    @Inject
    BookService bookService

    @Override
    void onApplicationEvent(StartupEvent event) {

        Tenants.withId("sherlock") {
            bookService.save('Sherlock diary')
        }
        Tenants.withId("watson") {
            bookService.save('Watson diary')
        }
    }
}
