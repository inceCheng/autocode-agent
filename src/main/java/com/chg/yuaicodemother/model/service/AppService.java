package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.dto.app.AppQueryRequest;
import com.chg.yuaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.chg.yuaicodemother.model.entity.App;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface AppService extends IService<App> {

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
}
