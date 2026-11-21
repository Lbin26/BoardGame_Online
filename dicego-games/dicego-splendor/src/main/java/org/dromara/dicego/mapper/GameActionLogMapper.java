package org.dromara.dicego.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.dicego.domain.entity.GameActionLogEntity;

/**
 * 对局行动日志 数据层（M5 落库，只追加）
 *
 * @author dicego
 */
public interface GameActionLogMapper extends BaseMapperPlus<GameActionLogEntity, GameActionLogEntity> {
}
