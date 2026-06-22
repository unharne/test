package com.example.waterdrop;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
    
    private boolean enabled = false;
    private float hitboxSize = 2.0f; // Размер хитбокса по умолчанию при включении

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
        if (player == null || mc.level == null || !enabled) return;

        // Перебираем всех сущностей в зоне рендера клиента
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == player) continue;

            double w = hitboxSize / 2.0;
            
            // Устанавливаем расширенный хитбокс вокруг позиции сущности
            entity.setBoundingBox(new AABB(
                entity.getX() - w,
                entity.getY(), // Низ хитбокса остается на уровне ног сущности
                entity.getZ() - w,
                entity.getX() + w,
                entity.getY() + hitboxSize, // Высота подгоняется под установленный размер
                entity.getZ() + w
            ));
        }
    }

    private void resetHitboxes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player) {
                // Сбрасывает хитбоксы до стандартных игровых значений
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

                        if (arg.equals("1")) {
                            enabled = true;
                            player.displayClientMessage(Component.literal("§aУвеличение хитбоксов: ВКЛ (Текущий размер: " + hitboxSize + ")"), false);
                        } else if (arg.equals("0")) {
                            enabled = false;
                            resetHitboxes();
                            player.displayClientMessage(Component.literal("§cУвеличение хитбоксов: ВЫКЛ"), false);
                        } else {
                            try {
                                // Попытка спарсить число (например 1.5, 3.2 или обычное целое 3)
                                float size = Float.parseFloat(arg);
                                hitboxSize = size;
                                enabled = true; // Автоматическая активация при изменении размера
                                player.displayClientMessage(Component.literal("§eРазмер хитбокса установлен на: " + size + " и активирован."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1 (вкл), 0 (выкл) или дробное/целое число для размера."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
