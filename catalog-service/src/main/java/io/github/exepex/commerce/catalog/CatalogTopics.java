package io.github.exepex.commerce.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commerce.topics")
record CatalogTopics(String stockOut) {}
