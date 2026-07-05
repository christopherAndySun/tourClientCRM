package com.tourcrm.service;

import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class DealRepository {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public DealRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DealResponse> findByCode(String dealCode) {
        if (!StringUtils.hasText(dealCode)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date, quote_text,
                                   travel_date, itinerary, deal_date, deal_user, deal_user_code, total_deal_sequence,
                                   personal_deal_sequence, status, refund_amount, refund_remark, refunded_at, landing_at,
                                   landing_remark, created_at_text, updated_at_text
                            FROM crm_deals
                            WHERE deal_code = ?
                            """,
                    (rs, rowNum) -> readDealRow(rs),
                    dealCode));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public boolean existsForCustomer(String customerCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals WHERE customer_code = ?", Long.class, customerCode);
        return count != null && count > 0;
    }

    public boolean exists(String dealCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals WHERE deal_code = ?", Long.class, dealCode);
        return count != null && count > 0;
    }

    public PageResponse<DealResponse> queryReportPage(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            String salesEmployeeCode,
            Integer page,
            Integer pageSize
    ) {
        DealReportSql reportSql = buildDealReportSql(salesEmployeeCode);
        List<Object> filterParams = new ArrayList<>();
        String where = buildDealReportWhereSql(keyword, dealCode, customerCode, customerName, status, startDate, endDate, filterParams);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        List<Object> countParams = new ArrayList<>(reportSql.params());
        countParams.addAll(filterParams);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + reportSql.sql() + ") deal_view WHERE " + where, Long.class, countParams.toArray());

        List<Object> pageParams = new ArrayList<>(countParams);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        String pageSql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text
                FROM (
                """ + reportSql.sql()
                + "\n) deal_view\n"
                + "WHERE " + where + "\n"
                + "ORDER BY COALESCE(updated_at_value, created_at_value) DESC, deal_code DESC\n"
                + "LIMIT ?, ?";
        List<DealResponse> rows = jdbcTemplate.query(pageSql,
                (rs, rowNum) -> readDealRow(rs),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public List<DealResponse> queryReportForExport(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            String salesEmployeeCode,
            int limit
    ) {
        DealReportSql reportSql = buildDealReportSql(salesEmployeeCode);
        List<Object> filterParams = new ArrayList<>();
        String where = buildDealReportWhereSql(keyword, dealCode, customerCode, customerName, status, startDate, endDate, filterParams);
        List<Object> params = new ArrayList<>(reportSql.params());
        params.addAll(filterParams);
        params.add(Math.max(1, Math.min(limit, 50000)));
        String sql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text
                FROM (
                """ + reportSql.sql()
                + "\n) deal_view\n"
                + "WHERE " + where + "\n"
                + "ORDER BY COALESCE(updated_at_value, created_at_value) DESC, deal_code DESC\n"
                + "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> readDealRow(rs), params.toArray());
    }

    public void write(DealResponse row) {
        upsertDeal(row);
    }

    public boolean insert(DealResponse row) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO crm_deals (
                              deal_code, customer_code, customer_name, deposit, deposit_value, remaining_balance, remaining_balance_value, booking_date,
                              booking_date_value, add_wechat_date, add_wechat_date_value, quote_text, travel_date,
                              travel_date_value, itinerary, deal_date, deal_date_value, deal_user, deal_user_code,
                              total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_amount_value,
                              refund_remark, refunded_at, refunded_at_value, landing_at, landing_at_value,
                              landing_remark, created_at_text, created_at_value, updated_at_text, updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    dealParams(row));
            return true;
        } catch (DuplicateKeyException error) {
            return false;
        }
    }

    public void delete(String dealCode) {
        jdbcTemplate.update("DELETE FROM crm_deals WHERE deal_code = ?", dealCode);
    }

    private void upsertDeal(DealResponse row) {
        jdbcTemplate.update("""
                            INSERT INTO crm_deals (
                              deal_code, customer_code, customer_name, deposit, deposit_value, remaining_balance, remaining_balance_value, booking_date,
                              booking_date_value, add_wechat_date, add_wechat_date_value, quote_text, travel_date,
                              travel_date_value, itinerary, deal_date, deal_date_value, deal_user, deal_user_code,
                              total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_amount_value,
                              refund_remark, refunded_at, refunded_at_value, landing_at, landing_at_value,
                              landing_remark, created_at_text, created_at_value, updated_at_text, updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              customer_code = VALUES(customer_code),
                              customer_name = VALUES(customer_name),
                              deposit = VALUES(deposit),
                              deposit_value = VALUES(deposit_value),
                              remaining_balance = VALUES(remaining_balance),
                              remaining_balance_value = VALUES(remaining_balance_value),
                              booking_date = VALUES(booking_date),
                              booking_date_value = VALUES(booking_date_value),
                              add_wechat_date = VALUES(add_wechat_date),
                              add_wechat_date_value = VALUES(add_wechat_date_value),
                              quote_text = VALUES(quote_text),
                              travel_date = VALUES(travel_date),
                              travel_date_value = VALUES(travel_date_value),
                              itinerary = VALUES(itinerary),
                              deal_date = VALUES(deal_date),
                              deal_date_value = VALUES(deal_date_value),
                              deal_user = VALUES(deal_user),
                              deal_user_code = VALUES(deal_user_code),
                              total_deal_sequence = VALUES(total_deal_sequence),
                              personal_deal_sequence = VALUES(personal_deal_sequence),
                              status = VALUES(status),
                              refund_amount = VALUES(refund_amount),
                              refund_amount_value = VALUES(refund_amount_value),
                              refund_remark = VALUES(refund_remark),
                              refunded_at = VALUES(refunded_at),
                              refunded_at_value = VALUES(refunded_at_value),
                              landing_at = VALUES(landing_at),
                              landing_at_value = VALUES(landing_at_value),
                              landing_remark = VALUES(landing_remark),
                              created_at_text = VALUES(created_at_text),
                              created_at_value = VALUES(created_at_value),
                              updated_at_text = VALUES(updated_at_text),
                              updated_at_value = VALUES(updated_at_value)
                            """,
                dealParams(row));
    }

    private DealReportSql buildDealReportSql(String salesEmployeeCode) {
        List<Object> unionParams = new ArrayList<>();
        String salesWhere = "";
        if (StringUtils.hasText(salesEmployeeCode)) {
            salesWhere = " AND UPPER(deal_user_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        String unionSql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text,
                       deal_date_value, created_at_value, updated_at_value
                FROM (
                  SELECT d.deal_code,
                         d.customer_code,
                         d.customer_name,
                         COALESCE(NULLIF(c.deposit_amount, ''), d.deposit) AS deposit,
                         COALESCE(NULLIF(c.remaining_balance, ''), d.remaining_balance) AS remaining_balance,
                         d.booking_date,
                         d.add_wechat_date,
                         d.quote_text,
                         d.travel_date,
                         d.itinerary,
                         d.deal_date,
                         d.deal_user,
                         d.deal_user_code,
                         d.total_deal_sequence,
                         d.personal_deal_sequence,
                         CASE WHEN c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED') THEN c.status ELSE d.status END AS status,
                         CASE WHEN c.status = 'REFUNDED' AND c.refund_amount IS NOT NULL AND c.refund_amount <> '' THEN c.refund_amount ELSE d.refund_amount END AS refund_amount,
                         CASE WHEN c.status = 'REFUNDED' AND c.status_remark IS NOT NULL AND c.status_remark <> '' THEN c.status_remark ELSE d.refund_remark END AS refund_remark,
                         CASE WHEN c.status = 'REFUNDED' AND c.refunded_at IS NOT NULL AND c.refunded_at <> '' THEN c.refunded_at ELSE d.refunded_at END AS refunded_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_at IS NOT NULL AND c.landing_at <> '' THEN c.landing_at ELSE d.landing_at END AS landing_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_remark IS NOT NULL AND c.landing_remark <> '' THEN c.landing_remark ELSE d.landing_remark END AS landing_remark,
                         d.created_at_text,
                         d.updated_at_text,
                         d.deal_date_value,
                         d.created_at_value,
                         d.updated_at_value
                  FROM crm_deals d
                  LEFT JOIN crm_clues c ON c.customer_code = d.customer_code
                  WHERE 1 = 1""" + salesWhere + """
                  UNION ALL
                  SELECT CONCAT('AUTO-', c.customer_code) AS deal_code,
                         c.customer_code,
                         CASE WHEN c.contact_info IS NOT NULL AND c.contact_info <> '' THEN c.contact_info ELSE c.customer_code END AS customer_name,
                         c.deposit_amount AS deposit,
                         c.remaining_balance AS remaining_balance,
                         '' AS booking_date,
                         '' AS add_wechat_date,
                         '' AS quote_text,
                         '' AS travel_date,
                         c.remark AS itinerary,
                         SUBSTRING(COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text), 1, 10) AS deal_date,
                         c.assigned_sales AS deal_user,
                         c.assigned_sales_employee_code AS deal_user_code,
                         0 AS total_deal_sequence,
                         0 AS personal_deal_sequence,
                         c.status,
                         c.refund_amount,
                         c.status_remark AS refund_remark,
                         c.refunded_at,
                         c.landing_at,
                         c.landing_remark,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS created_at_text,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS updated_at_text,
                         DATE(COALESCE(c.updated_at_value, c.created_at_value)) AS deal_date_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS created_at_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS updated_at_value
                  FROM crm_clues c
                  WHERE c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED')
                    AND c.status <> 'DELETED'
                    AND c.assigned_sales_employee_code IS NOT NULL
                    AND c.assigned_sales_employee_code <> ''
                    AND NOT EXISTS (SELECT 1 FROM crm_deals d WHERE d.customer_code = c.customer_code)
                """;
        if (StringUtils.hasText(salesEmployeeCode)) {
            unionSql += " AND UPPER(c.assigned_sales_employee_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        unionSql += "\n) deal_rows";
        return new DealReportSql(unionSql, unionParams);
    }

    private String buildDealReportWhereSql(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("1 = 1");
        appendLike(where, params, "deal_code", dealCode);
        appendLike(where, params, "customer_code", customerCode);
        appendLike(where, params, "customer_name", customerName);
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            where.append("""
                    AND (
                      LOWER(deal_code) LIKE ?
                      OR LOWER(customer_code) LIKE ?
                      OR LOWER(customer_name) LIKE ?
                      OR LOWER(deal_user) LIKE ?
                      OR LOWER(deal_user_code) LIKE ?
                      OR LOWER(status) LIKE ?
                      OR LOWER(refund_remark) LIKE ?
                      OR LOWER(quote_text) LIKE ?
                      OR LOWER(itinerary) LIKE ?
                    )
                    """);
            String value = likeValue(keyword);
            for (int i = 0; i < 9; i++) {
                params.add(value);
            }
        }
        appendDateRange(where, params, "deal_date_value", startDate, endDate);
        return where.toString();
    }

    private void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        where.append(" AND ").append(column).append(" LIKE ?");
        params.add("%" + value.trim() + "%");
    }

    private void appendDateRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
        java.sql.Date start = parseDate(startDate);
        if (start != null) {
            where.append(" AND ").append(column).append(" >= ?");
            params.add(start);
        }
        java.sql.Date end = parseDate(endDate);
        if (end != null) {
            where.append(" AND ").append(column).append(" < DATE_ADD(?, INTERVAL 1 DAY)");
            params.add(end);
        }
    }

    private DealResponse readDealRow(ResultSet rs) throws SQLException {
        return new DealResponse(
                rs.getString("deal_code"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getString("deposit"),
                rs.getString("remaining_balance"),
                rs.getString("booking_date"),
                rs.getString("add_wechat_date"),
                rs.getString("quote_text"),
                rs.getString("travel_date"),
                rs.getString("itinerary"),
                rs.getString("deal_date"),
                rs.getString("deal_user"),
                rs.getString("deal_user_code"),
                intOrNull(rs, "total_deal_sequence"),
                intOrNull(rs, "personal_deal_sequence"),
                rs.getString("status"),
                rs.getString("refund_amount"),
                rs.getString("refund_remark"),
                rs.getString("refunded_at"),
                rs.getString("landing_at"),
                rs.getString("landing_remark"),
                rs.getString("created_at_text"),
                rs.getString("updated_at_text")
        );
    }

    private Object[] dealParams(DealResponse row) {
        return new Object[]{
                row.dealCode(), row.customerCode(), row.customerName(), row.deposit(), parseMoney(row.deposit()),
                row.remainingBalance(), parseMoney(row.remainingBalance()), row.bookingDate(), parseDate(row.bookingDate()), row.addWechatDate(), parseDate(row.addWechatDate()),
                row.quoteText(), row.travelDate(), parseDate(row.travelDate()), row.itinerary(), row.dealDate(),
                parseDate(row.dealDate()), row.dealUser(), row.dealUserCode(), row.totalDealSequence(),
                row.personalDealSequence(), row.status(), row.refundAmount(), parseMoney(row.refundAmount()),
                row.refundRemark(), row.refundedAt(), parseDateTime(row.refundedAt()), row.landingAt(),
                parseDateTime(row.landingAt()), row.landingRemark(), row.createdAt(), parseDateTime(row.createdAt()),
                row.updatedAt(), parseDateTime(row.updatedAt())
        };
    }

    private Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal parseMoney(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private java.sql.Date parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(value.trim(), DATE_FORMAT));
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private Timestamp parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim().replace("T", " ");
            if (normalized.length() == 10) {
                return Timestamp.valueOf(LocalDate.parse(normalized, DATE_FORMAT).atStartOfDay());
            }
            return Timestamp.valueOf(LocalDateTime.parse(normalized, DATE_TIME_FORMAT));
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private String likeValue(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private record DealReportSql(String sql, List<Object> params) {
    }
}
