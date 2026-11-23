package org.dromara.web.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.dromara.web.domain.vo.GameDefinitionVO;

import java.util.List;

/**
 * 游戏定义只读查询：大厅 NEW 图标的唯一数据来源。
 *
 * <p>只取正常状态且未删除的定义；create_time 可由运维在 game_definition 表中维护，
 * 新增游戏登记时的创建时间即视为上线时间。
 */
@Mapper
public interface DicegoGameDefinitionMapper {

    @Select("""
        select game_code as gameCode,
               game_name as gameName,
               create_time as createTime
          from game_definition
         where status = '0' and del_flag = '0'
         order by sort_no asc, id asc
        """)
    List<GameDefinitionVO> listDefinitions();
}
