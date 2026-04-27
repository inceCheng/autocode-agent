package com.chg.yuaicodemother.model.service;

import com.chg.yuaicodemother.model.entity.AppVersion;
import com.chg.yuaicodemother.model.vo.AppVersionVO;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 应用版本服务。
 */
public interface AppVersionService extends IService<AppVersion> {

    int getNextVersionNo(Long appId);

    AppVersion getCurrentSuccessVersion(Long appId);

    List<AppVersionVO> listVersionVO(Long appId);

    AppVersionVO getVersionVO(AppVersion version);

    String buildSourcePath(String previewPath, String codeGenType, Long appId, Integer versionNo);

    String buildManifestPath(String sourcePath);

    String buildPreviewUrl(String sourcePath, String codeGenType);
}
