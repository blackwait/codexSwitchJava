package com.codexswitcher.model;

public record AccountProbeStatus(
    String accountName,
    boolean team,
    boolean ok,
    String summary,
    String detail
) {
}
