package org.dromara.game.common.match.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.game.common.match.entity.GameActionLogEntity;

/**
 * 对局行动日志 数据层（M5 落库，只追加，全游戏共用）
 *
 * @author dicego
 */
public interface GameActionLogMapper extends BaseMapperPlus<GameActionLogEntity, GameActionLogEntity> {
}
