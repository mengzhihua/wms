package com.wms.common;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates document numbers like ASN20250101-0001 backed by table wms_sequence
 * (per prefix + day), so numbers stay unique across restarts and multiple instances.
 */
@Component
@RequiredArgsConstructor
public class CodeGenerator {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix) {
        String day = LocalDate.now().format(DAY);
        int updated = jdbc.update("UPDATE wms_sequence SET seq_value = seq_value + 1 WHERE prefix = ? AND day_key = ?", prefix, day);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO wms_sequence (prefix, day_key, seq_value) VALUES (?, ?, 1)", prefix, day);
            } catch (DuplicateKeyException e) {
                jdbc.update("UPDATE wms_sequence SET seq_value = seq_value + 1 WHERE prefix = ? AND day_key = ?", prefix, day);
            }
        }
        Integer n = jdbc.queryForObject("SELECT seq_value FROM wms_sequence WHERE prefix = ? AND day_key = ?", Integer.class, prefix, day);
        return String.format("%s%s-%04d", prefix, day, n);
    }
}
