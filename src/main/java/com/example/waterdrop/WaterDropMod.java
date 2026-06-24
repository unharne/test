package com.example.waterdrop;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    
    private boolean enabled = false;
    private float hitboxSize = 2.0f;

    // Переменные для отображения здоровья
    private boolean healthHudEnabled = false;
    private LivingEntity lastAttackedEntity = null;
    private int healthDisplayTimer = 0; // Таймер, чтобы убирать текст с экрана

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

        // Таймер отображения здоровья
        if (healthDisplayTimer > 0) {
            healthDisplayTimer--;
            if (healthDisplayTimer <= 0) {
                lastAttackedEntity = null;
            }
        }

        // Логика хитбоксов
        if (!enabled) return;

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

    private void resetHitboxes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player) {
                entity.refreshDimensions();
            }
        }
    }

    // Захватываем цель при ударе
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() == mc.player && event.getTarget() instanceof LivingEntity target) {
            lastAttackedEntity = target;
            healthDisplayTimer = 100; // Показываем текст ~5 секунд (100 тиков)
        }
    }

    // Отрисовка здоровья в левом верхнем углу
    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (!healthHudEnabled || lastAttackedEntity == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!lastAttackedEntity.isAlive()) {
            lastAttackedEntity = null;
            return;
        }

        float health = lastAttackedEntity.getHealth();
        float maxHealth = lastAttackedEntity.getMaxHealth();
        String name = lastAttackedEntity.getDisplayName().getString();

        String text = String.format(Locale.US, "❤ %s: %.1f / %.1f", name, health, maxHealth);
        
        event.getGuiGraphics().drawString(mc.font, text, 10, 10, 0xFF5555, true);
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
                        } else if (arg.equals("h1")) {
                            healthHudEnabled = true;
                            player.displayClientMessage(Component.literal("§aОтображение здоровья врага: ВКЛ"), false);
                        } else if (arg.equals("h0")) {
                            healthHudEnabled = false;
                            lastAttackedEntity = null;
                            player.displayClientMessage(Component.literal("§cОтображение здоровья врага: ВЫКЛ"), false);
                        } else {
                            try {
                                float size = Float.parseFloat(arg);
                                hitboxSize = size;
                                enabled = true; 
                                player.displayClientMessage(Component.literal("§eРазмер хитбокса установлен на: " + size + " и активирован."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка. Используйте: 1, 0, [размер], h1 (вкл хп), h0 (выкл хп)."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
