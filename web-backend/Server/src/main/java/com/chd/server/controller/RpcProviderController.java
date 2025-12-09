package com.chd.server.controller;

import com.chd.server.Interface.AuditInterface;
import com.chd.server.Interface.ManipulationInterface;
import com.chd.server.Interface.SelectInterface;
import com.chd.server.dto.AuditLogDTO;
import com.chd.server.dto.OperationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rpc")
public class RpcProviderController {

    private static final Logger log = LoggerFactory.getLogger(RpcProviderController.class);

    @Autowired
    private SelectInterface selectInterface;

    @Autowired
    private ManipulationInterface manipulationInterface;

    @Autowired
    private AuditInterface auditInterface;

    /**
     * 单条记录查询接口
     */
    @PostMapping("/select")
    public Map<String, Object> select(@RequestBody OperationDTO dto) {
        log.info("RPC Select: table={}, conditions={}", dto.getTable(), dto.getConditions());
        try {
            return selectInterface.select(dto);
        } catch (Exception e) {
            log.error("Select error: {}", e.getMessage());
            return null; // 或者返回空Map
        }
    }

    /**
     * 多条记录查询接口
     */
    @PostMapping("/selectList")
    public List<Map<String, Object>> selectList(@RequestBody OperationDTO dto) {
        log.info("RPC SelectList: table={}", dto.getTable());
        return selectInterface.selectList(dto);
    }

    /**
     * 数据插入接口
     */
    @PostMapping("/manipulate/insert")
    public boolean insert(@RequestBody OperationDTO dto) {
        log.info("RPC Insert: table={}", dto.getTable());
        return manipulationInterface.insert(dto);
    }

    /**
     * 数据更新接口
     */
    @PostMapping("/manipulate/update")
    public boolean update(@RequestBody OperationDTO dto) {
        log.info("RPC Update: table={}", dto.getTable());
        return manipulationInterface.update(dto);
    }

    /**
     * 审计日志记录接口
     */
    @PostMapping("/audit")
    public boolean audit(@RequestBody AuditLogDTO dto) {
        log.info("RPC Audit: {} on {}", dto.getOperationType(), dto.getTableName());
        return auditInterface.logOperation(dto);
    }
}