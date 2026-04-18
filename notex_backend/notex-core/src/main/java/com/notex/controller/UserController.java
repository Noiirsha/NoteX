package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.dto.*;
import com.notex.domain.vo.*;
import com.notex.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户登录、注册、信息修改、偏好设置等功能
 */
@RestController()
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户登录、注册、密码修改等接口")
public class UserController {

    private final IUserService userService;

    private final IUserPreferencesService userPreferencesService;

    private final IUserAiModelsService userAiModelsService;

    private final IUserChatsService userChatsService;

    private final IRAGService ragService;

    /**
     * 登录校验
     *
     * @param user 账户或密码
     * @return JWT Token
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码进行登录，返回JWT Token")
    public Response<UserLoginVO> login(@Valid @RequestBody UserLoginDTO user) {
        return userService.login(user);
    }

    /**
     * 注册新账号
     *
     * @param user 用户新账号信息
     * @return 空成功请求体
     */
    @PostMapping("/register_new_account")
    @Operation(summary = "用户注册", description = "注册新用户账号")
    public Response<Void> registerNewAccount(@Valid @RequestBody UserRegisterDTO user) {

        userService.registerUser(user);

        return Response.success();
    }

    /**
     * 修改密码
     *
     * @param user 旧密码，新密码Hash
     * @return 空成功请求体
     */
    @PutMapping("/change_password")
    @Operation(summary = "修改密码", description = "修改用户密码，需要提供旧密码验证")
    public Response<Void> changePassword(@Valid @RequestBody UserChangePasswordDTO user) {

        userService.changePassword(user);

        return Response.success();
    }

    /**
     * 修改个人信息
     *
     * @param user 昵称，头像信息
     * @return 空成功请求体
     */
    @PutMapping("/change_information")
    @Operation(summary = "修改个人信息", description = "可以修改昵称或头像信息")
    public Response<Void> changeInformation(@Valid @RequestBody UserChangeInformationDTO user) {

        userService.changeInformation(user);

        return Response.success();
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息VO
     */
    @GetMapping("/fetch_user_information")
    @Operation(summary = "获取用户信息", description = "获取用户信息")
    public Response<UserInformationVO> fetchUserInformation() {
        return userService.fetchUserInformation();
    }

    /**
     * 获取用户偏好设定
     *
     * @return 用户偏好设定VO
     */
    @GetMapping("/fetch_user_preferences")
    @Operation(summary = "获取用户偏好", description = "获取用户偏好")
    public Response<UserPreferenceVO> fetchUserPreferences() {
        return userPreferencesService.fetchUserPreferences();
    }

    /**
     * 获取用户模型配置信息
     *
     * @return 用户模型配置VO
     */
    @GetMapping("/fetch_user_ai_models")
    @Operation(summary = "获取用户模型配置", description = "获取用户模型配置")
    public Response<UserAiModelsVO> fetchUserAiModels() {
        return userAiModelsService.fetchUserAiModels();
    }

    /**
     * 修改用户偏好设定
     *
     * @param data 用户偏好设定数据
     * @return 空成功请求体
     */
    @PutMapping("/change_preferences")
    @Operation(summary = "修改用户偏好", description = "修改用户偏好设定，如黑暗模式等")
    public Response<Void> changePreferences(@RequestBody UserChangePreferencesDTO data) {
        userPreferencesService.changePreferences(data);

        return Response.success();
    }

    /**
     * 修改用户AI模型配置
     *
     * @param data AI模型配置数据
     * @return 空成功请求体
     */
    @PutMapping("/change_ai_model")
    @Operation(summary = "修改AI模型配置", description = "修改用户的AI模型配置，包括标准模型和嵌入模型")
    public Response<Void> changeAiModels(
            @RequestBody UserChangeAiModelsDTO data,
            @RequestParam(value = "changed_embedded_model", defaultValue = "false") boolean changedEmbeddedModel
    ) {
        userAiModelsService.changeAiModels(data);
        if (changedEmbeddedModel) ragService.deleteUserVector();
        return Response.success();
    }

    /**
     * 获取用户聊天历史列表
     * 获取用户的所有聊天会话记录
     *
     * @return 聊天会话列表
     */
    @GetMapping("/fetch_chats")
    @Operation(summary = "获取聊天历史", description = "获取用户的所有聊天会话记录")
    public Response<List<ChatHistoryVO>> fetchChatHistory() {
        return userChatsService.fetchChatHistory();
    }


}
