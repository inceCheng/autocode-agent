package com.chg.yuaicodemother.model.service.impl;

import com.chg.yuaicodemother.mapper.AiGenerationTaskMapper;
import com.chg.yuaicodemother.kafka.TaskResultEvent;
import com.chg.yuaicodemother.model.entity.App;
import com.chg.yuaicodemother.model.entity.AppVersion;
import com.chg.yuaicodemother.model.entity.AiGenerationTask;
import com.chg.yuaicodemother.model.service.AiGenerationTaskService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.chg.yuaicodemother.constant.AiGenerationTaskConstant.*;

/**
 * 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Slf4j
@Service
public class AiGenerationTaskServiceImpl extends ServiceImpl<AiGenerationTaskMapper, AiGenerationTask> implements AiGenerationTaskService {
    @Resource
    @Lazy
    private AppServiceImpl appService;
    @Resource
    private UserServiceImpl userService;
    @Resource
    private AppVersionServiceImpl appVersionService;

    @Override
    public void updateTaskStatus(String taskId, Long appId, Long userId, String status, String errorMsg) {
        TaskResultEvent event = TaskResultEvent.builder()
                .taskId(taskId)
                .appId(appId)
                .userId(userId)
                .status(status)
                .errorMsg(errorMsg)
                .build();
        updateTaskStatus(event);
    }

    @Override
    public void updateTaskStatus(TaskResultEvent event) {
        String taskId = event.getTaskId();
        String status = event.getStatus();
        String errorMsg = event.getErrorMsg();
        AiGenerationTask entity = new AiGenerationTask();
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());

        if (isTerminal(status)) {
            entity.setFinishedAt(LocalDateTime.now());
        }
        if (FAILED.equals(status) && errorMsg != null) {
            entity.setErrorMessage(errorMsg);
        }

        QueryWrapper queryWrapper = QueryWrapper.create().eq("taskId", taskId);
        int rows = this.mapper.updateByQuery(entity, queryWrapper);
        if (rows == 0) {
            log.warn("更新任务状态未找到对应记录, taskId={}, status={}", taskId, status);
        } else {
            log.debug("更新任务状态成功, taskId={}, status={}", taskId, status);
        }

        AiGenerationTask task = this.getOne(QueryWrapper.create().eq("taskId", taskId));
        if (task != null && isTerminal(status)) {
            updateVersionAndAppSnapshot(task, status, errorMsg);
        }
    }

    private boolean isTerminal(String status) {
        return SUCCESS.equals(status) || FAILED.equals(status) || INTERRUPTED.equals(status);
    }

    private void updateVersionAndAppSnapshot(AiGenerationTask task, String status, String errorMsg) {
        Long targetVersionId = task.getTargetVersionId();
        if (targetVersionId != null) {
            AppVersion version = new AppVersion();
            version.setId(targetVersionId);
            version.setStatus(SUCCESS.equals(status) ? SUCCESS : FAILED);
            version.setUpdateTime(LocalDateTime.now());
            appVersionService.updateById(version);
        }

        App app = new App();
        app.setId(task.getAppId());
        app.setCurrentTaskId("");
        app.setGenerateStatus(status);
        if (SUCCESS.equals(status) && targetVersionId != null) {
            app.setCurrentVersionId(targetVersionId);
        }
        appService.updateById(app);

        if (SUCCESS.equals(status)) {
            try {
                appService.deployApp(task.getAppId(), userService.getById(task.getUserId()));
                log.info("自动部署完成: appId={}, userId={}, status={}", task.getAppId(), task.getUserId(), status);
            } catch (Exception e) {
                log.warn("自动部署失败但版本已成功: appId={}, taskId={}, error={}", task.getAppId(), task.getTaskId(), e.getMessage());
            }
        } else if (errorMsg != null) {
            log.warn("任务失败: taskId={}, error={}", task.getTaskId(), errorMsg);
        }
    }
}
