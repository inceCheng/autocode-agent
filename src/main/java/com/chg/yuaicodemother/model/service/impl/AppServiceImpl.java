package com.chg.yuaicodemother.model.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chg.yuaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.chg.yuaicodemother.ai.client.AiRouteClient;
import com.chg.yuaicodemother.ai.client.AiTittleClient;
import com.chg.yuaicodemother.constant.AppConstant;
import com.chg.yuaicodemother.constant.UserConstant;
import com.chg.yuaicodemother.core.AiCodeGeneratorFacade;
import com.chg.yuaicodemother.core.builder.VueProjectBuilder;
import com.chg.yuaicodemother.core.handler.StreamHandlerExecutor;
import com.chg.yuaicodemother.exception.BusinessException;
import com.chg.yuaicodemother.exception.ErrorCode;
import com.chg.yuaicodemother.exception.ThrowUtils;
import com.chg.yuaicodemother.kafka.AiTaskProducer;
import com.chg.yuaicodemother.model.dto.app.*;
import com.chg.yuaicodemother.model.entity.AiGenerationTask;
import com.chg.yuaicodemother.model.entity.AppVersion;
import com.chg.yuaicodemother.model.entity.User;
import com.chg.yuaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.model.service.ChatHistoryService;
import com.chg.yuaicodemother.model.vo.AppVO;
import com.chg.yuaicodemother.model.vo.AppVersionVO;
import com.chg.yuaicodemother.model.vo.UserVO;
import com.chg.yuaicodemother.utils.JwtUtils;
import com.chg.yuaicodemother.utils.TaskIdGenerator;
import com.chg.yuaicodemother.utils.TraceIdGenerator;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.chg.yuaicodemother.model.entity.App;
import com.chg.yuaicodemother.mapper.AppMapper;
import com.chg.yuaicodemother.model.service.AppService;
import jakarta.annotation.Resource;
import jakarta.websocket.OnClose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.chg.yuaicodemother.constant.AiGenerationTaskConstant.*;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserServiceImpl userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private AiRouteClient aiRouteClient;
    @Resource
    private AiTittleClient aiTittleClient;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AiGenerationTaskServiceImpl aiGenerationTaskService;
    @Resource
    private JwtUtils jwtUtils;
    @Resource
    private AiTaskProducer aiTaskProducer;
    @Resource
    private AppVersionServiceImpl appVersionService;

    @Override
    public AppAddResponse createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        try {
            app.setAppName(aiTittleClient.getAiTitle(initPrompt));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "标题创建失败");
        }
        // 使用 AI 智能选择代码生成类型 调用 Python 服务
        CodeGenTypeEnum projectType = null;
        try {
            projectType = aiRouteClient.getProjectType(initPrompt);
            app.setCodeGenType(projectType.getValue());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "路由失败");
        }

        // 设置预览路径
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth());
        app.setPreviewPath(datePath);
        // 插入数据库
        boolean result = this.save(app);
        Long appId = app.getId();
        boolean res = chatHistoryService.addChatMessage(appId, initPrompt, "user", loginUser.getId());
        ThrowUtils.throwIf(!res, ErrorCode.SYSTEM_ERROR, "聊天创建失败，请稍后重试～");
        // 返回 jwt，并生成 task id ，传入 Kafka 消息队列
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("prompt", initPrompt);
        claims.put("appId", String.valueOf(appId));
        String token = jwtUtils.generateToken(String.valueOf(loginUser.getId()), claims);
        // 生成 task id
        long taskId = new TaskIdGenerator(1).nextId();
        String traceId = TraceIdGenerator.generateTraceId();
        int versionNo = appVersionService.getNextVersionNo(appId);
        String sourcePath = appVersionService.buildSourcePath(datePath, projectType.getValue(), appId, versionNo);
        AppVersion appVersion = AppVersion.builder()
                .appId(appId)
                .versionNo(versionNo)
                .taskId(String.valueOf(taskId))
                .codeGenType(projectType.getValue())
                .sourcePath(sourcePath)
                .manifestPath(appVersionService.buildManifestPath(sourcePath))
                .previewUrl(appVersionService.buildPreviewUrl(sourcePath, projectType.getValue()))
                .status(PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        boolean versionSaved = appVersionService.save(appVersion);
        ThrowUtils.throwIf(!versionSaved, ErrorCode.SYSTEM_ERROR, "应用版本创建失败，请稍后重试～");
        AiGenerationTask aiGenerationTask = AiGenerationTask.builder()
                .appId(appId)
                .taskId(String.valueOf(taskId))
                .projectType(projectType.getValue())
                .taskType(TASK_TYPE_GENERATE)
                .targetVersionId(appVersion.getId())
                .status(PENDING)
                .userId(loginUser.getId())
                .retryCount(RETRY_COUNT)
                .maxRetryCount(MAX_RETRY_COUNT)
                .version(DEFAULT_VERSION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        // 状态落库
        boolean save = aiGenerationTaskService.save(aiGenerationTask);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "应用状态更新失败，请稍候重试～");
        App statusApp = new App();
        statusApp.setId(appId);
        statusApp.setCurrentTaskId(String.valueOf(taskId));
        statusApp.setGenerateStatus(PENDING);
        this.updateById(statusApp);
        //  发送 Kafka 消息
        aiTaskProducer.sendGenerationTask(String.valueOf(taskId),
                String.valueOf(loginUser.getId()),
                String.valueOf(appId), initPrompt,
                projectType.getValue(), traceId, datePath, appVersion.getId(), sourcePath);
        // 同步 redis 中 task 的状态
        stringRedisTemplate.opsForValue().set(STATUS_KEY_PREFIX + taskId, PENDING, TASK_TIMEOUT, TimeUnit.HOURS);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", appId, projectType.getValue());
        return new AppAddResponse(String.valueOf(taskId), appId, token, appVersion.getId());
    }

    @Override
    public AppEditCreateResponse createEditTask(AppEditCreateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求不能为空");
        Long appId = request.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getInstruction()), ErrorCode.PARAMS_ERROR, "修改要求不能为空");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限修改该应用");
        ThrowUtils.throwIf(StrUtil.isNotBlank(app.getCurrentTaskId())
                        && !SUCCESS.equals(app.getGenerateStatus())
                        && !FAILED.equals(app.getGenerateStatus())
                        && !CANCELLED.equals(app.getGenerateStatus())
                        && !INTERRUPTED.equals(app.getGenerateStatus()),
                ErrorCode.OPERATION_ERROR, "当前应用有任务正在执行，请稍后再试");

        Long baseVersionId = request.getBaseVersionId();
        AppVersion baseVersion;
        if (baseVersionId != null && baseVersionId > 0) {
            baseVersion = appVersionService.getById(baseVersionId);
        } else if (app.getCurrentVersionId() != null) {
            baseVersion = appVersionService.getById(app.getCurrentVersionId());
        } else {
            baseVersion = appVersionService.getCurrentSuccessVersion(appId);
        }
        ThrowUtils.throwIf(baseVersion == null, ErrorCode.NOT_FOUND_ERROR, "当前应用还没有可编辑的成功版本");
        ThrowUtils.throwIf(!appId.equals(baseVersion.getAppId()), ErrorCode.NO_AUTH_ERROR, "版本不属于当前应用");
        ThrowUtils.throwIf(!SUCCESS.equals(baseVersion.getStatus()), ErrorCode.OPERATION_ERROR, "只能基于成功版本进行修改");

        int nextVersionNo = appVersionService.getNextVersionNo(appId);
        long taskId = new TaskIdGenerator(1).nextId();
        String traceId = TraceIdGenerator.generateTraceId();
        String codeGenType = app.getCodeGenType();
        String targetSourcePath = appVersionService.buildSourcePath(app.getPreviewPath(), codeGenType, appId, nextVersionNo);
        AppVersion targetVersion = AppVersion.builder()
                .appId(appId)
                .parentVersionId(baseVersion.getId())
                .versionNo(nextVersionNo)
                .taskId(String.valueOf(taskId))
                .codeGenType(codeGenType)
                .sourcePath(targetSourcePath)
                .manifestPath(appVersionService.buildManifestPath(targetSourcePath))
                .previewUrl(appVersionService.buildPreviewUrl(targetSourcePath, codeGenType))
                .status(PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        boolean versionSaved = appVersionService.save(targetVersion);
        ThrowUtils.throwIf(!versionSaved, ErrorCode.SYSTEM_ERROR, "应用版本创建失败，请稍后重试～");

        String requestPayload = JSONUtil.toJsonStr(request);
        AiGenerationTask aiGenerationTask = AiGenerationTask.builder()
                .appId(appId)
                .taskId(String.valueOf(taskId))
                .projectType(codeGenType)
                .taskType(TASK_TYPE_EDIT)
                .baseVersionId(baseVersion.getId())
                .targetVersionId(targetVersion.getId())
                .status(PENDING)
                .userId(loginUser.getId())
                .retryCount(RETRY_COUNT)
                .maxRetryCount(MAX_RETRY_COUNT)
                .requestPayload(requestPayload)
                .version(DEFAULT_VERSION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        boolean taskSaved = aiGenerationTaskService.save(aiGenerationTask);
        ThrowUtils.throwIf(!taskSaved, ErrorCode.SYSTEM_ERROR, "修改任务创建失败，请稍后重试～");

        chatHistoryService.addChatMessage(appId, request.getInstruction(), ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        App statusApp = new App();
        statusApp.setId(appId);
        statusApp.setCurrentTaskId(String.valueOf(taskId));
        statusApp.setGenerateStatus(PENDING);
        this.updateById(statusApp);

        HashMap<String, Object> claims = new HashMap<>();
        claims.put("appId", String.valueOf(appId));
        claims.put("taskId", String.valueOf(taskId));
        claims.put("taskType", TASK_TYPE_EDIT);
        String token = jwtUtils.generateToken(String.valueOf(loginUser.getId()), claims);

        List<Object> selectedElements = request.getSelectedElements() == null
                ? Collections.emptyList()
                : request.getSelectedElements().stream().map(item -> (Object) item).toList();
        aiTaskProducer.sendEditTask(
                String.valueOf(taskId),
                String.valueOf(loginUser.getId()),
                String.valueOf(appId),
                request.getInstruction(),
                codeGenType,
                traceId,
                app.getPreviewPath(),
                baseVersion.getId(),
                targetVersion.getId(),
                baseVersion.getSourcePath(),
                targetSourcePath,
                selectedElements,
                StrUtil.blankToDefault(request.getScope(), "single")
        );
        stringRedisTemplate.opsForValue().set(STATUS_KEY_PREFIX + taskId, PENDING, TASK_TIMEOUT, TimeUnit.HOURS);
        return new AppEditCreateResponse(
                String.valueOf(taskId),
                appId,
                token,
                baseVersion.getId(),
                targetVersion.getId(),
                PENDING
        );
    }


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        app.setCodeGenType(app.getCodeGenType());
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 保存用户消息到聊天记录
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集 ai 响应内容并在完成后记录到对话历史聊天记录
        return streamHandlerExecutor.doExecute(contentFlux, chatHistoryService, appId, app.getPreviewPath(), loginUser, codeGenTypeEnum);
    }


    @Override
    public String deployApp(Long appId, User loginUser) {
        return deployAppVersion(appId, null, loginUser);
    }

    @Override
    public String deployAppVersion(Long appId, Long versionId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        AppVersion version = null;
        if (versionId != null && versionId > 0) {
            version = appVersionService.getById(versionId);
            ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "应用版本不存在");
            ThrowUtils.throwIf(!appId.equals(version.getAppId()), ErrorCode.NO_AUTH_ERROR, "版本不属于当前应用");
        } else if (app.getCurrentVersionId() != null) {
            version = appVersionService.getById(app.getCurrentVersionId());
        } else {
            version = appVersionService.getCurrentSuccessVersion(appId);
        }
        if (version != null) {
            ThrowUtils.throwIf(!SUCCESS.equals(version.getStatus()), ErrorCode.OPERATION_ERROR, "只能部署成功版本");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = version != null ? version.getCodeGenType() : app.getCodeGenType();
        String sourceDirPath = version != null
                ? String.format("%s/%s", AppConstant.CODE_OUTPUT_ROOT_DIR, version.getSourcePath())
                : String.format("%s/%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, app.getPreviewPath(), codeGenType, appId);
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (CodeGenTypeEnum.VUE_PROJECT == codeGenTypeEnum) {
            // Vue 项目需要构建
            boolean buildResult = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildResult, ErrorCode.SYSTEM_ERROR, "构建项目失败，请稍后重试～");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "项目构建成功，但 dist 目0录不存在，请检查构建脚本");
            sourceDir = distDir;
            log.info("项目构建成功，dist 目录路径：{}", distDir.getAbsolutePath());

        }
        String deployDirPath = String.format("%s/%s/%s", AppConstant.CODE_DEPLOY_ROOT_DIR, app.getPreviewPath(), deployKey);
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9.构建应用访问 url
        String url = String.format("%s/%s/%s/", AppConstant.CODE_DEPLOY_HOST, app.getPreviewPath(), deployKey);
        // 10.异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, url);
        // 11. 返回可访问的 url
        return url;
    }


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        if (app.getCurrentVersionId() != null) {
            AppVersion currentVersion = appVersionService.getById(app.getCurrentVersionId());
            appVO.setCurrentVersion(appVersionService.getVersionVO(currentVersion));
        }
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        Set<Long> versionIds = appList.stream()
                .map(App::getCurrentVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AppVersionVO> versionVOMap = versionIds.isEmpty()
                ? Collections.emptyMap()
                : appVersionService.listByIds(versionIds).stream()
                .collect(Collectors.toMap(AppVersion::getId, appVersionService::getVersionVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            if (app.getCurrentVersionId() != null) {
                appVO.setCurrentVersion(versionVOMap.get(app.getCurrentVersionId()));
            }
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public AppVersionVO getCurrentVersionVO(Long appId, User loginUser) {
        App app = validateAppAccess(appId, loginUser);
        AppVersion version = app.getCurrentVersionId() == null
                ? appVersionService.getCurrentSuccessVersion(appId)
                : appVersionService.getById(app.getCurrentVersionId());
        return appVersionService.getVersionVO(version);
    }

    @Override
    public List<AppVersionVO> listVersionVO(Long appId, User loginUser) {
        validateAppAccess(appId, loginUser);
        return appVersionService.listVersionVO(appId);
    }

    private App validateAppAccess(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isOwner = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isOwner, ErrorCode.NO_AUTH_ERROR, "无权访问该应用");
        return app;
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用 ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            // 即使对话历史删除失败，也不会阻止应用的删除操作，只是记录错误日志，确保核心业务的稳定性
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

}
