package com.chd.score.remote.server.Interface;

import com.chd.score.remote.client.dto.OperationDTO;

public interface ManipulationInterface {
    boolean update(OperationDTO dto); // 更新操作
}