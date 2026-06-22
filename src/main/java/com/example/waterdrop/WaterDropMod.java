package com.example.waterdrop;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(WaterDropMod.MODID)
public class WaterDropMod {
    public static final String MODID = "waterdrop";
    
    private boolean enabled = false;
    private float hitboxSize = 2.0f; // Размер хитбокса по умолчанию
    private boolean onlyStandardBox = false; // Переключатель режима отрисовки ESP

    public WaterDropMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    // ТИК-МЕТОД: Изменяет физические размеры хитбоксов
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !enabled) return;

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

    // МЕТОД ОТРИСОВКИ: Рисует ESP сквозь стены
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        RenderSystem.disableDepthTest(); 
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            AABB box;

            if (onlyStandardBox) {
                // Стандартный хитбокс
                EntityDimensions dimensions = entity.getDimensions(entity.getPose());
                double w = dimensions.width / 2.0;
                double h = dimensions.height;
                
                box = new AABB(
                    entity.getX() - w,
                    entity.getY(),
                    entity.getZ() - w,
                    entity.getX() + w,
                    entity.getY() + h,
                    entity.getZ() + w
                );
            } else {
                // Измененный хитбокс
                box = entity.getBoundingBox();
            }

            double minX = box.minX - camPos.x;
            double minY = box.minY - camPos.y;
            double minZ = box.minZ - camPos.z;
            double maxX = box.maxX - camPos.x;
            double maxY = box.maxY - camPos.y;
            double maxZ = box.maxZ - camPos.z;

            // Красная рамка
            drawBox(bufferbuilder, minX, minY, minZ, maxX, maxY, maxZ, 255, 0, 0, 255);
        }

        tesselator.end();
        RenderSystem.enableDepthTest(); 
    }

    private void drawBox(BufferBuilder buffer, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int r, int g, int b, int a) {
        // Нижняя плоскость
        line(buffer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Верхняя плоскость
        line(buffer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Вертикальные ребра
        line(buffer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2, int r, int g, int b, int a) {
        buffer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x2, y2, z2).color(r, g, b, a).endVertex();
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
                
                // 1. Подкоманда: /helpme box [on/off]
                .then(net.minecraft.commands.Commands.literal("box")
                    .then(net.minecraft.commands.Commands.argument("state", StringArgumentType.string())
                        .executes(ctx -> {
                            String state = StringArgumentType.getString(ctx, "state");
                            Minecraft mc = Minecraft.getInstance();
                            LocalPlayer player = mc.player;
                            if (player == null) return 1;

                            if (state.equalsIgnoreCase("on")) {
                                onlyStandardBox = true;
                                player.displayClientMessage(Component.literal("§aОтрисовка ESP: только СТАНДАРТНЫЙ хитбокс."), false);
                            } else if (state.equalsIgnoreCase("off")) {
                                onlyStandardBox = false;
                                player.displayClientMessage(Component.literal("§eОтрисовка ESP: подстраивается под измененный хитбокс."), false);
                            } else {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте '/helpme box on' или '/helpme box off'"), false);
                            }
                            return 1;
                        })
                    )
                )

                // 2. Подкоманда: /helpme d (Переключатель диаграммы F3)
                .then(net.minecraft.commands.Commands.literal("d")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        if (player == null) return 1;

                        // Инвертируем настройку отображения круговой диаграммы
                        mc.options.renderDebugCharts = !mc.options.renderDebugCharts;
                        
                        // Если мы включили диаграмму, но само меню F3 скрыто — принудительно открываем его
                        if (mc.options.renderDebugCharts && !mc.options.renderDebug) {
                            mc.options.renderDebug = true;
                        }

                        player.displayClientMessage(Component.literal(mc.options.renderDebugCharts ? "§aДиаграмма профайлера (F3): ВКЛ" : "§cДиаграмма профайлера (F3): ВЫКЛ"), false);
                        return 1;
                    })
                )

                // 3. Основной аргумент: /helpme [1/0/размер]
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
                                float size = Float.parseFloat(arg);
                                hitboxSize = size;
                                enabled = true; 
                                player.displayClientMessage(Component.literal("§eРазмер хитбокса установлен на: " + size + " и активирован."), false);
                            } catch (NumberFormatException e) {
                                player.displayClientMessage(Component.literal("§4Ошибка: используйте 1, 0, число, команду 'box on/off' или 'd'."), false);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
