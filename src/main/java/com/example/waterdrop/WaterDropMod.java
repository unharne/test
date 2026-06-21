package com.example.waterdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod(WaterDropMod.MODID)
@EventBusSubscriber(value = Dist.CLIENT, modid = WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    private static boolean waterPlaced = false;
    private static BlockPos placedPos = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 1. Логика установки (за 0.5 блока до земли)
        if (player.fallDistance > 3.0F && player.getDeltaMovement().y < -0.5) {
            if (!waterPlaced) {
                BlockPos pos = BlockPos.containing(player.position().x, player.getBoundingBox().minY - 0.5, player.position().z);
                
                // Шлем пакет на сервер: "Я кликнул водой сюда"
                mc.getConnection().send(new ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(0.5, 1, 0.5), Direction.UP, pos, false),
                    0
                ));
                
                waterPlaced = true;
                placedPos = pos;
            }
        }

        // 2. Логика моментального забора (через 10 тиков / 0.5 секунды)
        if (waterPlaced && player.onGround()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                placedPos,
                Direction.UP
            ));
            waterPlaced = false;
            placedPos = null;
        }
    }
}
