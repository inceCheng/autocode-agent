package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.dto.user.UserQueryRequest;
import com.chg.yuaicodemother.model.vo.LoginUserVO;
import com.chg.yuaicodemother.model.vo.UserVO;
import com.chg.yuaicodemother.model.entity.User;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);


    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 根据User实体对象获取UserVO视图对象
     *
     * @param user User实体对象，包含用户的基本信息
     * @return UserVO 视图对象，通常用于前端展示，可能包含与实体对象不同的数据结构或字段
     */
    UserVO getUserVO(User user);

    /**
     * 根据用户列表获取用户视图对象列表
     *
     * @param userList 用户实体列表
     * @return 用户视图对象列表，包含用户展示所需信息
     */
    List<UserVO> getUserVOList(List<User> userList);


    /**
     * 获取查询条件
     *
     * @param userQueryRequest 用户查询请求
     * @return 包装好的 MyBatis-Plus 查询条件
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return 脱敏后的用户信息
     */
    LoginUserVO getLoginUserVO(User user);

}
