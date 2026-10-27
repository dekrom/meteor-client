/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.Predicate;

public class NoFall extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The way you are saved from fall damage.")
        .defaultValue(Mode.Packet)
        .onChanged(value -> {
            clearPlacement();
            if (isActive()) updatePathManager(value);
        })
        .build()
    );

    private final Setting<Boolean> resetOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-disable")
        .description("Sends reset packets when disabling No Ground.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.NoGround)
        .build()
    );

    private final Setting<PlacedItem> placedItem = sgGeneral.add(new EnumSetting.Builder<PlacedItem>()
        .name("placed-item")
        .description("Which block to place.")
        .defaultValue(PlacedItem.Bucket)
        .visible(() -> mode.get() == Mode.Place)
        .build()
    );

    private final Setting<PlaceMode> airPlaceMode = sgGeneral.add(new EnumSetting.Builder<PlaceMode>()
        .name("air-place-mode")
        .description("Whether place mode places before you die or before you take damage.")
        .defaultValue(PlaceMode.BeforeDeath)
        .visible(() -> mode.get() == Mode.AirPlace)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates toward placement and bucket pickup targets.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Place || mode.get() == Mode.AirPlace)
        .build()
    );

    private final Setting<Boolean> anchor = sgGeneral.add(new BoolSetting.Builder()
        .name("anchor")
        .description("Centers the player and reduces movement when using bucket or air place mode.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Place || mode.get() == Mode.AirPlace)
        .build()
    );

    private final Setting<Boolean> antiBounce = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-bounce")
        .description("Disables bouncing on slime-block and bed upon landing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMace = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-mace")
        .description("Pauses NoFall when using a mace.")
        .defaultValue(true)
        .visible(() -> mode.get() != Mode.NoGround)
        .build()
    );

    private ClientWorld placementWorld;
    private BlockPos targetPos;
    private PlacedItem recoveryItem;
    private int timer, settledTicks;
    private boolean usingBucket, collecting;
    private Runnable pendingBucketUse;
    private int placementSequence = -1;
    private int pickupSequence = -1;
    private int acknowledgedSequence = -1;
    private boolean prePathManagerNoFall;

    public NoFall() {
        super(Categories.Movement, "no-fall", "Attempts to prevent you from taking fall damage.");
    }

    @Override
    public void onActivate() {
        prePathManagerNoFall = PathManagers.get().getSettings().getNoFall().get();
        updatePathManager(mode.get());
        clearPlacement();
        placementWorld = mc.world;
        acknowledgedSequence = -1;
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().getSettings().getNoFall().set(prePathManagerNoFall);
        clearPlacement();
        if (mode.get() == Mode.NoGround && resetOnDisable.get() && Utils.canUpdate()) {
            sendResetPacket(0.0000008);
            sendResetPacket(0);
        }
    }

    private void updatePathManager(Mode mode) {
        PathManagers.get().getSettings().getNoFall().set(mode == Mode.Packet || mode == Mode.NoGround || prePathManagerNoFall);
    }

    private void sendResetPacket(double height) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(), mc.player.getY() + height, mc.player.getZ(), false, false);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    private void clearPlacement() {
        targetPos = null;
        recoveryItem = null;
        timer = settledTicks = 0;
        placementSequence = -1;
        pickupSequence = -1;
        pendingBucketUse = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearPlacement();
        placementWorld = null;
        acknowledgedSequence = -1;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerActionResponseS2CPacket packet) {
            mc.execute(() -> {
                if (mc.getNetworkHandler() != null && event.connection == mc.getNetworkHandler().getConnection()) {
                    acknowledgedSequence = packet.sequence();
                }
            });
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (usingBucket) {
            int sequence = event.packet instanceof PlayerInteractItemC2SPacket packet ? packet.getSequence()
                : event.packet instanceof PlayerInteractBlockC2SPacket packet ? packet.getSequence() : -1;
            if (sequence >= 0) {
                if (collecting) pickupSequence = sequence;
                else placementSequence = sequence;
            }
        }

        if (mc.player == null || mc.player.getAbilities().creativeMode
            || !(event.packet instanceof PlayerMoveC2SPacket packet)
            || ((IPlayerMoveC2SPacket) packet).meteor$getTag() == 1337) return;

        if (mode.get() == Mode.NoGround) {
            ((PlayerMoveC2SPacketAccessor) packet).setOnGround(false);
            return;
        }

        if (mode.get() != Mode.Packet || pauseOnMace.get() && mc.player.getMainHandStack().getItem() instanceof MaceItem) return;

        if (!Modules.get().isActive(Flight.class)) {
            if (mc.player.isGliding()) return;
            if (mc.player.getVelocity().y > -0.5) return;
            ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(true);
        } else {
            ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(true);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (mc.player.getAbilities().creativeMode) return;
        if (pauseOnMace.get() && mc.player.getMainHandStack().getItem() instanceof MaceItem) return;

        // Airplace mode
        if (mode.get() == Mode.AirPlace) {
            // Test if fall damage setting is valid
            if (!airPlaceMode.get().test((float) mc.player.fallDistance)) return;

            // Center and place block
            if (anchor.get()) PlayerUtils.centerPlayer();

            Runnable place = () -> {
                double preY = mc.player.getVelocity().y;
                ((IVec3d) mc.player.getVelocity()).meteor$setY(0);

                BlockUtils.place(mc.player.getBlockPos().down(), InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem), false, 0, true);

                ((IVec3d) mc.player.getVelocity()).meteor$setY(preY);
            };
            if (rotate.get()) Rotations.rotate(mc.player.getYaw(), 90, Integer.MAX_VALUE, place);
            else place.run();
        }
    }

    @EventHandler
    private void onPlaceTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mode.get() != Mode.Place) return;
        if (placementWorld != mc.world) {
            clearPlacement();
            acknowledgedSequence = -1;
            placementWorld = mc.world;
        }
        if (mc.player.getAbilities().creativeMode || mc.player.isSpectator()) return;
        if (pauseOnMace.get() && mc.player.getMainHandStack().getItem() instanceof MaceItem) return;

        if (targetPos != null) {
            timer++;
            boolean settled = mc.player.fallDistance == 0 && (mc.player.isOnGround()
                || recoveryItem == PlacedItem.PowderSnow && mc.player.inPowderSnow && mc.player.getVelocity().y >= -0.1);
            // A completed landing remains safe while waiting for the server acknowledgment.
            if (settledTicks < 2) settledTicks = settled ? settledTicks + 1 : 0;

            // Keep the target through pickup prediction so rejected pickups can retry.
            if (pickupSequence >= 0 && acknowledgedSequence < pickupSequence) {
                if (timer > recoveryTimeout(PlayerUtils.getPing())) clearPlacement();
                return;
            }
            pickupSequence = -1;

            if (canCollect(placementSequence, acknowledgedSequence, settledTicks)) {
                boolean exists = recoveryItem == PlacedItem.Bucket
                    ? mc.world.getFluidState(targetPos).isEqualAndStill(Fluids.WATER)
                    : mc.world.getBlockState(targetPos).isOf(Blocks.POWDER_SNOW);
                if (!exists) clearPlacement();
                else {
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false);
                    queueBucketUse(InvUtils.findInHotbar(Items.BUCKET), hit, recoveryItem, true);
                }
            }

            // A rejected prediction must not prevent the next clutch.
            if (targetPos != null && pickupSequence < 0 && placementSequence >= 0 && acknowledgedSequence >= placementSequence
                && !(recoveryItem == PlacedItem.Bucket ? mc.world.getFluidState(targetPos).isEqualAndStill(Fluids.WATER)
                    : mc.world.getBlockState(targetPos).isOf(Blocks.POWDER_SNOW))) clearPlacement();
            if (timer > recoveryTimeout(PlayerUtils.getPing())) clearPlacement();
            if (targetPos != null) return;
        }

        // A rejected water prediction may have reset the client's fall distance.
        double speedY = mc.player.getVelocity().y;
        if (speedY >= 0 || (mc.player.fallDistance <= 3 && speedY > -0.6) || mc.player.isTouchingWater()) return;
        PlacedItem item = mc.world.getDimension().ultrawarm()
            && placedItem.get() == PlacedItem.Bucket ? PlacedItem.PowderSnow : placedItem.get();
        FindItemResult found = InvUtils.findInHotbar(item.item);
        if (!found.found()) return;

        if (anchor.get()) PlayerUtils.centerPlayer();

        BlockHitResult hit = findLandingSurface(mc.player.getYaw());
        if (hit == null) return;

        if (item == PlacedItem.Bucket || item == PlacedItem.PowderSnow) {
            queueBucketUse(found, hit, item, false);
        } else if (isWithinPlaceReach(hit.getPos())) {
            BlockUtils.place(hit.getBlockPos().up(), found, rotate.get(), 10, true);
        }
    }

    @EventHandler
    private void onSendMovementPackets(SendMovementPacketsEvent.Post event) {
        Runnable use = pendingBucketUse;
        pendingBucketUse = null;
        if (use != null) use.run();
    }

    private static boolean canCollect(int placementSequence, int acknowledgedSequence, int settledTicks) {
        return placementSequence >= 0 && acknowledgedSequence >= placementSequence && settledTicks >= 2;
    }

    private static int recoveryTimeout(int ping) {
        return 20 + (int) Math.ceil(Math.max(0, ping) / 50.0);
    }

    public boolean cancelBounce() {
        return isActive() && antiBounce.get();
    }

    private boolean isWithinPlaceReach(Vec3d pos) {
        // Reach does not extend the server's interaction range.
        double range = mc.player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        return mc.player.getEyePos().squaredDistanceTo(pos) <= range * range;
    }

    private BlockHitResult findLandingSurface(float movementYaw) {
        Vec3d pos = mc.player.getPos();
        Vec3d velocity = mc.player.getVelocity();
        Box box = mc.player.getBoundingBox();
        double gravity = mc.player.getFinalGravity();
        if (mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) gravity = Math.min(gravity, 0.01);
        double horizontalDrag = mc.player.hasNoDrag() ? 1 : 0.91;
        double verticalDrag = mc.player.hasNoDrag() ? 1 : 0.98;
        Vec3d input = new Vec3d(mc.player.sidewaysSpeed, 0, mc.player.forwardSpeed);
        if (input.lengthSquared() > 1) input = input.normalize();
        input = input.multiply(mc.player.isSprinting() ? 0.026 : 0.02);
        double yaw = Math.toRadians(movementYaw);
        Vec3d acceleration = new Vec3d(input.x * Math.cos(yaw) - input.z * Math.sin(yaw), 0,
            input.z * Math.cos(yaw) + input.x * Math.sin(yaw));

        // Simulate only the nearby landing window, without changing the player's movement.
        for (int tick = 0; tick < 8; tick++) {
            velocity = velocity.add(acceleration);
            Vec3d movement = Entity.adjustMovementForCollisions(mc.player, velocity, box, mc.world,
                mc.world.getEntityCollisions(mc.player, box.stretch(velocity)));
            Vec3d next = pos.add(movement);
            BlockHitResult path = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.WATER, mc.player));
            if (path.getType() == HitResult.Type.BLOCK && mc.world.getFluidState(path.getBlockPos()).isIn(FluidTags.WATER)) return null;

            if (movement.y > velocity.y + 1e-7) {
                // The bounding box can land on a ledge before its center reaches it.
                double radius = mc.player.getWidth() / 2.0 - 0.001;
                for (int corner = 0; corner < 5; corner++) {
                    double x = corner == 0 ? 0 : ((corner & 1) == 0 ? radius : -radius);
                    double z = corner == 0 ? 0 : (corner <= 2 ? radius : -radius);
                    Vec3d feet = next.add(x, 0, z);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(feet.add(0, 0.01, 0), feet.subtract(0, 0.01, 0),
                        RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK && hit.getSide() == Direction.UP) return hit;
                }
                return null;
            }

            box = box.offset(movement);
            pos = next;
            velocity = new Vec3d(movement.x == velocity.x ? velocity.x * horizontalDrag : 0,
                (velocity.y - gravity) * verticalDrag, movement.z == velocity.z ? velocity.z * horizontalDrag : 0);
        }
        return null;
    }

    private void queueBucketUse(FindItemResult item, BlockHitResult hit, PlacedItem placedItem, boolean pickup) {
        if (!item.found()) return;
        ClientWorld world = mc.world;
        float movementYaw = mc.player.getYaw();
        Runnable use = () -> {
            if (!isActive() || !Utils.canUpdate() || mc.world != world || mode.get() != Mode.Place) return;
            if (pauseOnMace.get() && mc.player.getMainHandStack().getItem() instanceof MaceItem) return;
            if (pickup && (targetPos == null || !targetPos.equals(hit.getBlockPos()) || pickupSequence >= 0)) return;

            BlockHitResult landing = pickup ? hit : findLandingSurface(movementYaw);
            if (landing == null) return;

            Vec3d aim = pickup ? Vec3d.ofCenter(hit.getBlockPos()) : landing.getPos();
            BlockHitResult currentHit = landing;
            if (!pickup) {
                // Recheck placement after movement; pickup uses the normal bucket interaction.
                Vec3d eyes = mc.player.getEyePos();
                Vec3d end = aim.add(aim.subtract(eyes).normalize().multiply(0.01));
                currentHit = mc.world.raycast(new RaycastContext(eyes, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                if (currentHit.getType() != HitResult.Type.BLOCK || !currentHit.getBlockPos().equals(landing.getBlockPos())
                    || !isWithinPlaceReach(currentHit.getPos()) || currentHit.getSide() != Direction.UP || mc.player.isOnGround()) return;
            }

            if (!useBucket(item, currentHit, placedItem, pickup, pickup ? aim : currentHit.getPos())) return;
            if (!pickup) {
                targetPos = placedItem == PlacedItem.Bucket && mc.world.getFluidState(currentHit.getBlockPos()).isEqualAndStill(Fluids.WATER)
                    ? currentHit.getBlockPos() : currentHit.getBlockPos().up();
                recoveryItem = placedItem;
                timer = settledTicks = 0;
            }
        };

        // Use reachable water before falling again, rather than waiting until after movement.
        boolean useNow = pickup || isWithinPlaceReach(hit.getPos());
        Vec3d aim = pickup ? Vec3d.ofCenter(hit.getBlockPos()) : hit.getPos();
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(aim), Rotations.getPitch(aim),
            Integer.MAX_VALUE, true, useNow ? null : use);
        else if (!useNow) pendingBucketUse = use;
        if (useNow) use.run();
    }

    private boolean useBucket(FindItemResult item, BlockHitResult hit, PlacedItem placedItem, boolean pickup, Vec3d aim) {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        int slot = mc.player.getInventory().getSelectedSlot();
        Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        try {
            // Bucket use packets carry their own aim without a movement rotation.
            mc.player.setYaw((float) Rotations.getYaw(aim));
            mc.player.setPitch((float) Rotations.getPitch(aim));
            if (hand == Hand.MAIN_HAND) mc.player.getInventory().setSelectedSlot(item.slot());
            if (!mc.player.getStackInHand(hand).isOf(pickup ? Items.BUCKET : placedItem.item)) return false;
            usingBucket = true;
            collecting = pickup;
            if (pickup) pickupSequence = -1;
            else placementSequence = -1;
            ActionResult result = !pickup && placedItem == PlacedItem.PowderSnow
                ? mc.interactionManager.interactBlock(mc.player, hand, hit) : mc.interactionManager.interactItem(mc.player, hand);
            return result.isAccepted();
        } finally {
            usingBucket = false;
            collecting = false;
            if (hand == Hand.MAIN_HAND) {
                mc.player.getInventory().setSelectedSlot(slot);
                ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
            }
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }

    public enum Mode {
        Packet,
        NoGround,
        AirPlace,
        Place
    }

    public enum PlacedItem {
        Bucket(Items.WATER_BUCKET),
        PowderSnow(Items.POWDER_SNOW_BUCKET),
        HayBale(Items.HAY_BLOCK),
        Cobweb(Items.COBWEB),
        SlimeBlock(Items.SLIME_BLOCK);

        private final Item item;

        PlacedItem(Item item) {
            this.item = item;
        }
    }

    public enum PlaceMode {
        BeforeDamage(height -> height > 2),
        BeforeDeath(height -> height > Math.max(PlayerUtils.getTotalHealth(), 2));

        private final Predicate<Float> fallHeight;

        PlaceMode(Predicate<Float> fallHeight) {
            this.fallHeight = fallHeight;
        }

        public boolean test(float fallheight) {
            return fallHeight.test(fallheight);
        }
    }
}
