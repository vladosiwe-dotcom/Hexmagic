package com.hexmagic.gametest.film;

import com.hexmagic.ability.HexAbility;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import io.netty.buffer.ByteBuf;

/**
 * Shared script of the showcase film: one timeline (in game ticks from the start signal) that the
 * server director, the caster's client, the rival's client and the camera client all follow.
 */
@EventBusSubscriber(modid = "hexmagic_gametest")
public final class HexFilm {
    private HexFilm() {}

    public static final String MAGE = "HexMage";
    public static final String RIVAL = "Rival";
    public static final String CAM = "Cam";
    /** The game runs this slow while filming; the video is sped back up afterwards. */
    public static final float TICK_RATE = 4f;
    public static final int END = 1120;

    // ------------------------------------------------------------------ start signal

    public record Start(long t0) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexmagic_gametest", "film_start"));
        public static final StreamCodec<ByteBuf, Start> CODEC = ByteBufCodecs.VAR_LONG.map(Start::new, Start::t0);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client side: game time of tick 0, or MIN_VALUE before the director gave the signal. */
    public static volatile long clientT0 = Long.MIN_VALUE;

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Start.TYPE, Start.CODEC, (payload, ctx) -> clientT0 = payload.t0());
    }

    // ------------------------------------------------------------------ caster input (real key presses)

    /** Tick -> ability key the caster taps. */
    public static final int[][] MAGE_CLICKS = {
            {60, HexAbility.SHIELD.ordinal()},
            {405, HexAbility.SPIKE.ordinal()},
            {470, HexAbility.LAUNCH.ordinal()},
            {505, HexAbility.SHIELD.ordinal()},
            {640, HexAbility.HEX_ATTACK.ordinal()},
            {672, HexAbility.HEX_ATTACK.ordinal()},
            {720, HexAbility.PRISON.ordinal()},
            {978, HexAbility.SHIELD.ordinal()},
    };
    /** The drill is charged by holding the right mouse button (with Hexa Drill selected). */
    public static final int[][] MAGE_USE = {{270, 332}, {850, 882}};
    public static final int MAGE_SELECT_DRILL = 262;

    // ------------------------------------------------------------------ rival input

    public static final int[][] RIVAL_USE = {{70, 92}, {100, 122}, {190, 212}};
    public static final int[][] RIVAL_FORWARD = {{138, 172}, {760, 786}, {796, 830}};
    public static final int[] RIVAL_ATTACK = {1005, 1029};

    public static boolean inside(int[][] intervals, int t) {
        for (int[] iv : intervals) if (t >= iv[0] && t < iv[1]) return true;
        return false;
    }

    // ------------------------------------------------------------------ camera

    /** A camera move: from -> to, looking at a fixed point, at a player, or through a player's eyes. */
    public record Shot(int t0, int t1, Vec3 from, Vec3 to, Vec3 look, String lookAt, String eyesOf) {}

    public record Caption(int t0, int t1, String text, boolean title) {}

    public static final List<Shot> SHOTS = new ArrayList<>();
    public static final List<Caption> CAPTIONS = new ArrayList<>();

    private static void shot(int t0, int t1, double x0, double y0, double z0, double x1, double y1, double z1, double lx, double ly, double lz) {
        SHOTS.add(new Shot(t0, t1, new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), new Vec3(lx, ly, lz), null, null));
    }

    private static void follow(int t0, int t1, double x0, double y0, double z0, double x1, double y1, double z1, String who, double dy) {
        SHOTS.add(new Shot(t0, t1, new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), new Vec3(0, dy, 0), who, null));
    }

    private static void eyes(int t0, int t1, String who) {
        SHOTS.add(new Shot(t0, t1, null, null, null, null, who));
    }

    private static void cap(int t0, int t1, String text) {
        CAPTIONS.add(new Caption(t0, t1, text, false));
    }

    private static void title(int t0, int t1, String text) {
        CAPTIONS.add(new Caption(t0, t1, text, true));
    }

    static {
        // Caster stands at (0.5, -60, 0.5) facing +z, the rival around z = 9..12.
        shot(-40, 58, -1.7, -58.2, 3.6, -1.1, -58.4, 2.7, 0.15, -58.55, 0.8);
        title(-40, 58, "Хекс Посох");
        cap(8, 58, "Призыв: посох выходит из пустоты в руку");

        shot(58, 135, 6.2, -57.6, 2.4, 6.4, -57.8, 5.4, 0.5, -58.8, 5.0);
        title(58, 100, "1. Хекса Щит");
        cap(64, 100, "Стена из отдельных сот, 20 HP");
        cap(100, 135, "Стрелы противника: соты краснеют от урона");

        shot(135, 178, 5.2, -58.4, 0.6, 5.0, -58.4, 2.2, 0.5, -59.0, 3.6);
        cap(135, 178, "Барьер не пускает противника");

        shot(178, 258, -5.8, -58.0, 5.0, -6.4, -57.8, 3.8, -3.0, -59.0, -1.0);
        title(178, 215, "5. Хекса Отражение");
        cap(215, 258, "Стрела возвращается к стрелявшему (2/3 урона)");

        shot(258, 320, 2.7, -57.7, -2.1, 2.2, -57.2, -1.3, 0.5, -57.4, 1.2);
        title(258, 300, "2. Хекса Бур");
        cap(272, 320, "Удержание: соты собираются в конус и раскручиваются");

        shot(320, 395, -3.6, -57.8, -3.2, -4.8, -58.0, 3.2, 0.5, -58.5, 7.5);
        cap(320, 395, "Сквозь свою стену и камень — прямо в цель");

        shot(395, 468, 5.6, -58.2, 2.6, 5.8, -58.4, 6.2, 0.5, -58.8, 5.6);
        title(395, 430, "3. Хекса Шипы");
        cap(410, 468, "Щит обрастает шипами и летит в прицел");

        follow(468, 545, 4.2, -59.0, 3.6, 5.2, -50.5, 4.6, HexFilm.MAGE, 0.9);
        title(468, 500, "4. Хекса Прыжок");
        cap(500, 545, "На платформе заклинания быстрее");

        follow(545, 625, 7.5, -56.5, 5.5, 7.5, -58.6, 4.6, HexFilm.MAGE, 0.9);
        cap(545, 625, "Подушка из сот гасит падение");

        shot(625, 715, -4.8, -56.8, -3.8, -3.6, -57.4, -2.6, 0.5, -58.2, 5.5);
        title(625, 660, "6. Хекс Атака");
        cap(660, 715, "Малые буры наводятся и летят по очереди");

        shot(715, 760, -6.2, -54.8, 3.8, -5.6, -55.4, 5.2, 0.5, -58.6, 9.5);
        title(715, 760, "7. Хекс Тюрьма");

        shot(760, 800, 6.8, -56.8, 6.5, 6.2, -57.4, 8.2, 0.5, -59.0, 9.5);
        cap(760, 800, "Противник пытается выбраться");

        eyes(800, 848, HexFilm.RIVAL);
        cap(800, 848, "Вид изнутри");

        shot(848, 902, -3.6, -58.5, 4.6, -3.0, -58.5, 6.0, 0.5, -58.6, 8.2);
        cap(848, 902, "Бур проходит через открывшийся проход в своей стене");

        shot(902, 975, 0.5, -54.6, 5.0, 0.5, -55.6, 6.2, 0.5, -59.5, 9.5);
        cap(902, 940, "На 7-й секунде стены смыкаются");
        cap(940, 975, "Сжатие: 10 чистого урона");

        shot(975, 1062, 5.8, -58.4, 4.8, 5.1, -58.5, 3.7, 0.5, -58.6, 3.2);
        cap(975, 1032, "Противник ломает Хекса Щит топором");
        cap(1032, 1062, "Поле разрушено: пассивки отключены на 30 с");

        shot(1062, END + 40, 7.0, -56.0, -6.0, 10.5, -53.5, -9.5, 0.5, -58.5, 4.0);
        title(1062, END + 40, "Хекс Посох");
    }

    public static Shot shotAt(double t) {
        Shot best = SHOTS.get(0);
        for (Shot s : SHOTS) if (t >= s.t0()) best = s;
        return best;
    }
}
