package io.github.exepex.commerce.servicenow.incidents.dto;

/** What a tool that changed an incident tells the agent it did. */
public record Acknowledgement(String number, String message) {}
