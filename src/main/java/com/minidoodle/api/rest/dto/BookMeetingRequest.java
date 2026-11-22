package com.minidoodle.api.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record BookMeetingRequest(
    @NotBlank String title,
    String description,
    @NotEmpty Set<@Email String> participantEmails
) {}
