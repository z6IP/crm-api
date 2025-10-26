package com.crm.service;

import com.crm.vo.FileUrlVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhz
 */
public interface CommonService {
    FileUrlVO upload(MultipartFile multipartFile);
}
