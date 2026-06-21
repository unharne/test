package com.example.waterdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
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

    private static final double MAX_WATER_DROP_DISTANCE = 4.5D;
    private static final double MIN_FALL_SPEED = -0.35D;
    private static final float MIN_FALL_DISTANCE = 2.4F;
    private static final int WATER_DROP_COOLDOWN_TICKS = 2;

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
        if (player == null || mc.level == null || mc.gameMode == null) return;

        if (waterDropCooldown > 0) {
            waterDropCooldown--;
            return;
        }

        if (!isWdEnabled || !shouldTryWaterDrop(player)) return;

        BlockHitResult hit = findBestWaterDropHit(mc, player);
        if (hit == null) return;

        if (selectWaterBucket(player)) {
            // Более надежное размещение
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);           // Добавлено
            waterDropCooldown = WATER_DROP_COOLDOWN_TICKS;
        }
    }

    private boolean shouldTryWaterDrop(LocalPlayer player) {
        return !player.isCreative() && !player.isSpectator() &&
               !player.onGround() && !player.isInWater() && !player.isInLava() &&
               player.fallDistance >= MIN_FALL_DISTANCE &&
               player.getDeltaMovement().y <= MIN_FALL_DISTANCE;
    }

    private BlockHitResult findBestWaterDropHit(Minecraft mc, LocalPlayer player) {
        Vec3 feet = player.position();
        Vec3 vel = player.getDeltaMovement();

        for (int t = 0; t <= 7; t++) {
            Vec3 pos = feet.add(vel.x * t, vel.y * t - 0.04D * t * t, vel.z * t);
            BlockHitResult hit = findSolidBlockBelow(mc, player, pos);
            if (hit != null) return hit;
        }
        return findSolidBlockBelow(mc, player, feet);
    }

    private BlockHitResult findSolidBlockBelow(Minecraft mc, LocalPlayer player, Vec3 feet) {
        Vec3 end = feet.add(0, -MAX_WATER_DROP_DISTANCE, 0);
        BlockHitResult ray = mc.level.clip(new ClipContext(feet, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (ray.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, ray.getBlockPos(), ray.getLocation())) {
            return new BlockHitResult(ray.getLocation(), Direction.UP, ray.getBlockPos(), false);
        }

        // offsets
        double[][] offsets = {{0.3,0}, {-0.3,0}, {0,0.3}, {0,-0.3}, {0.25,0.25}, {0.25,-0.25}, {-0.25,0.25}, {-0.25,-0.25}};
        for (double[] off : offsets) {
            Vec3 p = feet.add(off[0], 0, off[1]);
            ray = mc.level.clip(new ClipContext(p, p.add(0, -MAX_WATER_DROP_DISTANCE, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (ray.getType() == HitResult.Type.BLOCK && canPlaceWaterOn(mc, ray.getBlockPos(), ray.getLocation())) {
                return new BlockHitResult(ray.getLocation(), Direction.UP, ray.getBlockPos(), false);
            }
        }
        return null;
    }

    private boolean canPlaceWaterOn(Minecraft mc, BlockPos pos, Vec3 hitLoc) {
        BlockPos waterPos = pos.above();
        VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
        return !shape.isEmpty() &&
               mc.level.getFluidState(waterPos).isEmpty() &&
               mc.level.getBlockState(waterPos).canBeReplaced() &&
               !mc.level.getBlockState(waterPos).is(Blocks.POWDER_SNOW) &&
               hitLoc.y <= waterPos.getY() + 0.1D;
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
                        .executes(ctx -> {
                            isWdEnabled = IntegerArgumentType.getInteger(ctx, "state") == 1;
                            LocalPlayer p = Minecraft.getInstance().player;
                            if (p != null) p.displayClientMessage(Component.literal(isWdEnabled ? "§aWaterDrop ВКЛ" : "§cWaterDrop ВЫКЛ"), false);
                            return 1;
                        })
                    )
                )
        );
    }
}
