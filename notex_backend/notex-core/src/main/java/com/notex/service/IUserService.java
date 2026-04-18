package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.*;
import com.notex.domain.po.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.UserAiModelsVO;
import com.notex.domain.vo.UserInformationVO;
import com.notex.domain.vo.UserLoginVO;
import com.notex.domain.vo.UserPreferenceVO;

/**
 * 用户服务接口
 * 定义用户登录、注册、信息修改等核心功能
 */
public interface IUserService extends IService<User> {

    /**
     * 用户登录
     * 使用用户名和密码进行登录认证
     *
     * @param user 登录请求DTO
     * @return 登录结果，包含用户信息和JWT令牌
     */
    Response<UserLoginVO> login(UserLoginDTO user);

    /**
     * 用户注册
     * 注册新用户账号
     *
     * @param user 注册请求DTO
     */
    void registerUser(UserRegisterDTO user);

    /**
     * 修改密码
     * 修改用户的登录密码
     *
     * @param user 修改密码请求DTO
     */
    void changePassword(UserChangePasswordDTO user);

    /**
     * 修改个人信息
     * 修改用户的基本信息，如昵称和头像
     *
     * @param user 修改信息请求DTO
     */
    void changeInformation(UserChangeInformationDTO user);

    /**
     * 获取用户信息
     * 获取当前登录用户的详细信息
     *
     * @return 用户信息
     */
    Response<UserInformationVO> fetchUserInformation();
}
