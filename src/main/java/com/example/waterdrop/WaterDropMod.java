package com.example.waterdrop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";

    private static boolean isWdEnabled = true;
    private static boolean isAimEnabled = true;

    public WaterDropMod() {
        // Убеждаемся, что код запускается ТОЛЬКО на клиенте
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

        // --- 1. АИМ-БОТ ДЛЯ ЛУКА (двигает камеру) ---
        if (isAimEnabled && player.isUsingItem() && player.getUseItem().is(Items.BOW)) {
            AABB searchBox = player.getBoundingBox().inflate(30.0D); // Радиус 30 блоков
            List<LivingEntity> targets = mc.level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != player && e.isAlive());

            LivingEntity bestTarget = null;
            double bestAngle = 0.90; // Захват цели, если она близко к центру экрана

            Vec3 lookVec = player.getLookAngle();
            Vec3 playerPos = player.getEyePosition();

            for (LivingEntity target : targets) {
                Vec3 dirToTarget = target.position().add(0, target.getBbHeight() / 2, 0).subtract(playerPos).normalize();
                double dotProduct = lookVec.dot(dirToTarget);
                if (dotProduct > bestAngle) {
                    bestAngle = dotProduct;
                    bestTarget = target;
                }
            }

            // Наводим камеру (прицел) прямо на цель
            if (bestTarget != null) {
                double dx = bestTarget.getX() - player.getX();
                double dy = (bestTarget.getY() + bestTarget.getEyeHeight() * 0.5) - player.getEyeY();
                double dz = bestTarget.getZ() - player.getZ();

                double distanceXZ = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
                float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

                player.setYRot(yaw);
                player.setXRot(pitch);
            }
        }

        // --- 2. МЕНЕЕ ПАЛЕВНЫЙ WATER DROP (Эмуляция клика) ---
        if (isWdEnabled && player.fallDistance > 3.0F && player.getDeltaMovement().y < -0.5) {
            // Пускаем "лазерный луч" вниз, чтобы узнать расстояние до земли
            Vec3 start = player.position();
            Vec3 end = start.add(0, -3.0, 0); // Смотрим на 3 блока вниз
            BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

            // Если земля ближе чем в 3 блоках
            if (hit.getType() == HitResult.Type.BLOCK) {
                int waterBucketSlot = -1;
                
                // Ищем ведро с водой в первых 9 слотах (хотбар)
                for (int i = 0; i < 9; i++) {
                    if (player.getInventory().getItem(i).is(Items.WATER_BUCKET)) {
                        waterBucketSlot = i;
                        break;
                    }
                }

                if (waterBucketSlot != -1) {
                    int previousSlot = player.getInventory().selected;
                    
                    // Резко переключаемся на ведро
                    player.getInventory().selected = waterBucketSlot;
                    
                    // Эмулируем клик правой кнопкой мыши
                    if (mc.gameMode != null) {
                        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    }
                    
                    // Сбрасываем fallDistance, чтобы скрипт не спамил кликами
                    player.fallDistance = 0;
                    
                    // Примечание: мод оставляет ведро в руках, чтобы ты мог сам собрать воду обратно.
                    // Если сразу переключить слот назад, сервер может отменить действие из-за античита.
                }
            }
        }
    }

    // --- 3. КЛИЕНТСКИЕ КОМАНДЫ ---
    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent event) {
        // Регистрируем команды только для твоего клиента (на сервер они не отправляются)
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("hack")
                .then(net.minecraft.commands.Commands.literal("wd").then(net.minecraft.commands.Commands.argument("state", IntegerArgumentType.integer(0, 1))
                    .executes(context -> {
                        isWdEnabled = (IntegerArgumentType.getInteger(context, "state") == 1);
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(Component.literal(isWdEnabled ? "§aWaterDrop ВКЛ" : "§cWaterDrop ВЫКЛ"), false);
                        }
                        return 1;
                    })
                ))
                .then(net.minecraft.commands.Commands.literal("aim").then(net.minecraft.commands.Commands.argument("state", IntegerArgumentType.integer(0, 1))
                    .executes(context -> {
                        isAimEnabled = (IntegerArgumentType.getInteger(context, "state") == 1);
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(Component.literal(isAimEnabled ? "§aAimAssist ВКЛ" : "§cAimAssist ВЫКЛ"), false);
                        }
                        return 1;
                    })
                ))
        );
    }
}
