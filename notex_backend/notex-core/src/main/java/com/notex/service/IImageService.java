package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.vo.ImageSaveVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface IImageService {
    Response<ImageSaveVO> saveImage(MultipartFile file);

    ResponseEntity<byte[]> getImage(String filename);
}
