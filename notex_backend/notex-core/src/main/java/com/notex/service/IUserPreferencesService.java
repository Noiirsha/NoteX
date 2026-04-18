package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.UserChangePreferencesDTO;
import com.notex.domain.po.UserPreferences;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.UserPreferenceVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
public interface IUserPreferencesService extends IService<UserPreferences> {

    Response<UserPreferenceVO> fetchUserPreferences();

    void changePreferences(UserChangePreferencesDTO data);
}
