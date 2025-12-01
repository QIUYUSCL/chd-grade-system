package com.chd.score.remote.server.Interface;

import com.chd.score.remote.client.dto.OperationDTO;
import java.util.List;
import java.util.Map;

public interface SelectInterface {
    Map<String, Object> select(OperationDTO dto);

    // 查询多条记录
    List<Map<String, Object>> selectList(OperationDTO dto);
}