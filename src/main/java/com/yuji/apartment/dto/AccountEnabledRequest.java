package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotNull;

public record AccountEnabledRequest(@NotNull Boolean enabled) {}
