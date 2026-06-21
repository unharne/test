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

import java.util.HashMap;
import java.util.Map;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    
    private static boolean isEnabled = true;
    private static final Map<BlockPos, Level> blocksToRemove = new HashMap<>();

    public WaterDropMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide) return;

        Player player = event.player;
        Level level = player.level();

        if (!blocksToRemove.isEmpty()) {
            blocksToRemove.forEach((pos, lvl) -> {
                if (lvl.getBlockState(pos).is(Blocks.WATER)) {
                    lvl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            });
            blocksToRemove.clear();
        }

        if (!isEnabled) return;

        if (player.fallDistance > 2.5F && player.getDeltaMovement().y < -0.5) {
            Vec3 pos = player.position();
            BlockPos blockUnder = BlockPos.containing(pos.x, player.getBoundingBox().minY - 0.5, pos.z);

            if (level.getBlockState(blockUnder).isAir()) {
                level.setBlock(blockUnder, Blocks.WATER.defaultBlockState(), 3);
                blocksToRemove.put(blockUnder, level);
                player.fallDistance = 0;
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