package com.example.waterdrop;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    
    // Переменные для хитбоксов
    private boolean enabled = false;
    private float hitboxSize = 2.0f; 

    // Переменная для быстрой стройки
    private boolean buildEnabled = false;

    public WaterDropMod() {
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

        // --- 1. ЛОГИКА УВЕЛИЧЕННЫХ ХИТБОКСОВ ---
        if (enabled) {
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

        // --- 2. ЛОГИКА БЫСТРОЙ СТРОЙКИ ПОД СОБОЙ ---
        if (buildEnabled && mc.gameMode != null) {
            // Получаем координаты блока ровно под ногами
            BlockPos posBelow = player.blockPosition().below();
            
            // Если под ногами пусто (игрок сходит с края)
            if (mc.level.getBlockState(posBelow).isAir()) {
                // Ищем соседний блок, к которому можно "прикрепить" новый
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = posBelow.relative(dir);
                    
                    if (!mc.level.getBlockState(neighbor).isAir()) {
                        // Создаем результат "наведения" на грань соседнего блока
                        BlockHitResult hitResult = new BlockHitResult(
                            new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5),
                            dir.getOpposite(),
                            neighbor,
                            false
                        );
                        
                        // Эмулируем клик ПКМ для установки блока из главной руки
                        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
                        
                        if (result.consumesAction()) {
                            // Взмах рукой для визуализации
                            player.swing(InteractionHand.MAIN_HAND);
                            break; // Успешно поставили блок, выходим из цикла
                        }
                    }
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
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("helpme")
                .then(net.minecraft.commands.Commands.argument("arg", StringArgumentType.string())
                    .executes(ctx -> {
                        String arg = StringArgumentType.getString(ctx, "arg");
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        
                        if (player == null) return 1;

                        // Команда для быстрой стройки
                        if (arg.equalsIgnoreCase("build")) {
                            buildEnabled = !buildEnabled;
                            player.displayClientMessage(Component.literal(buildEnabled ? "§aБыстрая стройка: ВКЛ" : "§cБыстрая стройка: ВЫКЛ"), false);
                            return 1;
                        }

                        // Команды для хитбоксов
                        if (arg.equals("1")) {
                            enabled = true;
                            player.displayClientMessage(Component.literal("§aУвеличение хитбоксов: ВКЛ (Текущий размер: " + hitboxSize + ")"), false);
                        } else if (arg.equals("0")) {
                            enabled = false;
                            resetHitboxes();
                            player.displayClientMessage(Component.literal("§cУвеличение хитбоксов: ВЫКЛ"), false);
                        } else {
                            try {
                                float size = Float.parseFloat(arg);
                                hitboxSize = size;
                                enabled = true;
                                player.displayClientMessage(Component.literal("§eРазмер хитбокса установлен на: " + size + " и активирован."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1, 0, число для размера хитбокса или 'build' для стройки."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
