package com.notex.service.impl;

import com.notex.domain.Response;
import com.notex.domain.dto.UserChangePreferencesDTO;
import com.notex.domain.po.UserPreferences;
import com.notex.domain.vo.UserPreferenceVO;
import com.notex.mapper.UserPreferencesMapper;
import com.notex.service.IUserPreferencesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.utils.UserIdContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
@Service
public class UserPreferencesServiceImpl extends ServiceImpl<UserPreferencesMapper, UserPreferences> implements IUserPreferencesService {

    @Override
    public Response<UserPreferenceVO> fetchUserPreferences() {
        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 获取数据
        UserPreferences userData = this.lambdaQuery()
                .eq(UserPreferences::getUserId, threadUserId)
                .select(UserPreferences::getDarkMode)
                .one();

        UserPreferenceVO userPreferenceVO = new UserPreferenceVO();
        BeanUtils.copyProperties(userData, userPreferenceVO);

        return Response.success(userPreferenceVO);
    }

    @Override
    public void changePreferences(UserChangePreferencesDTO data) {
        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        this.lambdaUpdate()
                .eq(UserPreferences::getUserId, threadUserId)
                .set(UserPreferences::getDarkMode, data.getDarkMode())
                .update();
    }
}
