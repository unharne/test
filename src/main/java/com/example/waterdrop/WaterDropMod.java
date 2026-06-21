package com.example.waterdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.mojang.brigadier.arguments.IntegerArgumentType;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    private static boolean isWdEnabled = true;
    private static final double MAX_WATER_DROP_DISTANCE = 4.25D;
    private static final double MIN_FALL_SPEED = -0.35D;
    private static final float MIN_FALL_DISTANCE = 2.4F;
    private static final int WATER_DROP_COOLDOWN_TICKS = 1;
    private int waterDropCooldown = 0;

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

        if (waterDropCooldown > 0) waterDropCooldown--;

        if (isWdEnabled && mc.gameMode != null && waterDropCooldown == 0 && shouldTryWaterDrop(player)) {
            BlockHitResult placementHit = findBestWaterDropHit(mc, player);
            if (placementHit != null && selectWaterBucket(player)) {
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, placementHit);
                waterDropCooldown = WATER_DROP_COOLDOWN_TICKS;
            }
        }
    }

    private boolean shouldTryWaterDrop(LocalPlayer player) {
        return !player.isCreative() && !player.isSpectator() && !player.onGround() &&
               !player.isInWater() && !player.isInLava() &&
               player.fallDistance >= MIN_FALL_DISTANCE &&
               player.getDeltaMovement().y <= MIN_FALL_SPEED;
    }

    private BlockHitResult findBestWaterDropHit(Minecraft mc, LocalPlayer player) {
        Vec3 feet = player.position();
        Vec3 velocity = player.getDeltaMovement();
        for (int tick = 0; tick <= 6; tick++) {
            Vec3 predictedFeet = feet.add(velocity.x * tick, velocity.y * tick - 0.04D * tick * tick, velocity.z * tick);
            BlockHitResult hit = findSolidBlockBelow(mc, player, predictedFeet);
            if (hit != null) return hit;
        }
        return findSolidBlockBelow(mc, player, feet);
    }

    private BlockHitResult findSolidBlockBelow(Minecraft mc, LocalPlayer player, Vec3 feet) {
        Vec3 end = feet.add(0.0D, -MAX_WATER_DROP_DISTANCE, 0.0D);
        BlockHitResult rayHit = mc.level.clip(new ClipContext(feet, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (rayHit.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, rayHit.getBlockPos(), rayHit.getLocation())) {
            return new BlockHitResult(rayHit.getLocation(), Direction.UP, rayHit.getBlockPos(), false);
        }

        double[][] offsets = {{0.28,0},{ -0.28,0},{0,0.28},{0,-0.28},{0.28,0.28},{0.28,-0.28},{-0.28,0.28},{-0.28,-0.28}};
        for (double[] offset : offsets) {
            Vec3 shifted = feet.add(offset[0], 0, offset[1]);
            BlockHitResult hit = mc.level.clip(new ClipContext(shifted, shifted.add(0, -MAX_WATER_DROP_DISTANCE, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, hit.getBlockPos(), hit.getLocation())) {
                return new BlockHitResult(hit.getLocation(), Direction.UP, hit.getBlockPos(), false);
            }
        }
        return null;
    }

    private boolean canPlaceWaterOn(Minecraft mc, BlockPos blockPos, Vec3 hitLocation) {
        BlockPos waterPos = blockPos.above();
        VoxelShape supportShape = mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos);
        return !supportShape.isEmpty() && mc.level.getFluidState(waterPos).isEmpty() &&
               mc.level.getBlockState(waterPos).canBeReplaced() &&
               !mc.level.getBlockState(waterPos).is(Blocks.POWDER_SNOW) &&
               hitLocation.y <= waterPos.getY() + 0.02D;
    }

    private boolean selectWaterBucket(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.WATER_BUCKET)) return true;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.WATER_BUCKET)) {
                player.getInventory().selected = i;
                player.connection.send(new ServerboundSetCarriedItemPacket(i));
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("hack")
                .then(net.minecraft.commands.Commands.literal("wd")
                    .then(net.minecraft.commands.Commands.argument("state", IntegerArgumentType.integer(0, 1))
                        .executes(context -> {
                            isWdEnabled = IntegerArgumentType.getInteger(context, "state") == 1;
                            LocalPlayer p = Minecraft.getInstance().player;
                            if (p != null) p.displayClientMessage(Component.literal(isWdEnabled ? "§aWaterDrop ВКЛ" : "§cWaterDrop ВЫКЛ"), false);
                            return 1;
                        })
                    )
                )
        );
    }
}
