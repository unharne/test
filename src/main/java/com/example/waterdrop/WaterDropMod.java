package com.example.waterdrop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    
    private static boolean isEnabled = true;
    // Используем простой список координат вместо хранения тяжелых объектов мира, чтобы избежать утечек и крашей
    private static final List<BlockPos> blocksToRemove = new ArrayList<>();

    public WaterDropMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Проверяем сторону строго через встроенный логический метод Forge
        if (event.phase != TickEvent.Phase.START || event.side.isClient()) return;

        Player player = event.player;
        if (player == null) return;

        // getCommandSenderWorld() — самый стабильный метод во всех сборках 1.20.1, обходящий баги маппингов
        Level level = player.getCommandSenderWorld();
        if (level == null) return;

        // Безопасная очистка блоков воды с прошлых тиков
        if (!blocksToRemove.isEmpty()) {
            for (BlockPos pos : new ArrayList<>(blocksToRemove)) {
                if (level.getBlockState(pos).is(Blocks.WATER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            blocksToRemove.clear();
        }

        if (!isEnabled) return;

        // Проверка падения: если игрок летит вниз с высоты более 2.5 блоков
        if (player.fallDistance > 2.5F && player.getDeltaMovement().y < -0.5) {
            Vec3 pos = player.position();
            // Находим блок строго под хитбоксом ног персонажа
            BlockPos blockUnder = BlockPos.containing(pos.x, player.getBoundingBox().minY - 0.5, pos.z);

            if (level.getBlockState(blockUnder).isAir()) {
                level.setBlock(blockUnder, Blocks.WATER.defaultBlockState(), 3);
                blocksToRemove.add(blockUnder);
                player.fallDistance = 0; // Полностью обнуляем урон от падения
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("wd")
                .then(Commands.argument("state", IntegerArgumentType.integer(0, 1))
                    .executes(context -> {
                        int state = IntegerArgumentType.getInteger(context, "state");
                        isEnabled = (state == 1);
                        
                        String message = isEnabled ? "§aWaterDrop включен!" : "§cWaterDrop выключен!";
                        context.getSource().sendSuccess(() -> Component.literal(message), false);
                        return 1;
                    })
                )
        );
    }
}
