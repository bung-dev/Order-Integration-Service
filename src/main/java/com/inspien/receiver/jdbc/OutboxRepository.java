package com.inspien.receiver.jdbc;

import com.inspien.order.domain.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final NamedParameterJdbcTemplate template;


    public int[] batchInsert(List<Outbox> outboxes) {
        String sql = """
                INSERT INTO OUTBOX_TB
                (APPLICANT_KEY, ORDER_ID, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS, PROCESSED)
                VALUES
                (:applicantKey, :orderId, :userId, :itemId, :name, :address, :itemName, :price, :status, :processed)
                """;
        if (outboxes == null || outboxes.isEmpty()) {
            return new int[0];
        }

        SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(outboxes);

        return template.batchUpdate(sql, params);
    }

    public List<Outbox> findUnprocessed(String applicantKey) {
        String sql = """
                SELECT APPLICANT_KEY, ORDER_ID, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS, PROCESSED
                FROM OUTBOX_TB
                WHERE APPLICANT_KEY = :applicantKey AND PROCESSED = false
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey);

        return template.query(sql, params, new BeanPropertyRowMapper<>(Outbox.class));
    }

    public int updateProcessed(String applicantKey, List<String> orderIds) {
        String sql = """
                UPDATE OUTBOX_TB
                SET PROCESSED = true
                WHERE APPLICANT_KEY = :applicantKey AND ORDER_ID IN (:orderIds)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey)
                .addValue("orderIds", orderIds);

        return template.update(sql, params);
    }

    public int deleteProcessedOldData(String applicantKey, int days) {
        String sql = """
                DELETE FROM OUTBOX_TB
                WHERE APPLICANT_KEY = :applicantKey
                  AND PROCESSED = true
                  AND UPDATED < :cutoffDate
                """;
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey)
                .addValue("cutoffDate", cutoffDate);
        return template.update(sql, params);
    }

    public List<Outbox> findTargetForProcess(String applicantKey, int maxRetry) {
        String sql = """
                SELECT APPLICANT_KEY, ORDER_ID, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS, PROCESSED, RETRY_COUNT, LAST_ERROR_MSG
                FROM OUTBOX_TB
                WHERE APPLICANT_KEY = :applicantKey AND STATUS = 'UNPROCESSED' AND RETRY_COUNT < :maxRetry
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey)
                .addValue("maxRetry", maxRetry);
        return template.query(sql, params, new BeanPropertyRowMapper<>(Outbox.class));
    }

    public int increaseRetryCount(String orderId, String errorMsg) {
        String sql = """
                UPDATE OUTBOX_TB
                SET RETRY_COUNT = RETRY_COUNT + 1, LAST_ERROR_MSG = :errorMsg
                WHERE ORDER_ID = :orderId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("errorMsg", errorMsg);
        return template.update(sql, params);
    }

    public int updateStatus(String orderId, String status) {
        String sql = """
                UPDATE OUTBOX_TB
                SET STATUS = :status,
                    PROCESSED = CASE WHEN :status = 'PROCESSED' THEN true ELSE PROCESSED END
                WHERE ORDER_ID = :orderId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("status", status);
        return template.update(sql, params);
    }

    public List<Outbox> findFailed(String applicantKey) {
        String sql = """
                SELECT APPLICANT_KEY, ORDER_ID, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS, PROCESSED, RETRY_COUNT, LAST_ERROR_MSG
                FROM OUTBOX_TB
                WHERE APPLICANT_KEY = :applicantKey AND STATUS = 'FAILED'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey);
        return template.query(sql, params, new BeanPropertyRowMapper<>(Outbox.class));
    }

    public int resetFailed(String orderId) {
        String sql = """
                UPDATE OUTBOX_TB
                SET STATUS = 'UNPROCESSED', RETRY_COUNT = 0, LAST_ERROR_MSG = NULL
                WHERE ORDER_ID = :orderId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId);
        return template.update(sql, params);
    }
}
