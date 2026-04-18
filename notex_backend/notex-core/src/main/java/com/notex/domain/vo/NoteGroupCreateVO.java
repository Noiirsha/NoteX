package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记分组创建结果VO
 * 用于返回创建笔记分组后的结果信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记分组创建结果")
public class NoteGroupCreateVO {

    /**
     * 分组ID
     * 数据库中的自增ID
     */
    @Schema(description = "分组ID", example = "1")
    private Long id;

}
