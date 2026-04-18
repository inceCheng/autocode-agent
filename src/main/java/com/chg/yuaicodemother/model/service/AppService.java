package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.dto.app.AppAddRequest;
import com.chg.yuaicodemother.model.dto.app.AppAddResponse;
import com.chg.yuaicodemother.model.dto.app.AppQueryRequest;
import com.chg.yuaicodemother.model.entity.User;
import com.chg.yuaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.chg.yuaicodemother.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface AppService extends IService<App> {
    /**
     * 创建应用的方法
     *
     * @param appAddRequest 包含应用创建所需信息的请求对象
     * @param loginUser     当前登录用户信息
     * @return 创建成功后返回的应用ID(Long类型)
     */
    AppAddResponse createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 应用聊天生成代码 流式 SSE
     *
     * @param appId     应用 id
     * @param message   提示词
     * @param loginUser 登录用户
     * @return 流式响应
     */

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用生成代码部署
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 应用 URL
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 应用详情
     *
     * @param app 应用
     * @return 脱敏后应用详情
     */
    AppVO getAppVO(App app);

    /**
     * 查询对象
     *
     * @param appQueryRequest 查询参数
     * @return 查询对象
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);


    /**
     * 根据 id 获取应用详情
     *
     * @param appList 应用列表
     * @return 脱敏后用户列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    void generateAppScreenshotAsync(Long appId, String appUrl);
}
