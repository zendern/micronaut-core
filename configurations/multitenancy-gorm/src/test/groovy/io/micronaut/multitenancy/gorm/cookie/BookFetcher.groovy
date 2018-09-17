package io.micronaut.multitenancy.gorm.cookie

interface BookFetcher {
    List<String> findAll()
}