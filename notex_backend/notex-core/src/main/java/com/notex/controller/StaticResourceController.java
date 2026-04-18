package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.vo.ImageSaveVO;
import com.notex.service.IImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 静态资源管理控制器
 * 提供图片上传和下载功能
 */
@RestController
@RequestMapping("/static")
@RequiredArgsConstructor
@Tag(name = "静态资源管理", description = "图片上传和下载接口")
public class StaticResourceController {

    private final IImageService imageService;

    /**
     * 保存图片
     * 注意：请在前端将图片转换为webp格式后再上传
     *
     * @param file 图片文件
     * @return 图片保存结果VO，包含图片UUID
     */
    @PostMapping("/save_image")
    @Operation(summary = "保存图片", description = "上传并保存图片文件，建议使用webp格式")
    public Response<ImageSaveVO> saveImage(@RequestParam(value = "file") MultipartFile file) {
        return imageService.saveImage(file);
    }

    /**
     * 获取图片
     *
     * @param filename 图片文件名
     * @return 图片文件的二进制数据
     */
    @GetMapping("/get_image")
    @Operation(summary = "获取图片", description = "根据文件名获取图片文件")
    public ResponseEntity<byte[]> getImage(
            @RequestParam(value = "filename") String filename
    ) {
        return imageService.getImage(filename);
    }


}
