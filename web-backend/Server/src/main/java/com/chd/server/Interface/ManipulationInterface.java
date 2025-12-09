package com.chd.server.Interface;

import com.chd.server.dto.OperationDTO;

public interface ManipulationInterface {
    boolean update(OperationDTO dto); // 更新操作
    boolean insert(OperationDTO dto);
}