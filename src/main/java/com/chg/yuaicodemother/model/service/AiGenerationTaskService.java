package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.entity.AiGenerationTask;
import com.chg.yuaicodemother.kafka.TaskResultEvent;
import com.mybatisflex.core.service.IService;

/**
 * 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface AiGenerationTaskService extends IService<AiGenerationTask> {

    /**
     * 根据 taskId 更新任务状态
     *
     * @param taskId   任务 ID
     * @param status   新状态
     * @param errorMsg 错误信息（仅 FAILED 状态时有效）
     */
    void updateTaskStatus(String taskId, Long appId, Long userId, String status, String errorMsg);

    /**
     * 根据 Python 回传事件更新任务、版本和 app 快照状态。
     */
    void updateTaskStatus(TaskResultEvent event);
}
