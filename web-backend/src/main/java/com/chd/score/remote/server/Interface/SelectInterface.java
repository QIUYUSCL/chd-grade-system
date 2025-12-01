package com.chd.score.remote.server.Interface;

import com.chd.score.remote.client.dto.OperationDTO;
import java.util.Map;

public interface SelectInterface {
    Map<String, Object> select(OperationDTO dto);
}