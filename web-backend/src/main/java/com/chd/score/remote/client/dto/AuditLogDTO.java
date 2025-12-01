// 在远程客户端dto包中新建
package com.chd.score.remote.client.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class AuditLogDTO {
    @NotBlank
    private String operationType;  // PASSWORD_CHANGE / PASSWORD_RESET
    @NotBlank
    private String tableName;      // students/teachers/admins
    @NotBlank
    private String recordId;       // 被操作记录ID
    @NotBlank
    private String operatorId;     // 操作人ID
    @NotBlank
    private String operatorType;   // STUDENT/TEACHER/ADMIN
    private String clientIp;       // 客户端IP
}