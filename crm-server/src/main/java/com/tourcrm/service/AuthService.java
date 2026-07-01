package com.tourcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.AdminUserUpdateRequest;
import com.tourcrm.dto.AuthLoginRequest;
import com.tourcrm.dto.AuthRegisterRequest;
import com.tourcrm.dto.AuthUserResponse;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import com.tourcrm.dto.UserUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthService {

    public static final String MENU_CLUES = "CLUES";
    public static final String MENU_CLUE_CREATE = "CLUE_CREATE";
    public static final String MENU_DEALS = "DEALS";
    public static final String MENU_STATS = "STATS";
    public static final String MENU_PERFORMANCE = "PERFORMANCE";
    public static final String MENU_USERS = "USERS";
    public static final String MENU_ASSIGN = "ASSIGN";
    public static final String MENU_ASSIGN_LOGS = "ASSIGN_LOGS";
    public static final String MENU_OPERATION_LOGS = "OPERATION_LOGS";
    public static final String MENU_ORG = "ORG";
    public static final String MENU_MENUS = "MENUS";
    public static final String MENU_SETTINGS = "SETTINGS";

    private static final String ADMIN_EMPLOYEE_CODE = "ADMIN";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final Pattern EMPLOYEE_CODE_PATTERN = Pattern.compile("^[A-Z]{2,5}$");
    private static final Set<String> ROLES = Set.of("ADMIN", "LEADER", "EMPLOYEE");
    private static final Set<String> POSITIONS = Set.of("OPERATION", "SALES");
    private static final Set<String> MENUS = Set.of(MENU_CLUES, MENU_CLUE_CREATE, MENU_DEALS, MENU_STATS, MENU_PERFORMANCE, MENU_USERS, MENU_ASSIGN, MENU_ASSIGN_LOGS, MENU_OPERATION_LOGS, MENU_ORG, MENU_MENUS, MENU_SETTINGS);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<List<UserRecord>> USER_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path dataFile;

    public AuthService(ObjectMapper objectMapper, @Value("${app.user-data-file:data/users.json}") String dataFile) {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile);
    }

    public synchronized AuthUserResponse login(AuthLoginRequest request) {
        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        Optional<UserRecord> matched = readAll().stream()
                .filter(user -> user.employeeCode().equals(employeeCode))
                .findFirst();
        if (matched.isEmpty() || !matched.get().password().equals(request.password())) {
            throw new BusinessException("员工编号或密码错误");
        }
        return toAuthResponse(matched.get());
    }

    public synchronized List<UserRecord> listUsers(String token) {
        requireAdmin(token);
        return readAll().stream()
                .sorted(Comparator.comparing(UserRecord::createdAt).reversed())
                .toList();
    }

    synchronized List<UserRecord> listAllUsersForSystem() {
        return readAll();
    }

    public synchronized List<UserRecord> listLeaders(String token) {
        requireAdmin(token);
        return readAll().stream()
                .filter(user -> "LEADER".equals(user.role()) || "ADMIN".equals(user.role()))
                .sorted(Comparator.comparing(UserRecord::employeeCode))
                .toList();
    }

    public synchronized List<UserRecord> listSalesCandidates(String token) {
        if (!hasMenuPermission(token, MENU_ASSIGN) && !hasMenuPermission(token, MENU_DEALS)) {
            throw new BusinessException("没有分配管理权限，请联系管理员开通");
        }
        return readAll().stream()
                .filter(user -> "SALES".equals(user.position()))
                .sorted(Comparator.comparing(UserRecord::employeeCode))
                .toList();
    }

    public synchronized List<UserRecord> usersVisibleTo(String token) {
        UserSession currentUser = currentUser(token);
        List<UserRecord> users = readAll();
        if ("ADMIN".equals(currentUser.role())) {
            return users;
        }
        if ("LEADER".equals(currentUser.role())) {
            return users.stream()
                    .filter(user -> user.employeeCode().equals(currentUser.employeeCode())
                            || currentUser.employeeCode().equals(user.leaderEmployeeCode()))
                    .toList();
        }
        return users.stream()
                .filter(user -> user.employeeCode().equals(currentUser.employeeCode()))
                .toList();
    }

    public synchronized UserRecord createUser(AuthRegisterRequest request, String token) {
        requireAdmin(token);
        validateRegisterRequest(request);
        List<UserRecord> users = readAll();
        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        boolean exists = users.stream().anyMatch(user -> user.employeeCode().equals(employeeCode));
        if (exists) {
            throw new BusinessException("员工编号已存在");
        }

        String role = normalizeRole(request.role(), "EMPLOYEE");
        String position = normalizePosition(request.position(), "OPERATION");
        String leaderEmployeeCode = "EMPLOYEE".equals(role) ? normalizeNullableEmployeeCode(request.leaderEmployeeCode()) : null;
        if (StringUtils.hasText(leaderEmployeeCode)) {
            validateLeader(users, leaderEmployeeCode, position);
        }
        UserRecord user = new UserRecord(
                request.name().trim(),
                employeeCode,
                request.password(),
                role,
                position,
                leaderEmployeeCode,
                normalizeMenus(request.menuPermissions(), role, position),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        users.add(user);
        writeAll(users);
        return user;
    }

    public synchronized UserRecord updateUser(String employeeCode, AdminUserUpdateRequest request, String token) {
        requireAdmin(token);
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        List<UserRecord> users = readAll();
        for (int i = 0; i < users.size(); i++) {
            UserRecord old = users.get(i);
            if (!old.employeeCode().equals(normalizedEmployeeCode)) {
                continue;
            }
            String role = normalizeRole(request.role(), old.role());
            String position = normalizePosition(request.position(), old.position());
            String leaderEmployeeCode = normalizeNullableEmployeeCode(request.leaderEmployeeCode());
            if ("ADMIN".equals(old.employeeCode())) {
                role = "ADMIN";
                leaderEmployeeCode = null;
            }
            if ("EMPLOYEE".equals(role) && !position.equals(old.position())) {
                leaderEmployeeCode = null;
            }
            if ("ADMIN".equals(role) || "LEADER".equals(role)) {
                leaderEmployeeCode = null;
            }
            if ("EMPLOYEE".equals(role) && StringUtils.hasText(leaderEmployeeCode)) {
                validateLeader(users, leaderEmployeeCode, position);
            }
            validateNameAndPassword(request.name(), request.password(), old.password());
            UserRecord updated = new UserRecord(
                    request.name().trim(),
                    old.employeeCode(),
                    StringUtils.hasText(request.password()) ? request.password() : old.password(),
                    role,
                    position,
                    leaderEmployeeCode,
                    normalizeMenus(request.menuPermissions(), role, position),
                    old.createdAt()
            );
            users.set(i, updated);
            if ("LEADER".equals(old.role()) && (!"LEADER".equals(role) || !position.equals(old.position()))) {
                users = releaseMembers(users, old.employeeCode());
            }
            writeAll(users);
            return updated;
        }
        throw new BusinessException("员工账号不存在");
    }

    public synchronized boolean deleteUser(String employeeCode, String token) {
        requireAdmin(token);
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        if (ADMIN_EMPLOYEE_CODE.equals(normalizedEmployeeCode)) {
            throw new BusinessException("管理员账号不能删除");
        }
        List<UserRecord> users = readAll();
        boolean removed = users.removeIf(user -> user.employeeCode().equals(normalizedEmployeeCode));
        if (removed) {
            users = releaseMembers(users, normalizedEmployeeCode);
            writeAll(users);
        }
        return removed;
    }

    public synchronized UserSession updateCurrentUser(UserUpdateRequest request, String token) {
        UserSession currentUser = currentUser(token);
        List<UserRecord> users = readAll();
        for (int i = 0; i < users.size(); i++) {
            UserRecord old = users.get(i);
            if (!old.employeeCode().equals(currentUser.employeeCode())) {
                continue;
            }
            validateNameAndPassword(request.name(), request.password(), old.password());
            UserRecord updated = new UserRecord(
                    request.name().trim(),
                    old.employeeCode(),
                    StringUtils.hasText(request.password()) ? request.password() : old.password(),
                    old.role(),
                    old.position(),
                    old.leaderEmployeeCode(),
                    old.menuPermissions(),
                    old.createdAt()
            );
            users.set(i, updated);
            writeAll(users);
            return toSession(updated);
        }
        throw new BusinessException("当前账号不存在，请重新登录");
    }

    public UserSession currentUser(String token) {
        if (!StringUtils.hasText(token)) {
            return previewUser();
        }
        String rawToken = token.replace("Bearer ", "").trim();
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rawToken), StandardCharsets.UTF_8);
            String employeeCode = decoded.split(":", 2)[0];
            return readAll().stream()
                    .filter(user -> user.employeeCode().equals(employeeCode))
                    .findFirst()
                    .map(this::toSession)
                    .orElse(previewUser());
        } catch (IllegalArgumentException error) {
            return previewUser();
        }
    }

    public boolean hasMenuPermission(String token, String menuCode) {
        UserSession currentUser = currentUser(token);
        return "ADMIN".equals(currentUser.role()) || currentUser.menuPermissions().contains(menuCode);
    }

    public UserSession previewUser() {
        return new UserSession("Xbai", "XB", "EMPLOYEE", "OPERATION", null, defaultMenus("EMPLOYEE", "OPERATION"));
    }

    public void requireAdminUser(String token) {
        requireAdmin(token);
    }

    private void requireAdmin(String token) {
        UserSession currentUser = currentUser(token);
        if (!"ADMIN".equalsIgnoreCase(currentUser.role())) {
            throw new BusinessException("只有管理员可以操作账号");
        }
    }

    private void validateRegisterRequest(AuthRegisterRequest request) {
        validateNameAndPassword(request.name(), request.password(), "");
        if (!StringUtils.hasText(request.employeeCode()) || !EMPLOYEE_CODE_PATTERN.matcher(normalizeEmployeeCode(request.employeeCode())).matches()) {
            throw new BusinessException("员工编号必须是 2-5 位大写字母");
        }
    }

    private void validateNameAndPassword(String name, String password, String oldPassword) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("请填写姓名");
        }
        String finalPassword = StringUtils.hasText(password) ? password : oldPassword;
        if (!StringUtils.hasText(finalPassword) || finalPassword.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
    }

    private String normalizeRole(String role, String fallback) {
        String normalized = StringUtils.hasText(role) ? role.trim().toUpperCase() : fallback;
        if (!ROLES.contains(normalized)) {
            throw new BusinessException("角色不正确");
        }
        return normalized;
    }

    private String normalizePosition(String position, String fallback) {
        String normalized = StringUtils.hasText(position) ? position.trim().toUpperCase() : fallback;
        if (!POSITIONS.contains(normalized)) {
            throw new BusinessException("岗位不正确");
        }
        return normalized;
    }

    private List<String> normalizeMenus(List<String> menuPermissions, String role, String position) {
        if ("ADMIN".equals(role)) {
            return List.copyOf(MENUS);
        }
        List<String> source = (menuPermissions == null || menuPermissions.isEmpty()) ? defaultMenus(role, position) : menuPermissions;
        return source.stream()
                .filter(MENUS::contains)
                .distinct()
                .toList();
    }

    private List<String> defaultMenus(String role, String position) {
        if ("SALES".equals(position)) {
            return List.of(MENU_ASSIGN, MENU_ASSIGN_LOGS, MENU_DEALS, MENU_STATS, MENU_PERFORMANCE);
        }
        return List.of(MENU_CLUES, MENU_CLUE_CREATE, MENU_ASSIGN, MENU_ASSIGN_LOGS, MENU_STATS, MENU_PERFORMANCE);
    }

    private AuthUserResponse toAuthResponse(UserRecord user) {
        return new AuthUserResponse(
                createToken(user),
                user.name(),
                user.employeeCode(),
                user.role(),
                user.position(),
                user.leaderEmployeeCode(),
                user.menuPermissions()
        );
    }

    private UserSession toSession(UserRecord user) {
        return new UserSession(user.name(), user.employeeCode(), user.role(), user.position(), user.leaderEmployeeCode(), user.menuPermissions());
    }

    private String createToken(UserRecord user) {
        String tokenText = user.employeeCode() + ":" + System.currentTimeMillis();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenText.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return employeeCode == null ? "" : employeeCode.trim().toUpperCase();
    }

    private String normalizeNullableEmployeeCode(String employeeCode) {
        String normalized = normalizeEmployeeCode(employeeCode);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private List<UserRecord> readAll() {
        List<UserRecord> users;
        if (!Files.exists(dataFile)) {
            users = new ArrayList<>();
        } else {
            try {
                users = new ArrayList<>(objectMapper.readValue(dataFile.toFile(), USER_LIST_TYPE));
            } catch (IOException error) {
                throw new IllegalStateException("读取用户数据失败", error);
            }
        }
        users = normalizeUsers(users);
        return ensureAdminUser(users);
    }

    private List<UserRecord> normalizeUsers(List<UserRecord> users) {
        return users.stream()
                .map(user -> {
                    String role = StringUtils.hasText(user.role()) ? user.role() : "EMPLOYEE";
                    String position = StringUtils.hasText(user.position()) ? user.position() : "OPERATION";
                    return new UserRecord(
                            user.name(),
                            normalizeEmployeeCode(user.employeeCode()),
                            user.password(),
                            role,
                            position,
                            normalizeNullableEmployeeCode(user.leaderEmployeeCode()),
                            normalizeMenus(user.menuPermissions(), role, position),
                            user.createdAt()
                    );
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private List<UserRecord> releaseMembers(List<UserRecord> users, String leaderEmployeeCode) {
        return users.stream()
                .map(user -> leaderEmployeeCode.equals(user.leaderEmployeeCode())
                        ? new UserRecord(
                        user.name(),
                        user.employeeCode(),
                        user.password(),
                        user.role(),
                        user.position(),
                        null,
                        user.menuPermissions(),
                        user.createdAt()
                )
                        : user)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private void validateLeader(List<UserRecord> users, String leaderEmployeeCode, String position) {
        UserRecord leader = users.stream()
                .filter(user -> user.employeeCode().equals(leaderEmployeeCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("直属组长不存在"));
        if (!"ADMIN".equals(leader.role()) && !"LEADER".equals(leader.role())) {
            throw new BusinessException("直属上级必须是组长或管理员");
        }
        if (!"ADMIN".equals(leader.role()) && !position.equals(leader.position())) {
            throw new BusinessException("员工只能挂到同部门组长下");
        }
    }

    private List<UserRecord> ensureAdminUser(List<UserRecord> users) {
        boolean exists = users.stream().anyMatch(user -> ADMIN_EMPLOYEE_CODE.equals(user.employeeCode()));
        if (exists) {
            return users;
        }
        users.add(new UserRecord(
                "admin",
                ADMIN_EMPLOYEE_CODE,
                ADMIN_PASSWORD,
                "ADMIN",
                "OPERATION",
                null,
                List.copyOf(MENUS),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        ));
        writeAll(users);
        return users;
    }

    private void writeAll(List<UserRecord> rows) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), rows);
        } catch (IOException error) {
            throw new IllegalStateException("保存用户数据失败", error);
        }
    }
}
