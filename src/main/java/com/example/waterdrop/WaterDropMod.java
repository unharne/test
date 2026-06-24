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
    private float hitboxSize = 2.0f; // Размер хитбокса по умолчанию

    // Переменные для чистого PvP HUD'а здоровья
    private boolean healthHudEnabled = false;
    private LivingEntity lastAttackedEntity = null;
    private int healthDisplayTimer = 0; 

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

        // Таймер отображения здоровья (скрывает HUD через 5 секунд после удара)
        if (healthDisplayTimer > 0) {
            healthDisplayTimer--;
            if (healthDisplayTimer <= 0) {
                lastAttackedEntity = null;
            }
        }

        if (!enabled) return;

        // Логика расширения хитбоксов
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

    // Захват цели при ударе
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() == mc.player && event.getTarget() instanceof LivingEntity target) {
            lastAttackedEntity = target;
            healthDisplayTimer = 100; // Показываем 100 тиков (5 секунд)
        }
    }

    // Отрисовка ясного PvP HUD по центру экрана
    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        // Рендерим только один раз (после отрисовки хотбара), чтобы избежать наложения и багов
        if (!event.getOverlay().id().getPath().equals("hotbar")) return;
        if (!healthHudEnabled || lastAttackedEntity == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Если цель умерла или пропала из мира — убираем HUD
        if (!lastAttackedEntity.isAlive() || mc.level.getEntity(lastAttackedEntity.getId()) == null) {
            lastAttackedEntity = null;
            return;
        }

        // Расчет координат центра экрана
        int centerX = event.getWindow().getGuiScaledWidth() / 2;
        int centerY = event.getWindow().getGuiScaledHeight() / 2;

        // Позиция HUD чуть выше прицела (идеально для PvP, не нужно отводить взгляд)
        int x = centerX - 60; // Ширина панели 120 пикселей, центрируем её
        int y = centerY - 38;

        float health = lastAttackedEntity.getHealth();
        float maxHealth = lastAttackedEntity.getMaxHealth();
        float healthRatio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));

        String name = lastAttackedEntity.getDisplayName().getString();
        String hpText = String.format(Locale.US, "%.1f / %.1f", health, maxHealth);

        // 1. Рисуем темную аккуратную подложку для читаемости
        event.getGuiGraphics().fill(x, y, x + 120, y + 24, 0x90000000);

        // 2. Выводим имя врага (слева)
        event.getGuiGraphics().drawString(mc.font, name, x + 5, y + 4, 0xFFFFFF, true);

        // 3. Выводим точное ХП (справа) контрастным цветом
        int hpTextWidth = mc.font.width(hpText);
        event.getGuiGraphics().drawString(mc.font, hpText, x + 125 - hpTextWidth - 10, y + 4, 0xFFFF5555, true);

        // 4. Рисуем полосу здоровья (Задний фон полосы - темно-красный)
        event.getGuiGraphics().fill(x + 5, y + 15, x + 115, y + 19, 0x55550000);

        // 5. Заполняем полосу ярким алым цветом в зависимости от текущего здоровья
        int barFillWidth = (int) (110 * healthRatio);
        event.getGuiGraphics().fill(x + 5, y + 15, x + 5 + barFillWidth, y + 19, 0xFFFF2222);
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
                                player.displayClientMessage(Component.literal("§4Ошибка. Используйте: 1, 0, [число], h1 (вкл хп), h0 (выкл хп)."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
