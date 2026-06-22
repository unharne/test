package com.example.legitscaffold;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
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
    
    // Переменные для функционала Хитбоксов (/helpme)
    private boolean hitboxEnabled = false;
    private float hitboxSize = 2.0f;

    // Переменные для функционала Скэффолда (/scaffold)
    private boolean scaffoldEnabled = false;
    private int placeDelayTicks = 2; 
    private int currentDelay = 0;
    private boolean isSneakingByMod = false; 

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

        // ===============================
        // 1. ЛОГИКА УВЕЛИЧЕНИЯ ХИТБОКСОВ
        // ===============================
        if (hitboxEnabled) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity == player) continue;

                double w = hitboxSize / 2.0;
                entity.setBoundingBox(new AABB(
                    entity.getX() - w,
                    entity.getY(), 
                    entity.getZ() - w,
                    entity.getX() + w,
                    entity.getY() + hitboxSize, 
                    entity.getZ() + w
                ));
            }
        }

        // ===============================
        // 2. ЛОГИКА ЛЕГИТНОГО СКЭФФОЛДА
        // ===============================
        if (currentDelay > 0) {
            currentDelay--;
        }

        if (!scaffoldEnabled) {
            if (isSneakingByMod) {
                mc.options.keyShift.setDown(false);
                isSneakingByMod = false;
            }
        } else {
            boolean holdingBlock = player.getMainHandItem().getItem() instanceof BlockItem || 
                                   player.getOffhandItem().getItem() instanceof BlockItem;

            if (holdingBlock && player.onGround()) {
                BlockPos underPos = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
                boolean isAirBelow = mc.level.getBlockState(underPos).isAir();

                if (isAirBelow) {
                    if (!mc.options.keyShift.isDown() && !isSneakingByMod) {
                        mc.options.keyShift.setDown(true);
                        isSneakingByMod = true;
                    }

                    if (currentDelay <= 0 && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
                        player.swing(InteractionHand.MAIN_HAND);
                        currentDelay = placeDelayTicks;
                    }
                } else {
                    if (isSneakingByMod) {
                        mc.options.keyShift.setDown(false);
                        isSneakingByMod = false;
                    }
                }
            } else {
                if (isSneakingByMod) {
                    mc.options.keyShift.setDown(false);
                    isSneakingByMod = false;
                }
            }
        }
    }

    private void resetHitboxes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player) {
                entity.refreshDimensions();
            }
        }
    }

    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent event) {
        // Регистрация команды /helpme
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("helpme")
                .then(net.minecraft.commands.Commands.argument("arg", StringArgumentType.string())
                    .executes(ctx -> {
                        String arg = StringArgumentType.getString(ctx, "arg");
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        
                        if (player == null) return 1;

                        if (arg.equals("1")) {
                            hitboxEnabled = true;
                            player.displayClientMessage(Component.literal("§aУвеличение хитбоксов: ВКЛ (Текущий размер: " + hitboxSize + ")"), false);
                        } else if (arg.equals("0")) {
                            hitboxEnabled = false;
                            resetHitboxes();
                            player.displayClientMessage(Component.literal("§cУвеличение хитбоксов: ВЫКЛ"), false);
                        } else {
                            try {
                                float size = Float.parseFloat(arg);
                                hitboxSize = size;
                                hitboxEnabled = true; 
                                player.displayClientMessage(Component.literal("§eРазмер хитбокса установлен на: " + size + " и активирован."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1 (вкл), 0 (выкл) или число для размера."), false);
                            }
                        }
                        return 1;
                    })
                )
        );

        // Регистрация команды /scaffold
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("scaffold")
                .then(net.minecraft.commands.Commands.argument("arg", StringArgumentType.string())
                    .executes(ctx -> {
                        String arg = StringArgumentType.getString(ctx, "arg");
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        
                        if (player == null) return 1;

                        if (arg.equals("1")) {
                            scaffoldEnabled = true;
                            player.displayClientMessage(Component.literal("§aLegit Scaffold: ВКЛ (Задержка: " + placeDelayTicks + ")"), false);
                        } else if (arg.equals("0")) {
                            scaffoldEnabled = false;
                            if (isSneakingByMod) {
                                mc.options.keyShift.setDown(false);
                                isSneakingByMod = false;
                            }
                            player.displayClientMessage(Component.literal("§cLegit Scaffold: ВЫКЛ"), false);
                        } else {
                            try {
                                int delay = Integer.parseInt(arg);
                                placeDelayTicks = Math.max(0, delay);
                                scaffoldEnabled = true;
                                player.displayClientMessage(Component.literal("§eЗадержка клика Scaffold установлена на: " + placeDelayTicks + " тиков."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1 (вкл), 0 (выкл) или целое число для задержки."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
