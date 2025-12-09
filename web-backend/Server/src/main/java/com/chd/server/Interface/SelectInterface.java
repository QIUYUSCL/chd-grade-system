package com.chd.server.Interface;

import com.chd.server.dto.OperationDTO;
import java.util.List;
import java.util.Map;

public interface SelectInterface {
    Map<String, Object> select(OperationDTO dto);

    // 查询多条记录
    List<Map<String, Object>> selectList(OperationDTO dto);
}