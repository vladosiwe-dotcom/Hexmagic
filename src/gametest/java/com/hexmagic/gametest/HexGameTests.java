package com.hexmagic.gametest;

import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.entity.HexPrisonEntity;
import com.hexmagic.registry.HexItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side behaviour of the honeycomb constructs, run on a headless game test server. */
@GameTestHolder("hexmagic")
@PrefixGameTestTemplate(false)
public class HexGameTests {
    private static final String TEMPLATE = "empty7";

    private static Zombie caster(GameTestHelper helper, Vec3 pos, float yaw) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, pos);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HexItems.HEX_STAFF.get()));
        z.setYRot(yaw);
        z.setYHeadRot(yaw);
        z.setYBodyRot(yaw);
        z.setXRot(0f);
        z.yRotO = yaw;
        return z;
    }

    private static Zombie dummy(GameTestHelper helper, Vec3 pos) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, pos);
        z.setNoAi(true);
        z.setInvulnerable(false);
        return z;
    }

    private static HexConstructEntity wall(GameTestHelper helper, Zombie owner, Vec3 center, float yaw) {
        ServerLevel level = helper.getLevel();
        HexConstructEntity w = HexConstructEntity.create(level, HexConstructEntity.KIND_SHIELD, owner, helper.absoluteVec(center), yaw, 0f, 2.2f, 1.75f, 1, 1200);
        level.addFreshEntity(w);
        return w;
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 100)
    public static void shieldStopsArrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HexConstructEntity w = wall(helper, null, new Vec3(3.5, 2.0, 4.0), 0f);
        float hpBefore = w.getHp();
        Arrow arrow = new Arrow(level, helper.absoluteVec(new Vec3(3.5, 2.0, 1.0)).x, helper.absoluteVec(new Vec3(3.5, 2.0, 1.0)).y,
                helper.absoluteVec(new Vec3(3.5, 2.0, 1.0)).z, ItemStack.EMPTY, null);
        arrow.setDeltaMovement(0, 0, 1.6);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(arrow.isRemoved(), "arrow should have been absorbed by the wall");
            helper.assertTrue(w.getHp() < hpBefore, "wall should have lost durability");
            helper.assertTrue(w.getHp() > 0f, "a single arrow must not destroy the wall");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 120)
    public static void wallPushesMobsOut(GameTestHelper helper) {
        Zombie z = dummy(helper, new Vec3(3.5, 1.0, 4.0));
        // Standing right inside the plane of the wall.
        wall(helper, null, new Vec3(3.5, 2.0, 4.0), 0f);
        helper.runAfterDelay(5, () -> {
            double dz = Math.abs(z.getZ() - helper.absoluteVec(new Vec3(3.5, 1.0, 4.0)).z);
            helper.assertTrue(dz > 0.25, "mob should have been pushed out of the wall, dz=" + dz);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 200)
    public static void drillBoresThroughStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            }
        }
        Zombie owner = caster(helper, new Vec3(3.5, 1.0, 1.5), 0f);
        HexDrillEntity drill = HexDrillEntity.createCharging(level, owner);
        level.addFreshEntity(drill);
        helper.runAfterDelay(62, () -> {
            helper.assertTrue(drill.getCharge() > 0.95f, "drill should be fully charged, got " + drill.getCharge());
            helper.assertTrue(drill.launch(owner), "drill should launch");
            helper.assertTrue(drill.getTier() == 4, "full charge is tier 4 (12 damage), got " + drill.getTier());
        });
        helper.runAfterDelay(90, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 5));
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 200)
    public static void drillOpensPassageInOwnWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie owner = caster(helper, new Vec3(3.5, 1.0, 1.0), 0f);
        HexConstructEntity own = wall(helper, owner, new Vec3(3.5, 2.5, 4.0), 0f);
        float hp = own.getHp();
        Zombie target = dummy(helper, new Vec3(3.5, 1.0, 6.0));
        float targetHp = target.getHealth();
        HexDrillEntity drill = HexDrillEntity.createCharging(level, owner);
        level.addFreshEntity(drill);
        helper.runAfterDelay(25, () -> helper.assertTrue(own.hasAperture(), "wall should open while the drill charges"));
        helper.runAfterDelay(30, () -> helper.assertTrue(drill.launch(owner), "drill should launch at 1.5s charge"));
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(own.getHp() == hp, "own wall must not take drill damage");
            helper.assertTrue(target.getHealth() < targetHp, "target behind the own wall should be hit");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 200)
    public static void drillGrindsEnemyWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie owner = caster(helper, new Vec3(3.5, 1.0, 1.0), 0f);
        Zombie enemy = dummy(helper, new Vec3(1.0, 1.0, 1.0));
        HexConstructEntity enemyWall = wall(helper, enemy, new Vec3(3.5, 2.5, 4.5), 0f);
        HexDrillEntity drill = HexDrillEntity.createCharging(level, owner);
        level.addFreshEntity(drill);
        helper.runAfterDelay(62, () -> helper.assertTrue(drill.launch(owner), "drill should launch"));
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(enemyWall.isBroken() || enemyWall.getHp() < enemyWall.getMaxHp(), "enemy wall should be ground down");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 200)
    public static void spikeWallHitsTarget(GameTestHelper helper) {
        Zombie owner = caster(helper, new Vec3(3.5, 1.0, 0.5), 0f);
        HexConstructEntity w = wall(helper, owner, new Vec3(3.5, 2.5, 2.0), 0f);
        Zombie target = dummy(helper, new Vec3(3.5, 1.0, 5.0));
        float hp = target.getHealth();
        helper.runAfterDelay(5, w::beginSpike);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(target.getHealth() < hp, "target should be hit by the flying spiked wall");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 400)
    public static void prisonClosesAndCrushes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie owner = caster(helper, new Vec3(3.5, 1.0, 0.5), 0f);
        Zombie target = dummy(helper, new Vec3(3.5, 1.0, 3.5));
        float hp = target.getHealth();
        HexPrisonEntity prison = HexPrisonEntity.create(level, owner, target);
        level.addFreshEntity(prison);
        helper.runAfterDelay(HexPrisonEntity.PREP_TICKS + 5, () -> {
            int walls = level.getEntitiesOfClass(HexConstructEntity.class, target.getBoundingBox().inflate(4)).size();
            helper.assertTrue(walls == 4, "prison should have four walls, got " + walls);
            helper.assertTrue(prison.contains(target), "target should be inside the prison");
        });
        helper.runAfterDelay(HexPrisonEntity.PREP_TICKS + 215, () -> {
            helper.assertTrue(target.getHealth() <= hp - 9.5f, "crush should deal 10 pure damage, health=" + target.getHealth());
            helper.assertTrue(prison.isRemoved(), "prison should be over");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "hexmagic", template = TEMPLATE, timeoutTicks = 20)
    public static void launchVelocityMath(GameTestHelper helper) {
        double v = HexConstructEntity.launchVelocity(15.0, 0.08);
        double h = 0, vel = v;
        for (int t = 0; t < 200 && vel > 0; t++) {
            h += vel;
            vel = (vel - 0.08) * 0.98;
        }
        helper.assertTrue(Math.abs(h - 15.0) < 0.6, "launch should reach 15 blocks, got " + h);
        helper.succeed();
    }
}
