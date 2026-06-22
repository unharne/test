package com.yourname.hitboxmod; // Замените на свой package, если нужно

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("hitboxmod")
@Mod.EventBusSubscriber(modid = "hitboxmod", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HitboxMod {

    private static boolean enabled = false;
    private static float hitboxSize = 2.0f; // Начальный размер хитбокса по умолчанию

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // Регистрация клиентской команды /helpme <аргумент>
        event.getDispatcher().register(Commands.literal("helpme")
            .then(Commands.argument("arg", StringArgumentType.string())
                .executes(context -> {
                    String arg = StringArgumentType.getString(context, "arg");
                    Minecraft mc = Minecraft.getInstance();

                    if (arg.equals("1")) {
                        enabled = true;
                        sendMsg(mc, "§aУвеличение хитбоксов: ВКЛ (Текущий размер: " + hitboxSize + ")");
                    } else if (arg.equals("0")) {
                        enabled = false;
                        resetHitboxes(mc);
                        sendMsg(mc, "§cУвеличение хитбоксов: ВЫКЛ");
                    } else {
                        try {
                            // Если введено число (например, 1.5, 3.0), меняем размер хитбокса
                            float size = Float.parseFloat(arg);
                            hitboxSize = size;
                            enabled = true; // Автоматически включаем при смене размера
                            sendMsg(mc, "§eРазмер хитбокса установлен на: " + size + " и активирован.");
                        } catch (NumberFormatException e) {
                            sendMsg(mc, "§4Ошибка: используйте 1 (вкл), 0 (выкл) или числовое значение (например, 2.5) для размера.");
                        }
                    }
                    return 1;
                })
            )
        );
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Выполняем только один раз за тик (в конце) и только если мод включен
        if (event.phase != TickEvent.Phase.END || !enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Перебираем всех отрендеренных сущностей в мире клиента
        for (Entity entity : mc.level.entitiesForRendering()) {
            // Игнорируем самого себя
            if (entity == mc.player) continue;

            // Центрируем расширенный хитбокс относительно позиции сущности
            double w = hitboxSize / 2.0;
            
            // Переопределяем bounding box, по которому регистрируются клики
            entity.setBoundingBox(new AABB(
                entity.getX() - w,
                entity.getY(), // Низ хитбокса остается на уровне ног
                entity.getZ() - w,
                entity.getX() + w,
                entity.getY() + hitboxSize, // Высота подгоняется под размер
                entity.getZ() + w
            ));
        }
    }

    // Возвращаем хитбоксы к ванильным значениям при выключении чита
    private static void resetHitboxes(Minecraft mc) {
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player) {
                // Обновляет AABB до стандартного в соответствии с EntityDimensions
                entity.refreshDimensions();
            }
        }
    }

    // Отправка сообщений только в чат клиента (не уходит на сервер)
    private static void sendMsg(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
