package io.micronaut.multitenancy.gorm.principal

interface BookFetcher {
    List<String> findAll()
}