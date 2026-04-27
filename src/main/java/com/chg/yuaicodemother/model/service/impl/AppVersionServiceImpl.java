package com.chg.yuaicodemother.model.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.chg.yuaicodemother.mapper.AppVersionMapper;
import com.chg.yuaicodemother.model.entity.AppVersion;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.model.service.AppVersionService;
import com.chg.yuaicodemother.model.vo.AppVersionVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.chg.yuaicodemother.constant.AiGenerationTaskConstant.SUCCESS;

/**
 * 应用版本服务实现。
 */
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion> implements AppVersionService {

    @Override
    public int getNextVersionNo(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("versionNo", false)
                .limit(1);
        AppVersion latest = this.getOne(queryWrapper);
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    @Override
    public AppVersion getCurrentSuccessVersion(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("status", SUCCESS)
                .orderBy("versionNo", false)
                .limit(1);
        return this.getOne(queryWrapper);
    }

    @Override
    public List<AppVersionVO> listVersionVO(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("versionNo", false);
        return this.list(queryWrapper).stream().map(this::getVersionVO).toList();
    }

    @Override
    public AppVersionVO getVersionVO(AppVersion version) {
        if (version == null) {
            return null;
        }
        AppVersionVO vo = new AppVersionVO();
        BeanUtil.copyProperties(version, vo);
        return vo;
    }

    @Override
    public String buildSourcePath(String previewPath, String codeGenType, Long appId, Integer versionNo) {
        String parent = StrUtil.blankToDefault(previewPath, "default").replaceAll("^/+|/+$", "");
        String projectDir = codeGenType + "_" + appId;
        if (versionNo != null && versionNo > 1) {
            projectDir = projectDir + "_v" + versionNo;
        }
        return parent + "/" + projectDir;
    }

    @Override
    public String buildManifestPath(String sourcePath) {
        return sourcePath + "/.ai/manifest.json";
    }

    @Override
    public String buildPreviewUrl(String sourcePath, String codeGenType) {
        if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(codeGenType)) {
            return "/static/" + sourcePath + "/dist/index.html#/";
        }
        return "/static/" + sourcePath + "/index.html";
    }
}
