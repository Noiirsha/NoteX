package com.notex.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.notex.context.UserContext;
import com.notex.domain.Response;
import com.notex.domain.dto.*;
import com.notex.domain.po.User;
import com.notex.domain.po.UserAiModels;
import com.notex.domain.po.UserPreferences;
import com.notex.domain.vo.UserInformationVO;
import com.notex.domain.vo.UserLoginVO;
import com.notex.exception.BusinessException;
import com.notex.mapper.UserMapper;
import com.notex.service.IUserAiModelsService;
import com.notex.service.IUserPreferencesService;
import com.notex.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.utils.JwtUtils;
import com.notex.utils.UserIdContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 用户服务实现类
 * 实现用户登录、注册、信息修改等核心逻辑
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Value("${notex.jwt.secret}")
    private String jwtSecret;

    /**
     * 用户登录
     * 使用用户名和密码进行登录认证，成功后生成JWT令牌
     *
     * @param user 登录请求DTO
     * @return 登录结果，包含用户信息和JWT令牌
     */
    @Override
    public Response<UserLoginVO> login(UserLoginDTO user) {
        // 在数据库中查找用户
        User match = this.lambdaQuery()
                .eq(User::getUsername, user.getUsername())
                .eq(User::getPasswordHash, user.getPasswordHash())
                .one();

        if (match == null) {
            // 用户名或密码不正确
            throw new BusinessException(HttpStatus.UNAUTHORIZED, UserContext.USERNAME_OR_PASSWORD_ERROR);
        }

        // 生成JWT Token
        String token = JwtUtils.createToken(
                match.getId().toString(),
                Duration.ofHours(12),
                jwtSecret
        );

        // 构建返回数据
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(match, userLoginVO);
        userLoginVO.setToken(token);

        return Response.success(userLoginVO);
    }

    /**
     * 用户注册
     * 注册新用户账号，同时创建用户偏好设置和AI模型配置
     *
     * @param user 注册请求DTO
     */
    @Override
    @Transactional
    public void registerUser(UserRegisterDTO user) {

        // 数据校验位于前端
        // 查看时候已经存在对应的用户
        boolean usernameExist = this.lambdaQuery()
                .eq(User::getUsername, user.getUsername())
                .exists();

        if (usernameExist) {
            throw new BusinessException(HttpStatus.CONFLICT, UserContext.USERNAME_ALREADY_EXISTS);
        }

        boolean emailExist = this.lambdaQuery()
                .eq(User::getEmail, user.getEmail())
                .exists();

        if (emailExist) {
            throw new BusinessException(HttpStatus.CONFLICT, UserContext.EMAIL_ALREADY_EXISTS);
        }

        // 新建用户
        User newUser = new User();
        BeanUtils.copyProperties(user, newUser);
        this.save(newUser);

        // 新建个性化
        UserPreferences userPreferences = new UserPreferences();
        userPreferences.setUserId(newUser.getId())
                .setDarkMode(user.getDarkMode());
        Db.save(userPreferences);

        // 新建用户Api存储
        UserAiModels userAiModels = new UserAiModels();
        userAiModels.setUserId(newUser.getId());
        Db.save(userAiModels);

    }

    /**
     * 修改密码
     * 验证旧密码后更新为新密码
     *
     * @param user 修改密码请求DTO
     */
    @Override
    public void changePassword(UserChangePasswordDTO user) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查旧密码是否正确
        boolean exists = this.lambdaQuery()
                .eq(User::getId, threadUserId)
                .eq(User::getPasswordHash, user.getOldPasswordHash())
                .exists();

        if (!exists) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, UserContext.PASSWORD_INCORRECT);
        }

        // 更新密码
        this.lambdaUpdate()
                .eq(User::getId, threadUserId)
                .set(User::getPasswordHash, user.getNewPasswordHash())
                .update();
    }

    /**
     * 修改个人信息
     * 修改用户的基本信息，如昵称和头像
     *
     * @param user 修改信息请求DTO
     */
    @Override
    public void changeInformation(UserChangeInformationDTO user) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 更新数据
        this.lambdaUpdate()
                .eq(User::getId, threadUserId)
                .set(StrUtil.isNotBlank(user.getNickname()), User::getNickname, user.getNickname())
                .set(User::getAvatarImageUrl, user.getAvatarImageUrl()) // 头像如果为空，则使用默认值
                .update();

    }

    /**
     * 获取用户信息
     * 获取当前登录用户的详细信息
     *
     * @return 用户信息
     */
    @Override
    public Response<UserInformationVO> fetchUserInformation() {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 获取数据
        User userData = this.lambdaQuery()
                .eq(User::getId, threadUserId)
                .select(
                        User::getUsername, User::getNickname, User::getEmail,
                        User::getAvatarImageUrl, User::getRegisterTime, User::getLastLoginTime
                ).one();

        UserInformationVO userInformationVO = new UserInformationVO();
        BeanUtils.copyProperties(userData, userInformationVO);

        return Response.success(userInformationVO);
    }

}
