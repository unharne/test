package com.example.waterdrop;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(LegitScaffoldMod.MODID)
public class LegitScaffoldMod {
    public static final String MODID = "legitscaffold";
    
    private boolean enabled = false;
    private int placeDelayTicks = 2; // Задержка между кликами ПКМ (в тиках)
    private int currentDelay = 0;
    private boolean isSneakingByMod = false; // Флаг, чтобы мод не мешал нажимать Shift руками

    public LegitScaffoldMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // Откат задержки клика
        if (currentDelay > 0) {
            currentDelay--;
        }

        // Если мод выключен, сбрасываем залипший шифт
        if (!enabled) {
            if (isSneakingByMod) {
                mc.options.keyShift.setDown(false);
                isSneakingByMod = false;
            }
            return;
        }

        // Проверяем, держит ли игрок блок в любой из рук
        boolean holdingBlock = player.getMainHandItem().getItem() instanceof BlockItem || 
                               player.getOffhandItem().getItem() instanceof BlockItem;

        // Scaffold работает только если игрок на земле и держит блок
        if (holdingBlock && player.onGround()) {
            
            // Проверяем блок ровно под ногами игрока (смещение на 0.05 вниз)
            BlockPos underPos = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
            boolean isAirBelow = mc.level.getBlockState(underPos).isAir();

            if (isAirBelow) {
                // Мы над пропастью! Прожимаем Shift, если игрок не нажал его сам
                if (!mc.options.keyShift.isDown() && !isSneakingByMod) {
                    mc.options.keyShift.setDown(true);
                    isSneakingByMod = true;
                }

                // Легитная авто-установка блока (работает, только если вы навели прицел на другой блок)
                if (currentDelay <= 0 && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                    
                    // Имитируем нажатие ПКМ
                    mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
                    player.swing(InteractionHand.MAIN_HAND);
                    
                    // Вешаем кулдаун на клики
                    currentDelay = placeDelayTicks;
                }
            } else {
                // Под ногами есть блок, отпускаем Shift, чтобы игрок мог быстро идти дальше
                if (isSneakingByMod) {
                    mc.options.keyShift.setDown(false);
                    isSneakingByMod = false;
                }
            }
        } else {
            // Если перестали держать блок или подпрыгнули
            if (isSneakingByMod) {
                mc.options.keyShift.setDown(false);
                isSneakingByMod = false;
            }
        }
    }

    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("scaffold")
                .then(net.minecraft.commands.Commands.argument("arg", StringArgumentType.string())
                    .executes(ctx -> {
                        String arg = StringArgumentType.getString(ctx, "arg");
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        
                        if (player == null) return 1;

                        if (arg.equals("1")) {
                            enabled = true;
                            player.displayClientMessage(Component.literal("§aLegit Scaffold: ВКЛ (Задержка: " + placeDelayTicks + ")"), false);
                        } else if (arg.equals("0")) {
                            enabled = false;
                            if (isSneakingByMod) {
                                mc.options.keyShift.setDown(false);
                                isSneakingByMod = false;
                            }
                            player.displayClientMessage(Component.literal("§cLegit Scaffold: ВЫКЛ"), false);
                        } else {
                            try {
                                // Парсим число как задержку (в тиках)
                                int delay = Integer.parseInt(arg);
                                placeDelayTicks = Math.max(0, delay);
                                enabled = true;
                                player.displayClientMessage(Component.literal("§eЗадержка клика Scaffold установлена на: " + placeDelayTicks + " тиков."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1 (вкл), 0 (выкл) или целое число для задержки в тиках."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
