package org.dromara.game.common.match.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.game.common.match.entity.GameActionLogEntity;
import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameActionLogMapper;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.dromara.game.common.room.GameRoomManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 对局清理：超时对局与解散对局都逻辑删除（del_flag=1），仍在进行且房间存在的对局保留。 */
@ExtendWith(MockitoExtension.class)
class GameMatchCleanupTaskTest {

    @Mock
    private GameMatchMapper matchMapper;
    @Mock
    private GameMatchPlayerMapper matchPlayerMapper;
    @Mock
    private GameActionLogMapper actionLogMapper;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 16, 0);

    /** 单元测试没有 Spring 上下文，手动给 MyBatis-Plus 的 LambdaWrapper 初始化表元数据缓存 */
    @BeforeAll
    static void initTableCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameMatchEntity.class);
        TableInfoHelper.initTableInfo(assistant, GameMatchPlayerEntity.class);
        TableInfoHelper.initTableInfo(assistant, GameActionLogEntity.class);
    }

    private static GameMatchEntity match(long id, String status, String roomNo, LocalDateTime createTime) {
        GameMatchEntity entity = new GameMatchEntity();
        entity.setId(id);
        entity.setMatchNo("M" + id);
        entity.setMatchStatus(status);
        entity.setRoomNo(roomNo);
        entity.setCreateTime(createTime);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static GameRoomManager<?> managerWithRooms(String... roomIds) {
        GameRoomManager<?> manager = mock(GameRoomManager.class);
        when(manager.listRoomIds()).thenReturn(List.of(roomIds));
        return manager;
    }

    private GameMatchCleanupTask task(List<GameRoomManager<?>> managers) {
        return new GameMatchCleanupTask(matchMapper, matchPlayerMapper, actionLogMapper, managers);
    }

    @Test
    void 超过一小时的对局连同子表被清理() {
        GameMatchEntity stale = match(1L, "2", "ABC123", NOW.minusHours(2));
        // 第一次 selectList：超时对局；第二次：未收尾对局
        when(matchMapper.selectList(any())).thenReturn(List.of(stale), List.of());

        int removed = task(List.of(managerWithRooms())).cleanup(NOW, 1);

        assertEquals(1, removed);
        verify(matchPlayerMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(actionLogMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(matchMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void 房间已解散的进行中对局被清理() {
        GameMatchEntity dissolved = match(2L, "1", "GONE01", NOW.minusMinutes(10));
        when(matchMapper.selectList(any())).thenReturn(List.of(), List.of(dissolved));

        int removed = task(List.of(managerWithRooms("OTHER1"))).cleanup(NOW, 1);

        assertEquals(1, removed);
        verify(matchMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void 房间仍存在的进行中对局保留() {
        GameMatchEntity running = match(3L, "1", "ALIVE1", NOW.minusMinutes(10));
        when(matchMapper.selectList(any())).thenReturn(List.of(), List.of(running));

        int removed = task(List.of(managerWithRooms("ALIVE1"))).cleanup(NOW, 1);

        assertEquals(0, removed);
        verify(matchMapper, org.mockito.Mockito.never()).update(any(), any(LambdaUpdateWrapper.class));
    }
}
