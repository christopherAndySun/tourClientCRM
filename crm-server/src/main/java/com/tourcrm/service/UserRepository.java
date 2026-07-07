package com.tourcrm.service;

import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.UserRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserRecord> readUsers() {
        return jdbcTemplate.query("""
                SELECT employee_code, name, password, must_change_password, role, position, leader_employee_code, org_type, branch_id, branch_name, created_at_text
                FROM crm_users
                ORDER BY created_at_text DESC
                """, (rs, rowNum) -> readUserRow(rs));
    }

    public PageResponse<UserRecord> queryUsersPage(Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_users", Long.class);
        List<UserRecord> rows = jdbcTemplate.query("""
                        SELECT employee_code, name, password, must_change_password, role, position, leader_employee_code, org_type, branch_id, branch_name, created_at_text
                        FROM crm_users
                        ORDER BY created_at_text DESC, employee_code ASC
                        LIMIT ?, ?
                        """,
                (rs, rowNum) -> readUserRow(rs),
                (safePage - 1) * safePageSize,
                safePageSize);
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public Optional<UserRecord> findUserByEmployeeCode(String employeeCode) {
        if (!StringUtils.hasText(employeeCode)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT employee_code, name, password, must_change_password, role, position, leader_employee_code, org_type, branch_id, branch_name, created_at_text
                            FROM crm_users
                            WHERE employee_code = ?
                            """,
                    (rs, rowNum) -> readUserRow(rs),
                    employeeCode));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public boolean userExists(String employeeCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_users WHERE employee_code = ?", Long.class, employeeCode);
        return count != null && count > 0;
    }

    @Transactional
    public void writeUsers(List<UserRecord> rows) {
        for (UserRecord row : rows) {
            writeUser(row);
        }
    }

    @Transactional
    public void writeUser(UserRecord row) {
        jdbcTemplate.update("""
                        INSERT INTO crm_users (employee_code, name, password, must_change_password, role, position, leader_employee_code, org_type, branch_id, branch_name, created_at_text)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          name = VALUES(name),
                          password = VALUES(password),
                          must_change_password = VALUES(must_change_password),
                          role = VALUES(role),
                          position = VALUES(position),
                          leader_employee_code = VALUES(leader_employee_code),
                          org_type = VALUES(org_type),
                          branch_id = VALUES(branch_id),
                          branch_name = VALUES(branch_name),
                          created_at_text = VALUES(created_at_text)
                        """,
                row.employeeCode(), row.name(), row.password(), row.mustChangePassword(), row.role(), row.position(), row.leaderEmployeeCode(),
                row.orgType(), row.branchId(), row.branchName(), row.createdAt());
        replaceUserMenus(row.employeeCode(), row.menuPermissions());
    }

    public void releaseMembersByLeader(String leaderEmployeeCode) {
        jdbcTemplate.update("UPDATE crm_users SET leader_employee_code = NULL WHERE leader_employee_code = ?", leaderEmployeeCode);
    }

    public void deleteUser(String employeeCode) {
        jdbcTemplate.update("DELETE FROM crm_user_menu_permissions WHERE employee_code = ?", employeeCode);
        jdbcTemplate.update("DELETE FROM crm_users WHERE employee_code = ?", employeeCode);
    }

    private List<String> readUserMenus(String employeeCode) {
        return jdbcTemplate.query(
                "SELECT menu_code FROM crm_user_menu_permissions WHERE employee_code = ? ORDER BY menu_code",
                (rs, rowNum) -> rs.getString("menu_code"),
                employeeCode);
    }

    private void replaceUserMenus(String employeeCode, List<String> menuPermissions) {
        jdbcTemplate.update("DELETE FROM crm_user_menu_permissions WHERE employee_code = ?", employeeCode);
        if (menuPermissions == null) {
            return;
        }
        for (String menuCode : menuPermissions) {
            jdbcTemplate.update("INSERT IGNORE INTO crm_user_menu_permissions (employee_code, menu_code) VALUES (?, ?)", employeeCode, menuCode);
        }
    }

    private UserRecord readUserRow(ResultSet rs) throws SQLException {
        String employeeCode = rs.getString("employee_code");
        return new UserRecord(
                rs.getString("name"),
                employeeCode,
                rs.getString("password"),
                rs.getString("role"),
                rs.getString("position"),
                rs.getString("leader_employee_code"),
                rs.getString("org_type"),
                rs.getString("branch_id"),
                rs.getString("branch_name"),
                rs.getBoolean("must_change_password"),
                readUserMenus(employeeCode),
                rs.getString("created_at_text")
        );
    }
}
