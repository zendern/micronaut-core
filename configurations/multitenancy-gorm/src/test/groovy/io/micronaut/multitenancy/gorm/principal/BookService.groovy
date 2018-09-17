package io.micronaut.multitenancy.gorm.principal

import io.micronaut.context.annotation.Requires
import io.micronaut.security.utils.SecurityService

import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@Singleton
class BookService {
    private final SecurityService securityService

    BookService(SecurityService securityService) {
        this.securityService = securityService
    }

    private final Map<String, List<Book>> books = new ConcurrentHashMap<>()

    Book save(String title) {
        if(securityService.isAuthenticated()) {
            Optional<String> usernameOptional = securityService.username().get()
            if (usernameOptional.isPresent()) {
                String username = usernameOptional.get()
                return save(username, title)
            }
        }
        null
    }

    Book save(String username, String title) {
        if (!books.containsKey(username)) {
            books.put(username, new ArrayList<>())
        }
        Book b = new Book(title: title)
        books.get(username).add(b)
        return b
    }

    List<Book> list() {
        if(securityService.isAuthenticated()) {
            Optional<String> usernameOptional = securityService.username()
            if (usernameOptional.isPresent()) {
                return books.get(usernameOptional.get())
            }
        }
        null
    }
}



