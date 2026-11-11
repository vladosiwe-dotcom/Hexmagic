import json, os, math
from PIL import Image, ImageDraw

ROOT = '/home/user/Hexmagic/src/main/resources'
A = f'{ROOT}/assets/hexmagic'
D = f'{ROOT}/data'
for d in ['models/item', 'textures/item', 'lang']:
    os.makedirs(f'{A}/{d}', exist_ok=True)

# ---------------------------------------------------------------- texture 32x64
W, H = 32, 64
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()

def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = c

# Shaft strip x 0..1 (N/S faces) and 1..2 (E/W), 32 px tall; dark blue-black wood with runes.
wood = [(34, 36, 50, 255), (40, 43, 60, 255), (30, 32, 46, 255)]
rune = (96, 160, 210, 255)
for y in range(32):
    for x in range(2):
        c = wood[(y * 7 + x * 3) % 3]
        if y in (4, 9, 14, 19, 24) and x == 0:
            c = rune
        if y in (6, 11, 16, 21, 26) and x == 1:
            c = (70, 120, 170, 255)
        if y < 2:
            c = (120, 200, 240, 255)  # bright collar under the head
        put(x, y, c)
# Grip x 4..6, y 0..8: wrapped leather.
for y in range(8):
    for x in range(4, 6):
        c = (62, 44, 38, 255) if (y + x) % 3 else (84, 60, 50, 255)
        put(x, y, c)
# Core 2x2 at x 8..10, y 0..2.
for y in range(2):
    for x in range(8, 10):
        put(x, y, (232, 251, 255, 255))
# Head hexagon ring 12x12 at x 16..28, y 0..12.
def hex_ring(ox, oy, size, thick, color, glow):
    cx, cy = size / 2.0, size / 2.0
    R = size / 2.0 - 0.5
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            # pointy-top hexagon distance
            ang = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            # distance to hexagon edge along this direction
            a = (ang - math.pi / 6) % (math.pi / 3) - math.pi / 6
            edge = R * math.cos(math.pi / 6) / max(1e-6, math.cos(a))
            d = edge - r
            if 0 <= d < thick:
                put(ox + x, oy + y, color)
            elif thick <= d < thick + 1.0:
                put(ox + x, oy + y, glow)
            elif d >= thick + 1.0 and r > 0.5:
                put(ox + x, oy + y, (140, 220, 255, 28))
hex_ring(16, 0, 12, 1.6, (170, 235, 255, 255), (70, 170, 225, 200))
# highlight pixels on the ring
for (x, y) in [(21, 0), (22, 0), (18, 2), (25, 2)]:
    put(x, y, (255, 255, 255, 255))
# Small cell 4x4 at x 16..20, y 14..18 and crown 6x6 at x 22..28, y 14..20.
hex_ring(16, 14, 4, 1.0, (150, 225, 255, 255), (80, 170, 220, 160))
hex_ring(22, 14, 6, 1.1, (170, 235, 255, 255), (80, 170, 220, 180))
img.save(f'{A}/textures/item/hex_staff.png')

# ---------------------------------------------------------------- model
def uv(x0, y0, x1, y1):
    return [x0 * 16.0 / W, y0 * 16.0 / H, x1 * 16.0 / W, y1 * 16.0 / H]

def box(frm, to, faces, shade=True):
    e = {"from": frm, "to": to, "faces": faces}
    if not shade:
        e["shade"] = False
    return e

T = "#0"
elements = [
    box([7.5, -16, 7.5], [8.5, 14, 8.5], {
        "north": {"uv": uv(0, 0, 1, 32), "texture": T},
        "south": {"uv": uv(0, 0, 1, 32), "texture": T},
        "east": {"uv": uv(1, 0, 2, 32), "texture": T},
        "west": {"uv": uv(1, 0, 2, 32), "texture": T},
        "up": {"uv": uv(0, 0, 1, 1), "texture": T},
        "down": {"uv": uv(0, 31, 1, 32), "texture": T}}),
    box([7, -6, 7], [9, 2, 9], {
        "north": {"uv": uv(4, 0, 6, 8), "texture": T},
        "south": {"uv": uv(4, 0, 6, 8), "texture": T},
        "east": {"uv": uv(4, 0, 6, 8), "texture": T},
        "west": {"uv": uv(4, 0, 6, 8), "texture": T},
        "up": {"uv": uv(4, 0, 6, 2), "texture": T},
        "down": {"uv": uv(4, 6, 6, 8), "texture": T}}),
    box([2, 14, 8], [14, 26, 8], {
        "north": {"uv": uv(16, 0, 28, 12), "texture": T},
        "south": {"uv": uv(16, 0, 28, 12), "texture": T}}, shade=False),
    box([8, 14, 2], [8, 26, 14], {
        "east": {"uv": uv(16, 0, 28, 12), "texture": T},
        "west": {"uv": uv(16, 0, 28, 12), "texture": T}}, shade=False),
    box([7, 19, 7], [9, 21, 9], {f: {"uv": uv(8, 0, 10, 2), "texture": T} for f in ["north", "south", "east", "west", "up", "down"]}, shade=False),
    box([6, 5, 8], [10, 9, 8], {"north": {"uv": uv(16, 14, 20, 18), "texture": T}, "south": {"uv": uv(16, 14, 20, 18), "texture": T}}, shade=False),
    box([8, 5, 6], [8, 9, 10], {"east": {"uv": uv(16, 14, 20, 18), "texture": T}, "west": {"uv": uv(16, 14, 20, 18), "texture": T}}, shade=False),
    box([5, 26, 8], [11, 32, 8], {"north": {"uv": uv(22, 14, 28, 20), "texture": T}, "south": {"uv": uv(22, 14, 28, 20), "texture": T}}, shade=False),
    box([8, 26, 5], [8, 32, 11], {"east": {"uv": uv(22, 14, 28, 20), "texture": T}, "west": {"uv": uv(22, 14, 28, 20), "texture": T}}, shade=False),
]
model = {
    "credit": "Hex Magic - generated staff model",
    "texture_size": [W, H],
    "textures": {"0": "hexmagic:item/hex_staff", "particle": "hexmagic:item/hex_staff"},
    "elements": elements,
    "gui_light": "front",
    "display": {
        "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [-7, 7, 2], "scale": [1.15, 1.15, 1.15]},
        "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [-7, 7, 2], "scale": [1.15, 1.15, 1.15]},
        "firstperson_righthand": {"rotation": [0, 0, -20], "translation": [10, 0, -8], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand": {"rotation": [0, 0, 20], "translation": [-10, 0, -8], "scale": [0.55, 0.55, 0.55]},
        "ground": {"translation": [0, 9.5, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [-13, 128, 65], "translation": [0.5, -1.0, 0], "scale": [0.55, 0.55, 0.55]},
        "fixed": {"translation": [0, 0.5, -0.75], "scale": [0.45, 0.45, 0.45]},
        "head": {"translation": [0, 12, 0], "scale": [1, 1, 1]}
    }
}
json.dump(model, open(f'{A}/models/item/hex_staff_model.json', 'w'), indent=1)
json.dump({"parent": "minecraft:builtin/entity", "gui_light": "front"}, open(f'{A}/models/item/hex_staff.json', 'w'), indent=1)

# ---------------------------------------------------------------- lang
en = {
    "item.hexmagic.hex_staff": "Hex Staff",
    "item.hexmagic.hex_staff.tooltip": "Honeycomb barriers that turn into weapons.",
    "itemGroup.hexmagic": "Hex Magic",
    "entity.hexmagic.hex_construct": "Hex Barrier",
    "entity.hexmagic.hex_drill": "Hexa Drill",
    "entity.hexmagic.hex_swarm": "Hex Attack Drills",
    "entity.hexmagic.hex_prison": "Hex Prison",
    "ability.hexmagic.shield": "Hexa Shield",
    "ability.hexmagic.shield.desc": "Raise a wall of honeycomb cells in front of you (1 charge).",
    "ability.hexmagic.drill": "Hexa Drill",
    "ability.hexmagic.drill.desc": "Hold to spin up a drill above your head, release to fire.",
    "ability.hexmagic.spike": "Hexa Spike",
    "ability.hexmagic.spike.desc": "Grow spikes on a wall and send it flying at your aim.",
    "ability.hexmagic.launch": "Hexa Launch",
    "ability.hexmagic.launch.desc": "A platform lifts you into the air; it also cushions your landing (1 charge).",
    "ability.hexmagic.reflection": "Hexa Reflection",
    "ability.hexmagic.reflection.desc": "Passive: throws arrows and spells back at their caster.",
    "ability.hexmagic.hex_attack": "Hex Attack",
    "ability.hexmagic.hex_attack.desc": "Small drills circle you and hunt the nearest enemy when you stay idle; press to fire at your aim.",
    "ability.hexmagic.prison": "Hex Prison",
    "ability.hexmagic.prison.desc": "Trap the target in a closing room of cells. Drill through your own walls to finish it.",
    "ability.hexmagic.irons": "Spellbook",
    "key.categories.hexmagic": "Hex Staff",
    "key.hexmagic.shield": "Hexa Shield",
    "key.hexmagic.drill": "Hexa Drill (hold)",
    "key.hexmagic.spike": "Hexa Spike",
    "key.hexmagic.launch": "Hexa Launch",
    "key.hexmagic.hex_attack": "Hex Attack",
    "key.hexmagic.prison": "Hex Prison",
    "key.hexmagic.next": "Next ability (right click)",
    "key.hexmagic.prev": "Previous ability (right click)",
    "message.hexmagic.no_charges": "No barrier charges left",
    "message.hexmagic.cooldown": "%s is recharging (%ss)",
    "message.hexmagic.no_target": "No target in sight",
    "message.hexmagic.passives_disabled": "Passive abilities are disabled for %ss",
    "message.hexmagic.passives_restored": "Passive abilities restored",
    "message.hexmagic.field_destroyed": "Protective field destroyed! Passives disabled for %ss",
    "message.hexmagic.prison_active": "A Hex Prison is already active",
    "hud.hexmagic.passives_off": "Passives off %ss",
    "hud.hexmagic.platform_boost": "Platform: faster casting",
    "hud.hexmagic.drill_charging": "spinning up...",
    "hud.hexmagic.drill_damage": "Hexa Drill: %s damage",
    "death.attack.hexmagic.drill": "%1$s was drilled through",
    "death.attack.hexmagic.drill.player": "%1$s was drilled through by %2$s",
    "death.attack.hexmagic.spike": "%1$s was impaled on honeycomb spikes",
    "death.attack.hexmagic.spike.player": "%1$s was impaled by %2$s's spikes",
    "death.attack.hexmagic.crush": "%1$s was crushed in a Hex Prison",
    "death.attack.hexmagic.crush.player": "%1$s was crushed in %2$s's Hex Prison",
}
ru = {
    "item.hexmagic.hex_staff": "Хекс Посох",
    "item.hexmagic.hex_staff.tooltip": "Магические соты, которые становятся оружием.",
    "itemGroup.hexmagic": "Хекс Магия",
    "entity.hexmagic.hex_construct": "Хекс-барьер",
    "entity.hexmagic.hex_drill": "Хекса-бур",
    "entity.hexmagic.hex_swarm": "Буры Хекс-атаки",
    "entity.hexmagic.hex_prison": "Хекс-тюрьма",
    "ability.hexmagic.shield": "Хекса Щит",
    "ability.hexmagic.shield.desc": "Стена из магических сот перед вами (1 заряд).",
    "ability.hexmagic.drill": "Хекса Бур",
    "ability.hexmagic.drill.desc": "Удерживайте, чтобы раскрутить бур над головой, отпустите — выстрел.",
    "ability.hexmagic.spike": "Хекса Шипы",
    "ability.hexmagic.spike.desc": "Стена покрывается шипами и летит в сторону прицела.",
    "ability.hexmagic.launch": "Хекса Прыжок",
    "ability.hexmagic.launch.desc": "Платформа поднимает вас в воздух и смягчает приземление (1 заряд).",
    "ability.hexmagic.reflection": "Хекса Отражение",
    "ability.hexmagic.reflection.desc": "Пассивно: стрелы и заклинания летят обратно в стрелявшего.",
    "ability.hexmagic.hex_attack": "Хекс Атака",
    "ability.hexmagic.hex_attack.desc": "Малые буры кружат над вами и сами атакуют врага при бездействии; нажмите — залп по цели.",
    "ability.hexmagic.prison": "Хекс Тюрьма",
    "ability.hexmagic.prison.desc": "Запирает цель в сжимающейся комнате из сот. Бур проходит сквозь свои стены.",
    "ability.hexmagic.irons": "Книга заклинаний",
    "key.categories.hexmagic": "Хекс Посох",
    "key.hexmagic.shield": "Хекса Щит",
    "key.hexmagic.drill": "Хекса Бур (удерживать)",
    "key.hexmagic.spike": "Хекса Шипы",
    "key.hexmagic.launch": "Хекса Прыжок",
    "key.hexmagic.hex_attack": "Хекс Атака",
    "key.hexmagic.prison": "Хекс Тюрьма",
    "key.hexmagic.next": "Следующая способность (ПКМ)",
    "key.hexmagic.prev": "Предыдущая способность (ПКМ)",
    "message.hexmagic.no_charges": "Заряды барьера закончились",
    "message.hexmagic.cooldown": "%s перезаряжается (%s с)",
    "message.hexmagic.no_target": "Нет цели в прицеле",
    "message.hexmagic.passives_disabled": "Пассивные способности отключены на %s с",
    "message.hexmagic.passives_restored": "Пассивные способности восстановлены",
    "message.hexmagic.field_destroyed": "Защитное поле разрушено! Пассивные способности отключены на %s с",
    "message.hexmagic.prison_active": "Хекс Тюрьма уже активна",
    "hud.hexmagic.passives_off": "Пассивки выкл. %s с",
    "hud.hexmagic.platform_boost": "Платформа: ускоренный каст",
    "hud.hexmagic.drill_charging": "раскрутка...",
    "hud.hexmagic.drill_damage": "Хекса Бур: %s урона",
    "death.attack.hexmagic.drill": "%1$s просверлен(а) насквозь",
    "death.attack.hexmagic.drill.player": "%1$s просверлен(а) насквозь игроком %2$s",
    "death.attack.hexmagic.spike": "%1$s пронзён(а) шипами из сот",
    "death.attack.hexmagic.spike.player": "%1$s пронзён(а) шипами игрока %2$s",
    "death.attack.hexmagic.crush": "%1$s раздавлен(а) в Хекс Тюрьме",
    "death.attack.hexmagic.crush.player": "%1$s раздавлен(а) в Хекс Тюрьме игрока %2$s",
}
json.dump(en, open(f'{A}/lang/en_us.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
json.dump(ru, open(f'{A}/lang/ru_ru.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

# ---------------------------------------------------------------- data
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, 'w'), indent=1)

for name in ['hex_drill', 'hex_spike', 'hex_crush']:
    write(f'{D}/hexmagic/damage_type/{name}.json', {"message_id": "hexmagic." + name[4:], "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})
write(f'{D}/minecraft/tags/damage_type/bypasses_armor.json', {"values": ["hexmagic:hex_crush"]})
write(f'{D}/minecraft/tags/damage_type/bypasses_enchantments.json', {"values": ["hexmagic:hex_crush"]})
write(f'{D}/minecraft/tags/damage_type/bypasses_effects.json', {"values": ["hexmagic:hex_crush"]})
write(f'{D}/minecraft/tags/damage_type/bypasses_shield.json', {"values": ["hexmagic:hex_crush", "hexmagic:hex_drill"]})
write(f'{D}/minecraft/tags/damage_type/no_knockback.json', {"values": ["hexmagic:hex_drill", "hexmagic:hex_spike", "hexmagic:hex_crush"]})
write(f'{D}/minecraft/tags/damage_type/avoids_guardian_thorns.json', {"values": ["hexmagic:hex_crush"]})
write(f'{D}/hexmagic/tags/block/drill_immune.json', {"values": [
    "#minecraft:wither_immune", "minecraft:bedrock", "minecraft:barrier", "minecraft:command_block", "minecraft:chain_command_block",
    "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw", "minecraft:end_portal", "minecraft:end_portal_frame",
    "minecraft:end_gateway", "minecraft:nether_portal", "minecraft:reinforced_deepslate", "minecraft:light", "minecraft:moving_piston",
    "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:respawn_anchor",
    "minecraft:ancient_debris", "minecraft:netherite_block", "minecraft:enchanting_table", "minecraft:ender_chest", "minecraft:beacon", "minecraft:conduit"]})
write(f'{D}/hexmagic/tags/entity_type/not_reflectable.json', {"values": [
    "minecraft:ender_pearl", "minecraft:fishing_bobber", "minecraft:eye_of_ender", "minecraft:experience_bottle", "minecraft:item",
    "minecraft:firework_rocket", "minecraft:llama_spit", "minecraft:shulker_bullet"]})
write(f'{D}/hexmagic/tags/entity_type/hex_attack_ignored.json', {"values": []})
write(f'{D}/irons_spellbooks/tags/item/staff.json', {"values": ["hexmagic:hex_staff"]})
write(f'{D}/hexmagic/recipe/hex_staff.json', {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": ["HAH", " B ", " D "],
    "key": {"H": {"item": "minecraft:honeycomb"}, "A": {"item": "minecraft:amethyst_shard"}, "B": {"item": "minecraft:breeze_rod"}, "D": {"item": "minecraft:diamond"}},
    "result": {"id": "hexmagic:hex_staff", "count": 1}})
print("assets written")
