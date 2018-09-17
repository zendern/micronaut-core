package io.micronaut.multitenancy.gorm.httpheader

interface BookFetcher {
    List<String> findAll()
}