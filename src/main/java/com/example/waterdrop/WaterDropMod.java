package com.example.waterdrop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    private static final double MAX_WATER_DROP_DISTANCE = 7.0D;
    private static final double MIN_FALL_SPEED = -0.35D;
    private static final float MIN_FALL_DISTANCE = 2.4F;
    private static final int WATER_DROP_COOLDOWN_TICKS = 4;

    private int waterDropCooldown = 0;

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

        // --- 2. WATER DROP: точная установка воды под ноги во время падения ---
        if (waterDropCooldown > 0) {
            waterDropCooldown--;
        }

        if (isWdEnabled && mc.gameMode != null && waterDropCooldown == 0 && shouldTryWaterDrop(player)) {
            BlockHitResult placementHit = findBestWaterDropHit(mc, player);
            if (placementHit != null && selectWaterBucket(player)) {
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, placementHit);
                player.swing(InteractionHand.MAIN_HAND);

                // Небольшая пауза защищает от двойного клика и повторной отправки пакета на один и тот же блок.
                waterDropCooldown = WATER_DROP_COOLDOWN_TICKS;
            }
        }
    }


    private boolean shouldTryWaterDrop(LocalPlayer player) {
        return !player.isCreative()
            && !player.isSpectator()
            && !player.onGround()
            && !player.isInWater()
            && !player.isInLava()
            && player.fallDistance >= MIN_FALL_DISTANCE
            && player.getDeltaMovement().y <= MIN_FALL_SPEED;
    }

    private BlockHitResult findBestWaterDropHit(Minecraft mc, LocalPlayer player) {
        Vec3 feet = player.position();
        Vec3 velocity = player.getDeltaMovement();

        // Сначала проверяем точку прямо под ногами, затем небольшое предсказание движения на ближайшие тики.
        for (int tick = 0; tick <= 6; tick++) {
            Vec3 predictedFeet = feet.add(velocity.x * tick, velocity.y * tick - 0.04D * tick * tick, velocity.z * tick);
            BlockHitResult hit = findSolidBlockBelow(mc, player, predictedFeet);
            if (hit != null) {
                return hit;
            }
        }

        return findSolidBlockBelow(mc, player, feet);
    }

    private BlockHitResult findSolidBlockBelow(Minecraft mc, LocalPlayer player, Vec3 feet) {
        Vec3 end = feet.add(0.0D, -MAX_WATER_DROP_DISTANCE, 0.0D);
        BlockHitResult rayHit = mc.level.clip(new ClipContext(feet, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (rayHit.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, rayHit.getBlockPos(), rayHit.getLocation())) {
            return new BlockHitResult(rayHit.getLocation(), Direction.UP, rayHit.getBlockPos(), false);
        }

        // Fallback по соседним точкам помогает, когда игрок падает рядом с краем блока.
        double[][] offsets = {
            {0.28D, 0.0D}, {-0.28D, 0.0D}, {0.0D, 0.28D}, {0.0D, -0.28D},
            {0.28D, 0.28D}, {0.28D, -0.28D}, {-0.28D, 0.28D}, {-0.28D, -0.28D}
        };
        for (double[] offset : offsets) {
            Vec3 shiftedFeet = feet.add(offset[0], 0.0D, offset[1]);
            BlockHitResult hit = mc.level.clip(new ClipContext(shiftedFeet, shiftedFeet.add(0.0D, -MAX_WATER_DROP_DISTANCE, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, hit.getBlockPos(), hit.getLocation())) {
                return new BlockHitResult(hit.getLocation(), Direction.UP, hit.getBlockPos(), false);
            }
        }

        return null;
    }

    private boolean canPlaceWaterOn(Minecraft mc, BlockPos blockPos, Vec3 hitLocation) {
        BlockPos waterPos = blockPos.above();
        VoxelShape supportShape = mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos);
        return !supportShape.isEmpty()
            && mc.level.getFluidState(waterPos).isEmpty()
            && mc.level.getBlockState(waterPos).canBeReplaced()
            && !mc.level.getBlockState(waterPos).is(Blocks.POWDER_SNOW)
            && hitLocation.y <= waterPos.getY() + 0.02D;
    }

    private boolean selectWaterBucket(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.WATER_BUCKET)) {
            return true;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.WATER_BUCKET)) {
                player.getInventory().selected = i;
                player.connection.send(new ServerboundSetCarriedItemPacket(i));
                return true;
            }
        }

        return false;
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
