package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.catalog.constants.ConfigKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(ConfigKeys.TOPICS_PREFIX)
record CatalogTopics(String stockOut) {}
