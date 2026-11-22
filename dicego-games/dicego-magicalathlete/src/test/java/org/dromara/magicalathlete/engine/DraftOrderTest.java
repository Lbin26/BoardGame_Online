package org.dromara.magicalathlete.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 蛇形选序断言（普通 3-6 人 + 2 人局变体） */
class DraftOrderTest {

    @Test
    void normalThreePlayersSnakeBothBatches() {
        List<Integer> batch0 = DraftOrder.snake(3, 0);
        assertEquals(List.of(1, 2, 3, 3, 2, 1), batch0);
        assertEquals(batch0, DraftOrder.snake(3, 1), "普通模式两批选序相同");
    }

    @Test
    void normalSixPlayersEachPicksFourTotal() {
        List<Integer> batch = DraftOrder.snake(6, 0);
        assertEquals(12, batch.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 6, 5, 4, 3, 2, 1), batch);
        // 两批 × 每批每人 2 张 = 4 张
        for (int i = 1; i <= 6; i++) {
            final int seat = i;
            long count = List.of(DraftOrder.snake(6, 0), DraftOrder.snake(6, 1)).stream()
                .flatMap(List::stream).filter(s -> s == seat).count();
            assertEquals(4, count, "座位 " + seat + " 应抽到 4 名");
        }
    }

    @Test
    void twoPlayerBatchesAreMirror() {
        assertEquals(List.of(1, 2, 2, 1, 1, 2, 2, 1), DraftOrder.snake(2, 0));
        assertEquals(List.of(2, 1, 1, 2, 2, 1, 1, 2), DraftOrder.snake(2, 1));
    }

    @Test
    void poolAndRacerCounts() {
        assertEquals(8, DraftOrder.poolSize(2));
        assertEquals(8, DraftOrder.racersPerPlayer(2));
        for (int n = 3; n <= 6; n++) {
            assertEquals(2 * n, DraftOrder.poolSize(n));
            assertEquals(4, DraftOrder.racersPerPlayer(n));
        }
        assertEquals(2, DraftOrder.batchCount());
    }

    @Test
    void rejectInvalidPlayerCount() {
        assertThrows(IllegalArgumentException.class, () -> DraftOrder.snake(1, 0));
        assertThrows(IllegalArgumentException.class, () -> DraftOrder.snake(7, 0));
    }
}
