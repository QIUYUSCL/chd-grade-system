package com.chd.server.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class OperationDTO {
    @NotBlank(message = "操作类型不能为空")
    private String operation;  // SELECT/INSERT/UPDATE/DELETE

    @NotBlank(message = "表名不能为空")
    private String table;      // students/teachers

    private Map<String, Object> data;       // 操作数据
    private Map<String, Object> conditions; // 查询条件
    private String role;        // 操作人角色
}