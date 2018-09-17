package io.micronaut.multitenancy.gorm.cookie

import io.micronaut.context.annotation.Requires
import io.micronaut.security.utils.SecurityService

import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Requires(property = 'spec.name', value = 'multitenancy.cookie.gorm')
@Singleton
class BookService {

    private final Map<String, List<Book>> books = new ConcurrentHashMap<>()

    Book save(String title) {
        String username = tenantId() as String
        return save(username, title)
    }

    Serializable tenantId() {
        new io.micronaut.multitenancy.gorm.CookieTenantResolver().resolveTenantIdentifier()
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
        String username = tenantId() as String
        return books.get(username)
    }
}



