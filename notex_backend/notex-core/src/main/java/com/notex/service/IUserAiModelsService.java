package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.UserChangeAiModelsDTO;
import com.notex.domain.po.UserAiModels;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.UserAiModelsVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
public interface IUserAiModelsService extends IService<UserAiModels> {

    Response<UserAiModelsVO> fetchUserAiModels();

    void changeAiModels(UserChangeAiModelsDTO data);
}
