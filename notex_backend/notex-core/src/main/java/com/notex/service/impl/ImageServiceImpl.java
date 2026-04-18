package com.notex.service.impl;

import cn.hutool.core.lang.UUID;
import com.notex.context.ImageContext;
import com.notex.domain.Response;
import com.notex.domain.vo.ImageSaveVO;
import com.notex.exception.BusinessException;
import com.notex.service.IImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ImageServiceImpl implements IImageService {

    @Value("${notex.images.storage.base-path}")
    private String basePath;

    @Override
    public Response<ImageSaveVO> saveImage(MultipartFile file) {

        // 生成图片随机UUID
        String fileUuid = generateFilename(file);
        String timeDateString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileSavingName = fileUuid + "_" + timeDateString;
        Path target = Paths.get(basePath).resolve(fileSavingName);

        // 保存
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }

        ImageSaveVO imageSaveVO = new ImageSaveVO();
        imageSaveVO.setImageUuid(fileUuid + "_" + timeDateString);

        return Response.success(imageSaveVO);
    }

    @Override
    public ResponseEntity<byte[]> getImage(String filename) {

        // 获取图片
        try {
            byte[] bytes = readImage(filename);
            String contentType = ImageContext.IMAGE_CONTENT_TYPE_WEBP;
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(bytes);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }

    }

    /**
     * 读取图片
     * @param filename 文件名
     * @return 图片字节数组
     */
    private byte[] readImage(String filename) throws IOException {
        Path path = Paths.get(basePath).resolve(filename);

        if (!Files.exists(path)) {
            throw new FileNotFoundException(ImageContext.IMAGE_NOT_FOUND_PREFIX + filename);
        }

        return Files.readAllBytes(path);
    }


    private String generateFilename(MultipartFile file) {
        return UUID.randomUUID() + "";
    }
}
