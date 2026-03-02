package com.inspien.receiver.jdbc;

import com.inspien.order.domain.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

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

        return template.query(sql, params, (rs, rowNum) -> Outbox.builder()
                .applicantKey(rs.getString("APPLICANT_KEY"))
                .orderId(rs.getString("ORDER_ID"))
                .userId(rs.getString("USER_ID"))
                .itemId(rs.getString("ITEM_ID"))
                .name(rs.getString("NAME"))
                .address(rs.getString("ADDRESS"))
                .itemName(rs.getString("ITEM_NAME"))
                .price(rs.getString("PRICE"))
                .status(rs.getString("STATUS"))
                .processed(rs.getBoolean("PROCESSED"))
                .build());
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

    public int deleteProcessed(String applicantKey) {
        String sql = """
                DELETE FROM OUTBOX_TB
                WHERE APPLICANT_KEY = :applicantKey
                  AND PROCESSED = true
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicantKey", applicantKey);
        return template.update(sql, params);
    }
}
