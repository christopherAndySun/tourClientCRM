package com.tourcrm.service;

import com.tourcrm.dto.MenuItemRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class MenuRepository {

    private final JdbcTemplate jdbcTemplate;

    public MenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MenuItemRecord> readMenus() {
        return jdbcTemplate.query("""
                SELECT menu_code, group_code, group_name, name, description, path, sort_no, enabled
                FROM crm_menus
                ORDER BY group_code, sort_no
                """, (rs, rowNum) -> new MenuItemRecord(
                rs.getString("menu_code"),
                rs.getString("group_code"),
                rs.getString("group_name"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("path"),
                rs.getInt("sort_no"),
                rs.getInt("enabled") == 1
        ));
    }

    @Transactional
    public void writeMenus(List<MenuItemRecord> rows) {
        for (MenuItemRecord row : rows) {
            writeMenu(row);
        }
    }

    public void writeMenu(MenuItemRecord row) {
        jdbcTemplate.update("""
                        INSERT INTO crm_menus (menu_code, group_code, group_name, name, description, path, sort_no, enabled)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          group_code = VALUES(group_code),
                          group_name = VALUES(group_name),
                          name = VALUES(name),
                          description = VALUES(description),
                          path = VALUES(path),
                          sort_no = VALUES(sort_no),
                          enabled = VALUES(enabled)
                        """,
                row.code(), row.groupCode(), row.groupName(), row.name(), row.description(), row.path(),
                row.sort() == null ? 0 : row.sort(), Boolean.TRUE.equals(row.enabled()) ? 1 : 0);
    }
}
