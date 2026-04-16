package com.chg.yuaicodemother.common;

import com.chg.yuaicodemother.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用响应类
 *
 * @param <T>
 */
@Data
@NoArgsConstructor // 🌟 必须加上这个：生成无参构造函数
@AllArgsConstructor // （可选）如果你之前写了带参构造，建议加上这个
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;


    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
