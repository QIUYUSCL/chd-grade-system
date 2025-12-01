// 在远程服务端Interface包中新建
package com.chd.score.remote.server.Interface;

import com.chd.score.remote.client.dto.AuditLogDTO;

public interface AuditInterface {
    /**
     * 记录安全审计日志（服务端执行）
     * @return 是否记录成功
     */
    boolean logOperation(AuditLogDTO dto);
}