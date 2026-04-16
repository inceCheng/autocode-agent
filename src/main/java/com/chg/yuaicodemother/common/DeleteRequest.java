package com.chg.yuaicodemother.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 删除请求
 */
@Data
@NoArgsConstructor // 🌟 必须加上这个：生成无参构造函数
@AllArgsConstructor // （可选）如果你之前写了带参构造，建议加上这个
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
