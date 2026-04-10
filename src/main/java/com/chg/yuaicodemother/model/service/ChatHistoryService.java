package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.chg.yuaicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.chg.yuaicodemother.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加聊天消息的方法
     *
     * @param appId       应用ID，标识消息所属的应用
     * @param message     聊天消息的具体内容
     * @param messageType 消息的类型，如文本、图片等
     * @param userId      发送消息的用户 ID
     * @return 返回操作是否成功，成功返回true，失败返回false
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 分页查询应用聊天记录
     *
     * @param appId          应用ID，用于指定要查询哪个应用的聊天记录
     * @param pageSize       每页记录数，用于控制返回结果的数量
     * @param lastCreateTime 上一次查询的最后一条记录的创建时间，用于分页查询
     * @param loginUser      登录用户信息，用于权限验证
     * @return 返回一个包含聊天记录的Page对象，支持分页功能
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 将聊天历史记录加载到内存中
     *
     * @param appId      应用程序ID，用于标识特定的应用程序实例
     * @param chatMemory 聊天记忆对象，用于存储加载的聊天历史
     * @param maxCount   最大加载的消息数量限制
     * @return 返回实际加载到内存中的消息数量
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 根据聊天历史查询请求参数获取查询包装器
     *
     * @param chatHistoryQueryRequest 聊天历史查询请求对象，包含查询条件
     * @return 返回一个QueryWrapper对象，用于构建数据库查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 根据应用 ID 删除相关数据
     *
     * @param appId 应用ID，用于标识需要删除的应用
     * @return 删除操作是否成功执行，true表示成功，false表示失败
     */
    boolean deleteByAppId(Long appId);
}
