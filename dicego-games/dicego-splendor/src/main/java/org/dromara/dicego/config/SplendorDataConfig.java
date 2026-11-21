package org.dromara.dicego.config;

import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.domain.entity.SplendorCardEntity;
import org.dromara.dicego.domain.entity.SplendorNobleEntity;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.mapper.SplendorCardMapper;
import org.dromara.dicego.mapper.SplendorNobleMapper;
import org.dromara.dicego.service.SplendorCardCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * D4 落地：启动时从 splendor_card / splendor_noble 种子表加载并校验（fail-fast），
 * 构建只读缓存供每局发牌。张数与 game.yaml 校验口径一致：40/30/20 + 10。
 */
@Configuration
public class SplendorDataConfig {

    /** DB bonus/need 列的色值编码：0白 1蓝 2绿 3红 4黑（与表注释一致） */
    private static final GemColor[] BONUS_CODES = {
        GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK
    };

    @Bean
    public SplendorCardCache splendorCardCache(SplendorCardMapper cardMapper, SplendorNobleMapper nobleMapper) {
        Map<Integer, List<SplendorCardDef>> byLevel = new HashMap<>();
        for (SplendorCardEntity e : cardMapper.selectList(cardOrderById())) {
            SplendorCardDef def = toCardDef(e);
            byLevel.computeIfAbsent(def.level(), k -> new ArrayList<>()).add(def);
        }
        List<SplendorNobleDef> nobles = nobleMapper.selectList(nobleOrderById()).stream()
            .map(this::toNobleDef)
            .toList();

        // fail-fast 校验：种子不全直接拒绝启动（口径 = tools splendor-official.spec.ts）
        int l1 = byLevel.getOrDefault(1, List.of()).size();
        int l2 = byLevel.getOrDefault(2, List.of()).size();
        int l3 = byLevel.getOrDefault(3, List.of()).size();
        if (l1 != 40 || l2 != 30 || l3 != 20) {
            throw new IllegalStateException(
                "splendor_card 种子数据不完整: 一级 %d/40, 二级 %d/30, 三级 %d/20，请检查 script/sql/boardgame_online.sql"
                    .formatted(l1, l2, l3));
        }
        if (nobles.size() != 10) {
            throw new IllegalStateException("splendor_noble 种子数据不完整: %d/10".formatted(nobles.size()));
        }

        byLevel.replaceAll((k, v) -> List.copyOf(v));
        return new SplendorCardCache(Map.copyOf(byLevel), nobles);
    }

    private SplendorCardDef toCardDef(SplendorCardEntity e) {
        if (e.getBonus() == null || e.getBonus() < 0 || e.getBonus() >= BONUS_CODES.length) {
            throw new IllegalStateException("splendor_card " + e.getCardCode() + " 加成色编码非法: " + e.getBonus());
        }
        Map<GemColor, Integer> cost = new EnumMap<>(GemColor.class);
        cost.put(GemColor.WHITE, nz(e.getCostWhite()));
        cost.put(GemColor.BLUE, nz(e.getCostBlue()));
        cost.put(GemColor.GREEN, nz(e.getCostGreen()));
        cost.put(GemColor.RED, nz(e.getCostRed()));
        cost.put(GemColor.BLACK, nz(e.getCostBlack()));
        return new SplendorCardDef(e.getCardCode(), e.getCardLevel(), BONUS_CODES[e.getBonus()],
            nz(e.getPrestige()), Map.copyOf(cost));
    }

    private SplendorNobleDef toNobleDef(SplendorNobleEntity e) {
        Map<GemColor, Integer> need = new EnumMap<>(GemColor.class);
        need.put(GemColor.WHITE, nz(e.getNeedWhite()));
        need.put(GemColor.BLUE, nz(e.getNeedBlue()));
        need.put(GemColor.GREEN, nz(e.getNeedGreen()));
        need.put(GemColor.RED, nz(e.getNeedRed()));
        need.put(GemColor.BLACK, nz(e.getNeedBlack()));
        return new SplendorNobleDef(e.getNobleCode(), e.getNobleName(), nz(e.getPrestige()), Map.copyOf(need));
    }

    private int nz(Integer n) {
        return n == null ? 0 : n;
    }

    /** 种子行按 id 升序读，保证 deck 顺序稳定（id 1..90 即 game.yaml 录入序） */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SplendorCardEntity> cardOrderById() {
        return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SplendorCardEntity>()
            .orderByAsc(SplendorCardEntity::getId);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SplendorNobleEntity> nobleOrderById() {
        return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SplendorNobleEntity>()
            .orderByAsc(SplendorNobleEntity::getId);
    }
}
