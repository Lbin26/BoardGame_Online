package org.dromara.game.common.match.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.game.common.match.entity.GamePlayerStatEntity;
import org.dromara.game.common.match.mapper.GamePlayerStatMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公共战绩写入：游客跳过、首次新建、再次累加（胜/负、最高分、累计分）。
 */
@ExtendWith(MockitoExtension.class)
class GamePlayerStatServiceTest {

    @Mock
    private GamePlayerStatMapper playerStatMapper;

    @InjectMocks
    private GamePlayerStatService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 10, 0);

    /** 单元测试没有 Spring 上下文，手动初始化 LambdaWrapper 的表元数据缓存 */
    @BeforeAll
    static void initTableCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GamePlayerStatEntity.class);
    }

    @Test
    void guestIsSkipped() {
        service.record(-1L, 10, true, NOW);
        service.record(0L, 10, true, NOW);
        verify(playerStatMapper, never()).insert(any(GamePlayerStatEntity.class));
        verify(playerStatMapper, never()).update(any(), any());
    }

    @Test
    void insertsNewStatOnFirstWin() {
        service.record(1001L, 42, true, NOW);

        ArgumentCaptor<GamePlayerStatEntity> captor = ArgumentCaptor.forClass(GamePlayerStatEntity.class);
        verify(playerStatMapper).insert(captor.capture());
        GamePlayerStatEntity row = captor.getValue();
        assertEquals(1001L, row.getUserId());
        assertEquals(1, row.getTotalMatches());
        assertEquals(1, row.getWinMatches());
        assertEquals(0, row.getLoseMatches());
        assertEquals(42, row.getHighestScore());
        assertEquals(42L, row.getTotalScore());
        assertEquals(NOW, row.getLastPlayTime());
        assertEquals("0", row.getDelFlag());
    }

    @Test
    void accumulatesOnLoss() {
        GamePlayerStatEntity existing = new GamePlayerStatEntity();
        existing.setId(9L);
        existing.setUserId(1001L);
        when(playerStatMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.record(1001L, 7, false, NOW);

        // 已有记录时走 update 累加，不重复 insert
        verify(playerStatMapper, never()).insert(any(GamePlayerStatEntity.class));
        verify(playerStatMapper).update(any(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
