package com.example.documentstore.repository;

import java.time.LocalDate;

/**
 * All fields are optional; a {@code null} field means "no filter on this field".
 * {@code nameLike}/{@code descriptionLike} are case-insensitive substring matches
 * (SQL {@code LIKE '%value%'} equivalent). {@code dateFrom}/{@code dateTo} bound
 * the document's last-modified date (inclusive) and are compared as calendar days.
 */
public record DocumentSearchCriteria(
        String nameLike,
        String descriptionLike,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
