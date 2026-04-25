package com.chg.yuaicodemother.model.service.impl;

import com.chg.yuaicodemother.mapper.AiGenerationTaskMapper;
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

    @Override
    public void updateTaskStatus(String taskId, Long appId, Long userId, String status, String errorMsg) {
        AiGenerationTask entity = new AiGenerationTask();
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());

        if (isTerminal(status)) {
            entity.setFinishedAt(LocalDateTime.now());
            // 如果是 SUCCESS  状态，自动触发部署
            if (SUCCESS.equals(status)) {
                appService.deployApp(appId, userService.getById(userId));
                log.info("自动部署完成: appId={}, userId={}, status={}", appId, userId, status);
            }
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
    }

    private boolean isTerminal(String status) {
        return SUCCESS.equals(status) || FAILED.equals(status) || INTERRUPTED.equals(status);
    }
}
