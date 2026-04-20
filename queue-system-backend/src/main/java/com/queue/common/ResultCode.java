package com.queue.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "success"),
    UNAUTHORIZED(401, "未授权或权限不足"),
    FORBIDDEN(403, "禁止访问"),
    DUPLICATE_TICKET(40001, "该手机号已有排队号码"),
    INVALID_BUSINESS_TYPE(40002, "业务类型不存在或已停用"),
    TICKET_NOT_FOUND(40003, "票号不存在"),
    INVALID_STATE_TRANSITION(40004, "当前状态不允许此操作"),
    COUNTER_NOT_OPERABLE(40005, "窗口当前不可操作"),
    QUEUE_EMPTY(40006, "暂无等待客户"),
    SYSTEM_ERROR(50001, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
