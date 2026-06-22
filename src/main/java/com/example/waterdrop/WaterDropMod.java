package com.example.waterdrop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ScaffoldMod.MODID)
public class ScaffoldMod {
    public static final String MODID = "scaffold";
    private static boolean isEnabled = true;
    private int placeCooldown = 0;

    public ScaffoldMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        if (!isEnabled) return;

        // Строим только если в основной руке удерживается блок
        if (!(player.getMainHandItem().getItem() instanceof BlockItem)) return;

        // Позиция блока строго под ногами игрока
        BlockPos blockBelow = BlockPos.containing(player.getX(), player.getY() - 1.0D, player.getZ());

        // Если под нами воздух — ищем куда поставить блок
        if (mc.level.getBlockState(blockBelow).isAir()) {
            BlockPos targetPos = null;
            Direction targetFace = null;

            // Сканируем смежные стороны, чтобы найти существующий блок для клика
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = blockBelow.relative(dir);
                if (!mc.level.getBlockState(neighbor).isAir() && mc.level.getFluidState(neighbor).isEmpty()) {
                    targetPos = neighbor;
                    targetFace = dir.getOpposite(); // Кликаем по встречной грани блока-соседа
                    break;
                }
            }

            // Если нашли за что зацепиться — ставим блок
            if (targetPos != null) {
                Vec3 hitVec = Vec3.atCenterOf(targetPos).add(Vec3.atLowerCornerOf(targetFace.getNormal()).scale(0.5D));
                BlockHitResult hitResult = new BlockHitResult(hitVec, targetFace, targetPos, false);

                // Легитное взаимодействие с миром через GameMode
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
                player.swing(InteractionHand.MAIN_HAND);
                
                // Задержка в 1 тик для имитации человеческого клика и стабильности
                placeCooldown = 1; 
            }
        }
    }

    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("hack")
                .then(net.minecraft.commands.Commands.literal("scaffold")
                    .then(net.minecraft.commands.Commands.argument("state", IntegerArgumentType.integer(0, 1))
                        .executes(ctx -> {
                            isEnabled = IntegerArgumentType.getInteger(ctx, "state") == 1;
                            LocalPlayer p = Minecraft.getInstance().player;
                            if (p != null) {
                                p.displayClientMessage(Component.literal(isEnabled ? "§aScaffold ВКЛ" : "§cScaffold ВЫКЛ"), false);
                            }
                            return 1;
                        })
                    )
                )
        );
    }
}
