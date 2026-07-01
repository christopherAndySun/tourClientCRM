package com.tourcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.MenuItemRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private static final TypeReference<List<MenuItemRecord>> MENU_LIST_TYPE = new TypeReference<>() {
    };

    private static final List<MenuItemRecord> DEFAULT_MENUS = List.of(
            new MenuItemRecord(AuthService.MENU_CLUES, "BUSINESS", "业务", "客户列表", "个人客户线索工作区", "/clues", 10, true),
            new MenuItemRecord(AuthService.MENU_CLUE_CREATE, "BUSINESS", "业务", "新增客户", "录入客户和老客新需求", "/clues/create", 20, true),
            new MenuItemRecord(AuthService.MENU_ASSIGN, "BUSINESS", "业务", "分配管理", "销售池领取、分配、转派和释放", "/assign", 30, true),
            new MenuItemRecord(AuthService.MENU_ASSIGN_LOGS, "BUSINESS", "业务", "分配日志", "查看领取、释放、转派和抢单冲突记录", "/assign-logs", 35, true),
            new MenuItemRecord(AuthService.MENU_DEALS, "BUSINESS", "业务", "成交记录", "登记、编辑、退单成交数据", "/deals", 40, true),
            new MenuItemRecord(AuthService.MENU_STATS, "MANAGE", "管理", "数据统计", "团队数据统计面板", "/index", 10, true),
            new MenuItemRecord(AuthService.MENU_PERFORMANCE, "MANAGE", "管理", "员工绩效", "员工绩效和明细查询", "/performance", 20, true),
            new MenuItemRecord(AuthService.MENU_OPERATION_LOGS, "MANAGE", "管理", "操作日志", "查看客户字段修改前后记录", "/operation-logs", 25, true),
            new MenuItemRecord(AuthService.MENU_USERS, "MANAGE", "管理", "账号管理", "员工账号与权限配置", "/users", 30, true),
            new MenuItemRecord(AuthService.MENU_ORG, "MANAGE", "管理", "组织架构", "查看和调整部门、组长、组员关系", "/org", 40, true),
            new MenuItemRecord(AuthService.MENU_MENUS, "MANAGE", "管理", "菜单管理", "维护菜单名称、排序和启停", "/menus", 50, true),
            new MenuItemRecord(AuthService.MENU_SETTINGS, "MANAGE", "管理", "系统设置", "维护 OCR 等系统级配置", "/settings", 60, true)
    );
    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final AuthService authService;

    public MenuService(
            ObjectMapper objectMapper,
            AuthService authService,
            @Value("${app.menu-data-file:data/menus.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.dataFile = Path.of(dataFile);
    }

    public synchronized List<MenuItemRecord> list(String token) {
        authService.currentUser(token);
        return sorted(readAll());
    }

    public synchronized List<MenuItemRecord> save(List<MenuItemRecord> request, String token) {
        authService.requireAdminUser(token);
        if (request == null || request.isEmpty()) {
            throw new BusinessException("菜单不能为空");
        }
        Map<String, MenuItemRecord> defaultMap = DEFAULT_MENUS.stream().collect(Collectors.toMap(MenuItemRecord::code, Function.identity()));
        Set<String> validCodes = defaultMap.keySet();
        List<MenuItemRecord> normalized = request.stream()
                .filter(item -> item != null && validCodes.contains(item.code()))
                .map(item -> normalize(item, defaultMap.get(item.code())))
                .toList();
        if (normalized.size() != validCodes.size()) {
            throw new BusinessException("系统菜单不完整，请刷新后重试");
        }
        writeAll(normalized);
        return sorted(normalized);
    }

    public List<String> enabledMenuCodes() {
        return readAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.enabled()) || AuthService.MENU_MENUS.equals(item.code()))
                .map(MenuItemRecord::code)
                .toList();
    }

    private MenuItemRecord normalize(MenuItemRecord item, MenuItemRecord fallback) {
        String name = clean(item.name(), fallback.name());
        String description = clean(item.description(), fallback.description());
        Integer sort = item.sort() == null ? fallback.sort() : item.sort();
        boolean enabled = AuthService.MENU_MENUS.equals(item.code()) || Boolean.TRUE.equals(item.enabled());
        return new MenuItemRecord(
                fallback.code(),
                fallback.groupCode(),
                fallback.groupName(),
                name,
                description,
                fallback.path(),
                sort,
                enabled
        );
    }

    private List<MenuItemRecord> sorted(List<MenuItemRecord> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(MenuItemRecord::groupCode).thenComparing(item -> item.sort() == null ? 0 : item.sort()))
                .toList();
    }

    private List<MenuItemRecord> readAll() {
        if (!Files.exists(dataFile)) {
            writeAll(DEFAULT_MENUS);
            return new ArrayList<>(DEFAULT_MENUS);
        }
        try {
            List<MenuItemRecord> rows = new ArrayList<>(objectMapper.readValue(dataFile.toFile(), MENU_LIST_TYPE));
            return mergeDefaults(rows);
        } catch (IOException error) {
            throw new IllegalStateException("读取菜单数据失败", error);
        }
    }

    private List<MenuItemRecord> mergeDefaults(List<MenuItemRecord> rows) {
        Map<String, MenuItemRecord> existing = rows.stream()
                .filter(item -> item != null && item.code() != null)
                .collect(Collectors.toMap(MenuItemRecord::code, Function.identity(), (left, right) -> left));
        List<MenuItemRecord> merged = DEFAULT_MENUS.stream()
                .map(item -> existing.containsKey(item.code()) ? normalize(existing.get(item.code()), item) : item)
                .collect(Collectors.toCollection(ArrayList::new));
        writeAll(merged);
        return merged;
    }

    private void writeAll(List<MenuItemRecord> rows) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), rows);
        } catch (IOException error) {
            throw new IllegalStateException("保存菜单数据失败", error);
        }
    }

    private String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
