package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.AdminUserUpdateRequest;
import com.tourcrm.dto.AuthLoginRequest;
import com.tourcrm.dto.AuthRegisterRequest;
import com.tourcrm.dto.AuthUserResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import com.tourcrm.dto.UserUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    public static final String MENU_THIRD_PARTY_POOL = "THIRD_PARTY_POOL";
    public static final String MENU_ORG = "ORG";
    public static final String MENU_MENUS = "MENUS";
    public static final String MENU_SETTINGS = "SETTINGS";

    private static final String ADMIN_EMPLOYEE_CODE = "ADMIN";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final Pattern EMPLOYEE_CODE_PATTERN = Pattern.compile("^[A-Z]{2,5}$");
    private static final Set<String> ROLES = Set.of("ADMIN", "LEADER", "EMPLOYEE");
    private static final Set<String> POSITIONS = Set.of("OPERATION", "SALES");
    private static final Set<String> ORG_TYPES = Set.of("HEADQUARTERS", "BRANCH");
    private static final Set<String> MENUS = Set.of(
            MENU_CLUES,
            MENU_CLUE_CREATE,
            MENU_DEALS,
            MENU_STATS,
            MENU_PERFORMANCE,
            MENU_USERS,
            MENU_ASSIGN,
            MENU_ASSIGN_LOGS,
            MENU_OPERATION_LOGS,
            MENU_THIRD_PARTY_POOL,
            MENU_ORG,
            MENU_MENUS,
            MENU_SETTINGS
    );
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final long tokenExpirationMinutes;
    private final boolean singleLogin;

    public AuthService(
            UserRepository userRepository,
            LoginSessionRepository loginSessionRepository,
            @Value("${app.jwt-expiration-minutes:1440}") long tokenExpirationMinutes,
            @Value("${app.auth.single-login:false}") boolean singleLogin
    ) {
        this.userRepository = userRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.tokenExpirationMinutes = tokenExpirationMinutes;
        this.singleLogin = singleLogin;
    }

    public AuthUserResponse login(AuthLoginRequest request) {
        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        Optional<UserRecord> matched = userRepository.findUserByEmployeeCode(employeeCode).map(this::normalizeUser);
        if (matched.isEmpty() || !matchesPassword(request.password(), matched.get().password())) {
            throw new BusinessException("员工编号或密码错误");
        }
        upgradePasswordIfNeeded(matched.get(), request.password());
        return toAuthResponse(matched.get());
    }

    public List<UserRecord> listUsers(String token) {
        requireAdmin(token);
        return readAll().stream()
                .sorted(Comparator.comparing(UserRecord::createdAt).reversed())
                .toList();
    }

    public PageResponse<UserRecord> listUsersPage(Integer page, Integer pageSize, String token) {
        requireAdmin(token);
        ensureAdminUser(new ArrayList<>(userRepository.readUsers()));
        PageResponse<UserRecord> pageResponse = userRepository.queryUsersPage(page, pageSize);
        return new PageResponse<>(
                normalizeUsers(pageResponse.records()),
                pageResponse.total(),
                pageResponse.page(),
                pageResponse.pageSize(),
                pageResponse.hasMore()
        );
    }

    List<UserRecord> listAllUsersForSystem() {
        return readAll();
    }

    public List<UserRecord> listLeaders(String token) {
        requireAdmin(token);
        return readAll().stream()
                .filter(user -> "LEADER".equals(user.role()) || "ADMIN".equals(user.role()))
                .sorted(Comparator.comparing(UserRecord::employeeCode))
                .toList();
    }

    public List<UserRecord> listSalesCandidates(String token) {
        if (!hasMenuPermission(token, MENU_ASSIGN) && !hasMenuPermission(token, MENU_DEALS)) {
            throw new BusinessException("没有分配管理权限，请联系管理员开通");
        }
        return readAll().stream()
                .filter(user -> "SALES".equals(user.position()))
                .sorted(Comparator.comparing(UserRecord::employeeCode))
                .toList();
    }

    public List<UserRecord> usersVisibleTo(String token) {
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

    @Transactional
    public UserRecord createUser(AuthRegisterRequest request, String token) {
        requireAdmin(token);
        validateRegisterRequest(request);
        List<UserRecord> users = readAll();
        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        if (userRepository.userExists(employeeCode)) {
            throw new BusinessException("员工编号已存在");
        }

        String role = normalizeRole(request.role(), "EMPLOYEE");
        String position = normalizePosition(request.position(), "OPERATION");
        String orgType = normalizeOrgType(request.orgType(), position);
        String branchId = normalizeBranchId(request.branchId(), orgType);
        String branchName = normalizeBranchName(request.branchName(), orgType);
        String leaderEmployeeCode = "EMPLOYEE".equals(role) ? normalizeNullableEmployeeCode(request.leaderEmployeeCode()) : null;
        if (StringUtils.hasText(leaderEmployeeCode)) {
            validateLeader(users, leaderEmployeeCode, position, orgType, branchId);
        }
        UserRecord user = new UserRecord(
                request.name().trim(),
                employeeCode,
                encodePassword(request.password()),
                role,
                position,
                leaderEmployeeCode,
                orgType,
                branchId,
                branchName,
                normalizeMenus(request.menuPermissions(), role, position),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        userRepository.writeUser(user);
        return user;
    }

    @Transactional
    public UserRecord updateUser(String employeeCode, AdminUserUpdateRequest request, String token) {
        requireAdmin(token);
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        List<UserRecord> users = readAll();
        Optional<UserRecord> oldOptional = userRepository.findUserByEmployeeCode(normalizedEmployeeCode).map(this::normalizeUser);
        if (oldOptional.isEmpty()) {
            throw new BusinessException("员工账号不存在");
        }
        UserRecord old = oldOptional.get();
        String role = normalizeRole(request.role(), old.role());
        String position = normalizePosition(request.position(), old.position());
        String orgType = normalizeOrgType(request.orgType(), position);
        String branchId = normalizeBranchId(request.branchId(), orgType);
        String branchName = normalizeBranchName(request.branchName(), orgType);
        String leaderEmployeeCode = normalizeNullableEmployeeCode(request.leaderEmployeeCode());
        if ("ADMIN".equals(old.employeeCode())) {
            role = "ADMIN";
            orgType = "HEADQUARTERS";
            branchId = null;
            branchName = null;
            leaderEmployeeCode = null;
        }
        if ("EMPLOYEE".equals(role) && (!position.equals(old.position()) || !sameOrg(orgType, branchId, old.orgType(), old.branchId()))) {
            leaderEmployeeCode = null;
        }
        if ("ADMIN".equals(role) || "LEADER".equals(role)) {
            leaderEmployeeCode = null;
        }
        if ("EMPLOYEE".equals(role) && StringUtils.hasText(leaderEmployeeCode)) {
            validateLeader(users, leaderEmployeeCode, position, orgType, branchId);
        }
        validateNameAndPassword(request.name(), request.password(), old.password());
        UserRecord updated = new UserRecord(
                request.name().trim(),
                old.employeeCode(),
                StringUtils.hasText(request.password()) ? encodePassword(request.password()) : old.password(),
                role,
                position,
                leaderEmployeeCode,
                orgType,
                branchId,
                branchName,
                normalizeMenus(request.menuPermissions(), role, position),
                old.createdAt()
        );
        userRepository.writeUser(updated);
        if ("LEADER".equals(old.role()) && (!"LEADER".equals(role) || !position.equals(old.position()))) {
            userRepository.releaseMembersByLeader(old.employeeCode());
        }
        if (StringUtils.hasText(request.password())) {
            loginSessionRepository.deleteSessionsByEmployeeCode(old.employeeCode());
        }
        return updated;
    }

    @Transactional
    public boolean deleteUser(String employeeCode, String token) {
        requireAdmin(token);
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        if (ADMIN_EMPLOYEE_CODE.equals(normalizedEmployeeCode)) {
            throw new BusinessException("管理员账号不能删除");
        }
        List<UserRecord> users = readAll();
        boolean removed = users.removeIf(user -> user.employeeCode().equals(normalizedEmployeeCode));
        if (removed) {
            userRepository.releaseMembersByLeader(normalizedEmployeeCode);
            userRepository.deleteUser(normalizedEmployeeCode);
        }
        return removed;
    }

    @Transactional
    public UserSession updateCurrentUser(UserUpdateRequest request, String token) {
        UserSession currentUser = currentUser(token);
        Optional<UserRecord> oldOptional = userRepository.findUserByEmployeeCode(currentUser.employeeCode()).map(this::normalizeUser);
        if (oldOptional.isEmpty()) {
            throw new BusinessException("当前账号不存在，请重新登录");
        }
        UserRecord old = oldOptional.get();
        validateNameAndPassword(request.name(), request.password(), old.password());
        UserRecord updated = new UserRecord(
                request.name().trim(),
                old.employeeCode(),
                StringUtils.hasText(request.password()) ? encodePassword(request.password()) : old.password(),
                old.role(),
                old.position(),
                old.leaderEmployeeCode(),
                old.orgType(),
                old.branchId(),
                old.branchName(),
                old.menuPermissions(),
                old.createdAt()
        );
        userRepository.writeUser(updated);
        if (StringUtils.hasText(request.password())) {
            loginSessionRepository.deleteSessionsByEmployeeCode(old.employeeCode());
        }
        return toSession(updated);
    }

    public UserSession currentUser(String token) {
        String resolvedToken = AuthTokenSupport.resolveFromCurrentRequest(token);
        if (StringUtils.hasText(resolvedToken)) {
            return currentUserByRawToken(resolvedToken);
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("请先登录");
        }
        String rawToken = token.replace("Bearer ", "").trim();
        Optional<String> employeeCode = loginSessionRepository.findSessionEmployeeCode(rawToken);
        if (employeeCode.isEmpty()) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return userRepository.findUserByEmployeeCode(employeeCode.get())
                .map(this::normalizeUser)
                .map(this::toSession)
                .orElseThrow(() -> new BusinessException("当前账号不存在，请重新登录"));
    }

    public void logout(String token) {
        String rawToken = AuthTokenSupport.resolveFromCurrentRequest(token);
        if (StringUtils.hasText(rawToken)) {
            loginSessionRepository.deleteSession(rawToken);
        }
    }

    public long sessionExpirationSeconds() {
        return tokenExpirationMinutes * 60;
    }

    private UserSession currentUserByRawToken(String rawToken) {
        Optional<String> employeeCode = loginSessionRepository.findSessionEmployeeCode(rawToken);
        if (employeeCode.isEmpty()) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return userRepository.findUserByEmployeeCode(employeeCode.get())
                .map(this::normalizeUser)
                .map(this::toSession)
                .orElseThrow(() -> new BusinessException("当前账号不存在，请重新登录"));
    }

    public boolean hasMenuPermission(String token, String menuCode) {
        UserSession currentUser = currentUser(token);
        return "ADMIN".equals(currentUser.role()) || currentUser.menuPermissions().contains(menuCode);
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

    private String normalizeOrgType(String orgType, String position) {
        if ("SALES".equals(position)) {
            return "HEADQUARTERS";
        }
        String normalized = StringUtils.hasText(orgType) ? orgType.trim().toUpperCase() : "HEADQUARTERS";
        if (!ORG_TYPES.contains(normalized)) {
            throw new BusinessException("组织类型不正确");
        }
        return normalized;
    }

    private String normalizeBranchId(String branchId, String orgType) {
        if (!"BRANCH".equals(orgType)) {
            return null;
        }
        if (!StringUtils.hasText(branchId)) {
            throw new BusinessException("分公司ID不能为空");
        }
        String normalized = branchId.trim().toUpperCase();
        if (!normalized.matches("^[A-Z0-9_-]{1,32}$")) {
            throw new BusinessException("分公司ID只能包含字母、数字、下划线或短横线");
        }
        return normalized;
    }

    private String normalizeBranchName(String branchName, String orgType) {
        if (!"BRANCH".equals(orgType)) {
            return null;
        }
        if (!StringUtils.hasText(branchName)) {
            throw new BusinessException("分公司名称不能为空");
        }
        return branchName.trim();
    }

    private List<String> normalizeMenus(List<String> menuPermissions, String role, String position) {
        if ("ADMIN".equals(role)) {
            return List.copyOf(MENUS);
        }
        List<String> source = (menuPermissions == null || menuPermissions.isEmpty()) ? defaultMenus(position) : menuPermissions;
        return source.stream()
                .filter(MENUS::contains)
                .distinct()
                .toList();
    }

    private List<String> defaultMenus(String position) {
        if ("SALES".equals(position)) {
            return List.of(MENU_ASSIGN, MENU_ASSIGN_LOGS, MENU_DEALS, MENU_STATS, MENU_PERFORMANCE);
        }
        return List.of(MENU_CLUES, MENU_CLUE_CREATE, MENU_ASSIGN, MENU_ASSIGN_LOGS, MENU_STATS, MENU_PERFORMANCE);
    }

    private AuthUserResponse toAuthResponse(UserRecord user) {
        String token = createToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpirationMinutes);
        loginSessionRepository.createSession(token, user.employeeCode(), expiresAt, singleLogin);
        return new AuthUserResponse(
                token,
                user.name(),
                user.employeeCode(),
                user.role(),
                user.position(),
                user.leaderEmployeeCode(),
                user.orgType(),
                user.branchId(),
                user.branchName(),
                user.menuPermissions(),
                expiresAt.format(DATE_TIME_FORMAT)
        );
    }

    private UserSession toSession(UserRecord user) {
        return new UserSession(user.name(), user.employeeCode(), user.role(), user.position(), user.leaderEmployeeCode(), user.orgType(), user.branchId(), user.branchName(), user.menuPermissions());
    }

    private String createToken() {
        return java.util.UUID.randomUUID() + "." + java.util.UUID.randomUUID();
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return PASSWORD_ENCODER.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private void upgradePasswordIfNeeded(UserRecord user, String rawPassword) {
        if (isBcrypt(user.password())) {
            return;
        }
        userRepository.findUserByEmployeeCode(user.employeeCode())
                .map(this::normalizeUser)
                .ifPresent(old -> userRepository.writeUser(new UserRecord(
                        old.name(),
                        old.employeeCode(),
                        encodePassword(rawPassword),
                        old.role(),
                        old.position(),
                        old.leaderEmployeeCode(),
                        old.orgType(),
                        old.branchId(),
                        old.branchName(),
                        old.menuPermissions(),
                        old.createdAt()
                )));
    }

    private String encodePassword(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    private boolean isBcrypt(String value) {
        return StringUtils.hasText(value) && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return employeeCode == null ? "" : employeeCode.trim().toUpperCase();
    }

    private String normalizeNullableEmployeeCode(String employeeCode) {
        String normalized = normalizeEmployeeCode(employeeCode);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private List<UserRecord> readAll() {
        List<UserRecord> users = new ArrayList<>(userRepository.readUsers());
        users = normalizeUsers(users);
        return ensureAdminUser(users);
    }

    private List<UserRecord> normalizeUsers(List<UserRecord> users) {
        return users.stream()
                .map(this::normalizeUser)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private UserRecord normalizeUser(UserRecord user) {
        String role = StringUtils.hasText(user.role()) ? user.role() : "EMPLOYEE";
        String position = StringUtils.hasText(user.position()) ? user.position() : "OPERATION";
        String orgType = normalizeOrgType(user.orgType(), position);
        return new UserRecord(
                user.name(),
                normalizeEmployeeCode(user.employeeCode()),
                user.password(),
                role,
                position,
                normalizeNullableEmployeeCode(user.leaderEmployeeCode()),
                orgType,
                normalizeBranchId(user.branchId(), orgType),
                normalizeBranchName(user.branchName(), orgType),
                normalizeMenus(user.menuPermissions(), role, position),
                user.createdAt()
        );
    }

    private void validateLeader(List<UserRecord> users, String leaderEmployeeCode, String position, String orgType, String branchId) {
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

    private boolean sameOrg(String leftType, String leftBranchId, String rightType, String rightBranchId) {
        String left = "BRANCH".equals(leftType) ? normalizeNullableText(leftBranchId) : "HQ";
        String right = "BRANCH".equals(rightType) ? normalizeNullableText(rightBranchId) : "HQ";
        return left.equals(right);
    }

    private String normalizeNullableText(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private List<UserRecord> ensureAdminUser(List<UserRecord> users) {
        boolean exists = users.stream().anyMatch(user -> ADMIN_EMPLOYEE_CODE.equals(user.employeeCode()));
        if (exists) {
            return users;
        }
        UserRecord admin = new UserRecord(
                "admin",
                ADMIN_EMPLOYEE_CODE,
                encodePassword(ADMIN_PASSWORD),
                "ADMIN",
                "OPERATION",
                null,
                "HEADQUARTERS",
                null,
                null,
                List.copyOf(MENUS),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        users.add(admin);
        userRepository.writeUser(admin);
        return users;
    }
}
